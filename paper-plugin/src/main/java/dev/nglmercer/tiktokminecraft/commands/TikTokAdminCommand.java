package dev.nglmercer.tiktokminecraft.commands;

import dev.nglmercer.tiktokminecraft.TikTokMinecraftPlugin;
import dev.nglmercer.tiktokminecraft.chat.ChatFormatter;
import dev.nglmercer.tiktokminecraft.event.TikTokEventContext;
import dev.nglmercer.tiktokminecraft.functions.FunctionEngineService;
import dev.nglmercer.tiktokminecraft.functions.FunctionJobManager;
import dev.nglmercer.tiktokminecraft.log.LogCategory;
import dev.nglmercer.tiktokminecraft.rules.EventRule;
import dev.nglmercer.tiktokminecraft.tiktok.LiveConnectionState;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/**
 * The {@code /tiktok} administration command.
 *
 * <p>Every subcommand is permission checked and, where it changes state, it
 * reports exactly what happened. The test subcommands generate synthetic
 * events: they never send a request to TikTok.</p>
 */
public final class TikTokAdminCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "tiktokminecraft.admin";
    private static final String STATUS_PERMISSION = "tiktokminecraft.status";

    private static final List<String> SUBCOMMANDS = Collections.unmodifiableList(
            Arrays.asList(
                    "help", "status", "connect", "disconnect", "reconnect", "reload",
                    "chat", "rules", "functions", "jobs", "test", "debug"));

    private final TikTokMinecraftPlugin plugin;
    private final Instant startedAt = Instant.now();
    private volatile String lastEvent = "none";
    private volatile long matchedRules;

    public TikTokAdminCommand(TikTokMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    /** Records the last event for the status report. */
    public void noteEvent(TikTokEventContext event) {
        if (event == null) {
            return;
        }
        String name = ChatFormatter.displayName(event.nickname(), event.uniqueId());
        if ("unknown".equals(name)) {
            lastEvent = event.type();
        } else {
            lastEvent = event.type() + " from " + name;
        }
    }

    /** Records how many rules matched since the last report. */
    public void noteMatches(int count) {
        matchedRules += count;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("help")) {
            help(sender, label);
            return true;
        }
        if (sub.equals("status")) {
            status(sender);
            return true;
        }
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }
        switch (sub) {
            case "connect":
                connect(sender);
                return true;
            case "disconnect":
                disconnect(sender);
                return true;
            case "reconnect":
                reconnect(sender);
                return true;
            case "reload":
                reload(sender);
                return true;
            case "chat":
                chat(sender, args);
                return true;
            case "rules":
                rules(sender, args);
                return true;
            case "functions":
                functions(sender, args);
                return true;
            case "jobs":
                jobs(sender, args);
                return true;
            case "test":
                test(sender, args);
                return true;
            case "debug":
                debug(sender, args);
                return true;
            default:
                sender.sendMessage("Unknown subcommand: " + args[0]);
                help(sender, label);
                return true;
        }
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage("TikTokMinecraft commands:");
        sender.sendMessage("  /" + label + " status - show the connection and pipeline state");
        sender.sendMessage("  /" + label + " connect - connect to the configured creator");
        sender.sendMessage("  /" + label + " disconnect - disconnect and stay offline");
        sender.sendMessage("  /" + label + " reconnect - reconnect immediately");
        sender.sendMessage("  /" + label + " reload - reload the configuration, rules and functions");
        sender.sendMessage("  /" + label + " chat on|off - toggle the Minecraft chat mirror");
        sender.sendMessage("  /" + label + " rules list|enable <id>|disable <id> - manage the rules");
        sender.sendMessage("  /" + label + " functions list|preview <id>|run <id> - manage the functions");
        sender.sendMessage("  /" + label + " jobs list|cancel <id> - manage the running functions");
        sender.sendMessage("  /" + label
                + " test chat|gift|like|follow|share - inject a synthetic event");
        sender.sendMessage("  /" + label + " debug on|off - toggle diagnostic logging");
    }

    private void status(CommandSender sender) {
        if (!sender.hasPermission(STATUS_PERMISSION) && !sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("You do not have permission to view the status.");
            return;
        }
        TikTokMinecraftPlugin owner = plugin;
        LiveConnectionState state =
                owner.liveService() == null ? LiveConnectionState.STOPPED : owner.liveService().state();
        sender.sendMessage("TikTokMinecraft");
        sender.sendMessage("  State: " + state);
        sender.sendMessage("  Creator: @" + owner.config().uniqueId());
        sender.sendMessage("  Room ID: " + (owner.liveService() == null
                ? "-" : display(owner.liveService().roomId())));
        sender.sendMessage("  Uptime: " + uptime());
        sender.sendMessage("  Last Event: " + lastEvent);
        sender.sendMessage("  Received Events: " + (owner.eventBus() == null
                ? 0 : owner.eventBus().received()));
        sender.sendMessage("  Dropped Events: " + (owner.eventBus() == null
                ? 0 : owner.eventBus().dropped()));
        sender.sendMessage("  Duplicate Events: " + (owner.eventBus() == null
                ? 0 : owner.eventBus().duplicates()));
        sender.sendMessage("  Queued Events: " + (owner.eventBus() == null
                ? 0 : owner.eventBus().queued()));
        sender.sendMessage("  Matched Rules: " + matchedRules);
        sender.sendMessage("  Rules Loaded: " + owner.rules().size());
        sender.sendMessage("  Actions Executed: " + (owner.ruleEngine() == null
                ? 0 : owner.ruleEngine().actionsExecuted()));
        sender.sendMessage("  Actions Rejected: " + (owner.ruleEngine() == null
                ? 0 : owner.ruleEngine().actionsRejected()));
        sender.sendMessage("  Functions Loaded: " + (owner.functionEngine() == null
                ? 0 : owner.functionEngine().list().size()));
        sender.sendMessage("  Active Jobs: " + (owner.functionEngine() == null
                ? 0 : owner.functionEngine().jobs().activeCount()));
        sender.sendMessage("  Functions Failed: " + (owner.functionEngine() == null
                ? 0 : owner.functionEngine().jobs().failedCount()));
        sender.sendMessage("  Queued Actions: " + (owner.commandDispatcher() == null
                ? 0 : owner.commandDispatcher().queued()));
        sender.sendMessage("  Reconnect Attempts: " + (owner.liveService() == null
                ? 0 : owner.liveService().reconnectAttempts()));
        sender.sendMessage("  Live Actions: " + (owner.config().executeLiveActions()
                ? "enabled" : "disabled"));
        matchedRules = 0;
    }

    private static String display(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    private String uptime() {
        Duration duration = Duration.between(startedAt, Instant.now());
        long seconds = duration.getSeconds();
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m " + (seconds % 60) + "s";
    }

    private void connect(CommandSender sender) {
        if (plugin.liveService() == null) {
            sender.sendMessage("The plugin is not running.");
            return;
        }
        if (plugin.liveService().state().isActive()) {
            sender.sendMessage("Already connected or connecting.");
            return;
        }
        plugin.liveService().connect();
        sender.sendMessage("Connecting to @" + plugin.config().uniqueId() + "...");
    }

    private void disconnect(CommandSender sender) {
        if (plugin.liveService() == null) {
            sender.sendMessage("The plugin is not running.");
            return;
        }
        plugin.liveService().disconnect();
        sender.sendMessage("Disconnected.");
    }

    private void reconnect(CommandSender sender) {
        if (plugin.liveService() == null) {
            sender.sendMessage("The plugin is not running.");
            return;
        }
        plugin.liveService().reconnect();
        sender.sendMessage("Reconnecting to @" + plugin.config().uniqueId() + "...");
    }

    private void reload(CommandSender sender) {
        List<String> problems = plugin.reload();
        if (problems.isEmpty()) {
            sender.sendMessage("Configuration, rules and functions reloaded.");
        } else {
            sender.sendMessage("Reload failed; the previous configuration is still active:");
            for (String problem : problems) {
                sender.sendMessage("  - " + problem);
            }
        }
    }

    private void chat(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /tiktok chat on|off");
            return;
        }
        boolean enabled = "on".equalsIgnoreCase(args[1]);
        if (!enabled && !"off".equalsIgnoreCase(args[1])) {
            sender.sendMessage("Usage: /tiktok chat on|off");
            return;
        }
        plugin.getConfig().set("chat.minecraft", Boolean.valueOf(enabled));
        plugin.saveConfig();
        plugin.reload();
        sender.sendMessage("Minecraft chat mirror " + (enabled ? "enabled" : "disabled") + ".");
    }

    private void rules(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /tiktok rules list|enable <id>|disable <id>");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            List<EventRule> loaded = plugin.rules().rules();
            if (loaded.isEmpty()) {
                sender.sendMessage("No rules are loaded.");
                return;
            }
            sender.sendMessage("Loaded rules:");
            for (EventRule rule : loaded) {
                Boolean override = plugin.ruleEngine().override(rule.id());
                String state = override == null
                        ? (rule.enabled() ? "enabled" : "disabled")
                        : (override.booleanValue() ? "enabled (override)" : "disabled (override)");
                sender.sendMessage("  " + rule.id() + " [" + rule.trigger() + "] - " + state
                        + " - " + rule.source());
            }
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("Usage: /tiktok rules " + action + " <id>");
            return;
        }
        String id = args[2];
        if (action.equals("enable")) {
            sender.sendMessage(plugin.ruleEngine().setEnabled(id, true)
                    ? "Enabled rule " + id
                    : "No rule with the id " + id);
        } else if (action.equals("disable")) {
            sender.sendMessage(plugin.ruleEngine().setEnabled(id, false)
                    ? "Disabled rule " + id
                    : "No rule with the id " + id);
        } else {
            sender.sendMessage("Usage: /tiktok rules list|enable <id>|disable <id>");
        }
    }

    private void functions(CommandSender sender, String[] args) {
        FunctionEngineService engine = plugin.functionEngine();
        if (engine == null) {
            sender.sendMessage("The plugin is not running.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /tiktok functions list|preview <id>|run <id>");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            List<String> ids = engine.list();
            if (ids.isEmpty()) {
                sender.sendMessage("No functions are loaded.");
                return;
            }
            sender.sendMessage("Loaded functions:");
            for (String id : ids) {
                sender.sendMessage("  " + id);
            }
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("Usage: /tiktok functions " + action + " <id>");
            return;
        }
        String id = args[2];
        if (action.equals("preview")) {
            try {
                dev.nglmercer.tiktokminecraft.functions.FunctionPreview preview =
                        engine.preview(id, new LinkedHashMap<String, Object>());
                sender.sendMessage("Preview of " + id + ":");
                for (String step : preview.steps()) {
                    sender.sendMessage("  " + step);
                }
                if (preview.steps().isEmpty()) {
                    sender.sendMessage("  (the function has no steps)");
                }
            } catch (dev.nglmercer.tiktokminecraft.functions.FunctionException failure) {
                sender.sendMessage("The function could not be previewed: " + failure.getMessage());
            }
            return;
        }
        if (action.equals("run")) {
            engine.execute(
                    id,
                    new LinkedHashMap<String, Object>(),
                    dev.nglmercer.tiktokminecraft.functions.ExecutionContext.server(
                            "the administrator"));
            sender.sendMessage("Started " + id + ".");
            return;
        }
        sender.sendMessage("Usage: /tiktok functions list|preview <id>|run <id>");
    }

    private void jobs(CommandSender sender, String[] args) {
        FunctionEngineService engine = plugin.functionEngine();
        if (engine == null) {
            sender.sendMessage("The plugin is not running.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /tiktok jobs list|cancel <id>");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            List<FunctionJobManager.Job> running = engine.jobs().list();
            if (running.isEmpty()) {
                sender.sendMessage("No function is running.");
                return;
            }
            sender.sendMessage("Running functions:");
            for (FunctionJobManager.Job job : running) {
                sender.sendMessage("  " + job.id() + " " + job.functionId() + " - " + job.state()
                        + " - " + job.target());
            }
            return;
        }
        if (action.equals("cancel") && args.length >= 3) {
            sender.sendMessage(engine.jobs().cancel(args[2])
                    ? "Cancelled job " + args[2]
                    : "No job with the id " + args[2]);
            return;
        }
        sender.sendMessage("Usage: /tiktok jobs list|cancel <id>");
    }

    private void debug(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /tiktok debug on|off");
            return;
        }
        boolean enabled = "on".equalsIgnoreCase(args[1]);
        if (!enabled && !"off".equalsIgnoreCase(args[1])) {
            sender.sendMessage("Usage: /tiktok debug on|off");
            return;
        }
        if (enabled) {
            plugin.log().enableDebug();
        } else {
            plugin.log().disableDebug();
        }
        sender.sendMessage("Diagnostic logging " + (enabled ? "enabled" : "disabled") + ".");
    }

    private void test(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(
                    "Usage: /tiktok test chat <message> | gift <name> [final|update] | like <count> | follow | share");
            return;
        }
        String kind = args[1].toLowerCase(Locale.ROOT);
        TikTokEventContext event;
        if (kind.equals("chat")) {
            String message = args.length > 2
                    ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                    : "";
            if (message.isEmpty()) {
                sender.sendMessage("Usage: /tiktok test chat <message>");
                return;
            }
            event = synthetic(TikTokEventContext.CHAT, chatPayload(message));
        } else if (kind.equals("gift")) {
            String name = args.length > 2 ? args[2] : "Rose";
            // A gift that has been completed is the event most gift rules
            // listen for, so that is the default.
            String trigger = TikTokEventContext.GIFT_FINAL;
            if (args.length > 3) {
                if (args[3].equalsIgnoreCase("update")) {
                    trigger = TikTokEventContext.GIFT;
                } else if (!args[3].equalsIgnoreCase("final")) {
                    sender.sendMessage("The gift trigger must be 'final' or 'update'.");
                    return;
                }
            }
            event = synthetic(trigger, giftPayload(name));
        } else if (kind.equals("like")) {
            long count = 1;
            if (args.length > 2) {
                try {
                    count = Long.parseLong(args[2]);
                } catch (NumberFormatException failure) {
                    sender.sendMessage("The like count must be a whole number.");
                    return;
                }
            }
            event = synthetic(TikTokEventContext.LIKE, likePayload(count));
        } else if (kind.equals("follow")) {
            event = synthetic(TikTokEventContext.SOCIAL_FOLLOW, socialPayload("follow", 1L));
        } else if (kind.equals("share")) {
            event = synthetic(TikTokEventContext.SOCIAL_SHARE, socialPayload("share", 3L));
        } else {
            sender.sendMessage(
                    "Usage: /tiktok test chat <message> | gift <name> | like <count> | follow | share");
            return;
        }
        plugin.eventBus().offer(event);
        sender.sendMessage("Injected a synthetic " + event.type()
                + " event through the normal pipeline.");
    }

    private TikTokEventContext synthetic(String type, Map<String, Object> payload) {
        return new TikTokEventContext(
                "synthetic-" + System.nanoTime(),
                plugin.liveService() == null ? "" : plugin.liveService().roomId(),
                type,
                "0",
                "tester",
                "Tester",
                System.currentTimeMillis(),
                false,
                payload);
    }

    private static Map<String, Object> chatPayload(String message) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("chat.text", message);
        payload.put("chat.emotes", new ArrayList<Object>());
        payload.put("chat.emoteOnly", Boolean.FALSE);
        return payload;
    }

    private static Map<String, Object> giftPayload(String name) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("gift.id", "0");
        payload.put("gift.name", name);
        payload.put("gift.diamonds", Long.valueOf(1));
        payload.put("gift.repeatCount", Long.valueOf(1));
        payload.put("gift.comboCount", Long.valueOf(1));
        payload.put("gift.groupId", "0");
        payload.put("gift.repeatEnd", Boolean.TRUE);
        return payload;
    }

    private static Map<String, Object> likePayload(long count) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("like.count", Long.valueOf(count));
        payload.put("like.total", Long.valueOf(count));
        return payload;
    }

    private static Map<String, Object> socialPayload(String kind, long action) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("social.kind", kind);
        payload.put("social.action", Long.valueOf(action));
        payload.put("social.followCount", Long.valueOf(0));
        payload.put("social.shareCount", Long.valueOf(0));
        return payload;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (sub.equals("chat") || sub.equals("debug")) {
                return filter(Arrays.asList("on", "off"), args[1]);
            }
            if (sub.equals("test")) {
                return filter(
                        Arrays.asList("chat", "gift", "like", "follow", "share"), args[1]);
            }
            if (sub.equals("rules")) {
                return filter(Arrays.asList("list", "enable", "disable"), args[1]);
            }
            if (sub.equals("functions")) {
                return filter(Arrays.asList("list", "preview", "run"), args[1]);
            }
            if (sub.equals("jobs")) {
                return filter(Arrays.asList("list", "cancel"), args[1]);
            }
        }
        if (args.length == 3 && sub.equals("rules")) {
            List<String> ids = new ArrayList<String>();
            for (EventRule rule : plugin.rules().rules()) {
                ids.add(rule.id());
            }
            return filter(ids, args[2]);
        }
        if (args.length == 3 && sub.equals("functions")) {
            return filter(plugin.functionEngine().list(), args[2]);
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<String>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
