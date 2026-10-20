package dev.nglmercer.tiktokminecraft.commands;

import dev.nglmercer.tiktokminecraft.TikTokMinecraftPlugin;
import dev.nglmercer.tiktokminecraft.chat.ChatFormatter;
import dev.nglmercer.tiktokminecraft.event.TikTokEventContext;
import dev.nglmercer.tiktokminecraft.log.LogCategory;
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
                    "chat", "test", "debug"));

    private final TikTokMinecraftPlugin plugin;
    private final Instant startedAt = Instant.now();
    private volatile String lastEvent = "none";

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
        sender.sendMessage("  /" + label + " reload - reload the configuration");
        sender.sendMessage("  /" + label + " chat on|off - toggle the Minecraft chat mirror");
        sender.sendMessage("  /" + label + " test chat <message> - inject a synthetic chat event");
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
        sender.sendMessage("  Reconnect Attempts: " + (owner.liveService() == null
                ? 0 : owner.liveService().reconnectAttempts()));
        sender.sendMessage("  Live Actions: " + (owner.config().executeLiveActions()
                ? "enabled" : "disabled"));
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
            sender.sendMessage("Configuration reloaded.");
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
            sender.sendMessage("Usage: /tiktok test chat <message>");
            return;
        }
        String kind = args[1].toLowerCase(Locale.ROOT);
        if (!kind.equals("chat")) {
            sender.sendMessage("Only 'chat' is supported right now.");
            return;
        }
        String message = args.length > 2
                ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                : "";
        if (message.isEmpty()) {
            sender.sendMessage("Usage: /tiktok test chat <message>");
            return;
        }
        TikTokEventContext event = new TikTokEventContext(
                "synthetic-" + System.nanoTime(),
                plugin.liveService() == null ? "" : plugin.liveService().roomId(),
                TikTokEventContext.CHAT,
                "0",
                "tester",
                "Tester",
                System.currentTimeMillis(),
                false,
                chatPayload(message));
        plugin.eventBus().offer(event);
        sender.sendMessage("Injected a synthetic chat event through the normal pipeline.");
    }

    private static Map<String, Object> chatPayload(String message) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("chat.text", message);
        payload.put("chat.emotes", new ArrayList<Object>());
        payload.put("chat.emoteOnly", Boolean.FALSE);
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
                return filter(Collections.singletonList("chat"), args[1]);
            }
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
