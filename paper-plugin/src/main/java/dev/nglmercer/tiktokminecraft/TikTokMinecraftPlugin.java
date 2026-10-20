package dev.nglmercer.tiktokminecraft;

import dev.nglmercer.tiktokminecraft.actions.ActionContext;
import dev.nglmercer.tiktokminecraft.actions.ActionRegistry;
import dev.nglmercer.tiktokminecraft.actions.EntityRateLimiter;
import dev.nglmercer.tiktokminecraft.chat.TikTokChatBridge;
import dev.nglmercer.tiktokminecraft.commands.TikTokAdminCommand;
import dev.nglmercer.tiktokminecraft.config.PluginConfig;
import dev.nglmercer.tiktokminecraft.event.EventBus;
import dev.nglmercer.tiktokminecraft.event.TikTokEventContext;
import dev.nglmercer.tiktokminecraft.functions.CommandDispatcher;
import dev.nglmercer.tiktokminecraft.functions.FunctionEngineService;
import dev.nglmercer.tiktokminecraft.log.LogCategory;
import dev.nglmercer.tiktokminecraft.log.PluginLog;
import dev.nglmercer.tiktokminecraft.rules.DslRuleLoader;
import dev.nglmercer.tiktokminecraft.rules.EventRule;
import dev.nglmercer.tiktokminecraft.rules.RuleEngine;
import dev.nglmercer.tiktokminecraft.rules.RulesSnapshot;
import dev.nglmercer.tiktokminecraft.rules.YamlRuleLoader;
import dev.nglmercer.tiktokminecraft.security.CommandPolicy;
import dev.nglmercer.tiktokminecraft.tiktok.LiveConnectionState;
import dev.nglmercer.tiktokminecraft.tiktok.TikTokLiveService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * The TikTokMinecraft plugin.
 *
 * <p>Startup order matters: configuration first, then the function engine
 * and the rules, and only then the TikTok connection. Shutdown runs the
 * same list backwards, so no event can reach Minecraft after the plugin has
 * been disabled.</p>
 */
public final class TikTokMinecraftPlugin extends JavaPlugin {

    private PluginConfig config;
    private PluginLog log;
    private EventBus eventBus;
    private TikTokChatBridge chatBridge;
    private TikTokLiveService liveService;
    private TikTokAdminCommand adminCommand;
    private BukkitTask tickTask;

    private ActionRegistry actionRegistry;
    private CommandDispatcher commandDispatcher;
    private EntityRateLimiter entityLimiter;
    private FunctionEngineService functionEngine;
    private RuleEngine ruleEngine;

    private int tickCounter;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        writeDefaultFiles();

        PluginConfig.Result result = PluginConfig.load(getConfig());
        if (!result.isValid()) {
            getLogger().severe("Invalid configuration; the plugin will not start:");
            for (String problem : result.problems()) {
                getLogger().severe("  - " + problem);
            }
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        config = result.config();
        log = new PluginLog(getLogger());

        actionRegistry = new ActionRegistry();
        commandDispatcher = new CommandDispatcher(this);
        entityLimiter = new EntityRateLimiter(config.maxEntitiesPerMinute());

        functionEngine =
                new FunctionEngineService(
                        this,
                        log,
                        dev.nglmercer.tiktokminecraft.functions.FunctionStorage.defaultRoot(
                                getDataFolder().toPath()),
                        config.functionLimits(),
                        new CommandPolicy(config.allowedCommands()),
                        Bukkit.getMinecraftVersion(),
                        commandDispatcher,
                        config.allowConsoleCommands(),
                        config.maxConcurrentFunctions(),
                        config.maxQueuedFunctions());
        loadFunctions();

        ruleEngine = new RuleEngine(actionRegistry);
        loadRules();

        eventBus = new EventBus(config);
        chatBridge = new TikTokChatBridge(log, config);

        liveService =
                new TikTokLiveService(
                        log,
                        getDataFolder().toPath().resolve("cache"),
                        new TikTokLiveService.Listener() {
                            @Override
                            public void onEvent(TikTokEventContext event) {
                                eventBus.offer(event);
                            }

                            @Override
                            public void onState(LiveConnectionState state, String detail) {
                                log.debug(LogCategory.CONNECTION, state + ": " + detail);
                            }
                        },
                        config);

        adminCommand = new TikTokAdminCommand(this);
        PluginCommand command = getCommand("tiktok");
        if (command != null) {
            command.setExecutor(adminCommand);
            command.setTabCompleter(adminCommand);
        }

        // One repeating task drains every pipeline on the server thread.
        tickTask = getServer().getScheduler().runTaskTimer(this, this::tick, 1L, 1L);

        getLogger().info("Enabled. Creator: @" + config.uniqueId());
        if (config.autoConnect()) {
            liveService.connect();
        } else {
            getLogger().info("Auto-connect is disabled; use /tiktok connect.");
        }
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (liveService != null) {
            liveService.close();
            liveService = null;
        }
        if (functionEngine != null) {
            functionEngine.close();
            functionEngine = null;
        }
        if (commandDispatcher != null) {
            commandDispatcher.close();
            commandDispatcher = null;
        }
        if (chatBridge != null) {
            chatBridge.close();
            chatBridge = null;
        }
        if (eventBus != null) {
            eventBus.close();
            eventBus = null;
        }
        getLogger().info("Disabled.");
    }

    /** Runs once per server tick on the server thread. */
    private void tick() {
        if (eventBus != null) {
            eventBus.drain(this::handleEvent);
        }
        if (chatBridge != null) {
            chatBridge.drain();
        }
        if (commandDispatcher != null && config != null) {
            commandDispatcher.drain(config.maxCommandsPerTick());
        }
        tickCounter++;
        if (tickCounter % 20 == 0 && chatBridge != null) {
            chatBridge.reportSuppressed();
        }
    }

    private void handleEvent(TikTokEventContext event) {
        if (chatBridge != null) {
            chatBridge.accept(event);
        }
        if (adminCommand != null) {
            adminCommand.noteEvent(event);
        }
        if (ruleEngine == null) {
            return;
        }
        List<EventRule> matched = ruleEngine.match(event);
        if (matched.isEmpty()) {
            return;
        }
        if (adminCommand != null) {
            adminCommand.noteMatches(matched.size());
        }
        if (config != null && config.logRuleMatches()) {
            for (EventRule rule : matched) {
                log.debug(LogCategory.RULES, "Matched " + rule.id() + " (" + event.type() + ")");
            }
        }
        if (config == null || !config.executeLiveActions()) {
            // Live actions are disabled: the rules still match, but nothing an
            // event triggers may change the world.
            return;
        }
        ActionContext context =
                new ActionContext(
                        this,
                        config,
                        functionEngine,
                        log,
                        event,
                        matched.get(0).id(),
                        entityLimiter);
        for (EventRule rule : matched) {
            ruleEngine.execute(rule, event, context);
            if (rule.stopProcessing()) {
                break;
            }
        }
    }

    /**
     * Re-reads the configuration, the functions and the rules.
     *
     * <p>The TikTok connection is only restarted when a connection relevant
     * setting actually changed; a rule or function change never disturbs a
     * live session.</p>
     *
     * @return the problems that prevented the reload, or an empty list
     */
    public List<String> reload() {
        List<String> problems = new ArrayList<String>();
        reloadConfig();
        PluginConfig.Result result = PluginConfig.load(getConfig());
        if (!result.isValid()) {
            problems.addAll(result.problems());
            return problems;
        }
        PluginConfig previous = config;
        PluginConfig updated = result.config();

        problems.addAll(loadFunctions());
        problems.addAll(loadRules());

        if (!problems.isEmpty()) {
            // The previous configuration stays active.
            return problems;
        }
        config = updated;
        eventBus.applyConfig(updated);
        chatBridge.applyConfig(updated);
        liveService.applyConfig(updated);

        boolean connectionChanged = previous == null
                || !previous.uniqueId().equals(updated.uniqueId())
                || previous.fetchGifts() != updated.fetchGifts();
        if (connectionChanged && liveService.state().isActive()) {
            getLogger().info("Connection settings changed; reconnecting.");
            liveService.reconnect();
        }
        getLogger().info("Configuration reloaded.");
        return problems;
    }

    /** Loads the functions. A broken file keeps the previous set active. */
    private List<String> loadFunctions() {
        List<String> problems = new ArrayList<String>();
        try {
            FunctionEngineService.ReloadResult result = functionEngine.reload();
            if (!result.isValid()) {
                for (Map.Entry<String, String> failure : result.failures().entrySet()) {
                    problems.add("function " + failure.getKey() + ": " + failure.getValue());
                }
                return problems;
            }
            getLogger().info("Loaded " + result.loaded().size() + " function(s).");
        } catch (IOException failure) {
            problems.add("The function directory could not be read: " + failure.getMessage());
        }
        return problems;
    }

    /** Loads the rules. A broken file keeps the previous set active. */
    private List<String> loadRules() {
        List<String> problems = new ArrayList<String>();
        ActionContext validation =
                new ActionContext(
                        this, config, functionEngine, log, null, null, entityLimiter);
        YamlRuleLoader loader = new YamlRuleLoader(actionRegistry, validation);
        YamlRuleLoader.LoadResult yaml =
                loader.load(getDataFolder().toPath().resolve("events"));
        for (Map.Entry<String, String> failure : yaml.failures().entrySet()) {
            problems.add("rule file " + failure.getKey() + ": " + failure.getValue());
        }
        DslRuleLoader.LoadResult scripts =
                DslRuleLoader.load(
                        getDataFolder().toPath().resolve("scripts"), actionRegistry, validation);
        for (Map.Entry<String, String> failure : scripts.failures().entrySet()) {
            problems.add("script " + failure.getKey() + ": " + failure.getValue());
        }
        if (!problems.isEmpty()) {
            return problems;
        }
        List<EventRule> all = new ArrayList<EventRule>(yaml.snapshot().rules());
        all.addAll(scripts.rules());
        ruleEngine.apply(new RulesSnapshot(all));
        getLogger().info("Loaded " + all.size() + " rule(s).");
        return problems;
    }

    /** Creates the runtime directories and copies the bundled defaults. */
    private void writeDefaultFiles() {
        Path root = getDataFolder().toPath();
        try {
            Files.createDirectories(root.resolve("events"));
            Files.createDirectories(root.resolve("functions/effects"));
            Files.createDirectories(root.resolve("functions/gifts"));
            Files.createDirectories(root.resolve("functions/events"));
            Files.createDirectories(root.resolve("scripts"));
            Files.createDirectories(root.resolve("cache"));
        } catch (IOException failure) {
            getLogger().warning("Could not create the plugin directories: " + failure.getMessage());
            return;
        }
        copyDefault("defaults/events/chat.yml", "events/chat.yml");
        copyDefault("defaults/events/gifts.yml", "events/gifts.yml");
        copyDefault("defaults/events/likes.yml", "events/likes.yml");
        copyDefault("defaults/events/social.yml", "events/social.yml");
        copyDefault("defaults/events/members.yml", "events/members.yml");
        copyDefault("defaults/functions/effects/welcome.json", "functions/effects/welcome.json");
        copyDefault("defaults/scripts/example.ttl", "scripts/example.ttl");
    }

    private void copyDefault(String resource, String relativeTarget) {
        File target = new File(getDataFolder(), relativeTarget);
        if (target.exists()) {
            return;
        }
        java.io.InputStream stream = getResource(resource);
        if (stream == null) {
            return;
        }
        try {
            File parent = target.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            try (java.io.InputStream input = stream;
                    java.io.OutputStream output = new java.io.FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) > 0) {
                    output.write(buffer, 0, read);
                }
            }
            getLogger().info("Created default file " + relativeTarget);
        } catch (IOException failure) {
            getLogger().warning(
                    "Could not create " + relativeTarget + ": " + failure.getMessage());
        }
    }

    public PluginConfig config() {
        return config;
    }

    public PluginLog log() {
        return log;
    }

    public EventBus eventBus() {
        return eventBus;
    }

    public TikTokChatBridge chatBridge() {
        return chatBridge;
    }

    public TikTokLiveService liveService() {
        return liveService;
    }

    public TikTokAdminCommand adminCommand() {
        return adminCommand;
    }

    public ActionRegistry actionRegistry() {
        return actionRegistry;
    }

    public FunctionEngineService functionEngine() {
        return functionEngine;
    }

    public RuleEngine ruleEngine() {
        return ruleEngine;
    }

    public EntityRateLimiter entityLimiter() {
        return entityLimiter;
    }

    public CommandDispatcher commandDispatcher() {
        return commandDispatcher;
    }

    /** The active rules. */
    public RulesSnapshot rules() {
        return ruleEngine == null ? RulesSnapshot.empty() : ruleEngine.snapshot();
    }
}
