package dev.nglmercer.tiktokminecraft;

import dev.nglmercer.tiktokminecraft.actions.ActionContext;
import dev.nglmercer.tiktokminecraft.actions.ActionRegistry;
import dev.nglmercer.tiktokminecraft.actions.EntityRateLimiter;
import dev.nglmercer.tiktokminecraft.chat.TikTokChatBridge;
import dev.nglmercer.tiktokminecraft.commands.TikTokAdminCommand;
import dev.nglmercer.tiktokminecraft.config.PluginConfig;
import dev.nglmercer.tiktokminecraft.config.LiveFileWatcher;
import dev.nglmercer.tiktokminecraft.functions.FunctionRegistry;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
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

    private volatile PluginConfig config;
    private PluginLog log;
    private EventBus eventBus;
    private TikTokChatBridge chatBridge;
    private TikTokLiveService liveService;
    private final Map<String, TikTokLiveService> connections = new java.util.LinkedHashMap<>();
    public Map<String, TikTokLiveService> connections() { return java.util.Collections.unmodifiableMap(connections); }
    private TikTokAdminCommand adminCommand;
    private BukkitTask tickTask;
    private LiveFileWatcher fileWatcher;
    private String lastHotReload = "Waiting for edits";

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
        // Inventory prank restores the player on logout, timeout or plugin disable.
        getServer().getPluginManager().registerEvents(
                (dev.nglmercer.tiktokminecraft.actions.ClearInventoryAction)
                        actionRegistry.get("temporary-clear-inventory"), this);
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
        loadRules(config);

        eventBus = new EventBus(config);
        chatBridge = new TikTokChatBridge(log, config);

        reconcileConnections(null, config);

        adminCommand = new TikTokAdminCommand(this);
        PluginCommand command = getCommand("tiktok");
        if (command != null) {
            command.setExecutor(adminCommand);
            command.setTabCompleter(adminCommand);
        }

        // One repeating task drains every pipeline on the server thread.
        tickTask = getServer().getScheduler().runTaskTimer(this, this::tick, 1L, 1L);

        fileWatcher = new LiveFileWatcher(getDataFolder().toPath(),
                config.hotReloadEnabled(), config.hotReloadDebounceMillis(),
                changed -> {
                    if (!isEnabled()) return;
                    try {
                        getServer().getScheduler().runTask(this, () -> {
                            if (!isEnabled()) return;
                            List<String> errors = reload();
                            if (errors.isEmpty()) {
                                lastHotReload = "Applied " + String.join(", ", changed);
                                getLogger().info("Hot reload applied: " + String.join(", ", changed));
                            } else {
                                lastHotReload = "Rejected " + String.join(", ", changed)
                                        + ": " + String.join("; ", errors);
                                getLogger().warning("Hot reload rejected; previous state retained: "
                                        + lastHotReload);
                            }
                        });
                    } catch (IllegalStateException ignored) {
                        // The server is stopping; never restart a closed watcher.
                    }
                },
                message -> getLogger().warning(message));
        fileWatcher.start();

        getLogger().info("Enabled. Creator: @" + config.uniqueId());
        if (config.autoConnect()) {
            connections.values().forEach(TikTokLiveService::connect);
        } else {
            getLogger().info("Auto-connect is disabled; use /tiktok connect.");
        }
    }

    @Override
    public void onDisable() {
        if (fileWatcher != null) {
            fileWatcher.close();
            fileWatcher = null;
        }
        if (actionRegistry != null) {
            ((dev.nglmercer.tiktokminecraft.actions.LightningAction)
                    actionRegistry.get("lightning")).cancelAll();
            ((dev.nglmercer.tiktokminecraft.actions.LightningAction)
                    actionRegistry.get("lightning-effect")).cancelAll();
            ((dev.nglmercer.tiktokminecraft.actions.ClearInventoryAction)
                    actionRegistry.get("temporary-clear-inventory")).restoreAll();
            ((dev.nglmercer.tiktokminecraft.actions.PlaceCobwebAction)
                    actionRegistry.get("place-cobweb")).restoreAll();
            ((dev.nglmercer.tiktokminecraft.actions.TntCageAction)
                    actionRegistry.get("tnt-cage")).restoreAll();
            ((dev.nglmercer.tiktokminecraft.actions.VoidShaftAction)
                    actionRegistry.get("void-shaft")).restoreAll();
        }
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        connections.values().forEach(TikTokLiveService::close);
        connections.clear();
        liveService = null;
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
        Object source = event.payload().get("connection.uniqueId");
        if (source != null && config.accounts().stream()
                .noneMatch(a -> a.uniqueId().equalsIgnoreCase(source.toString()))) return;
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
        List<String> problems = new ArrayList<>();
        // Bukkit's loadConfiguration() silently falls back to an empty config
        // on malformed YAML. Parse strictly so an editor's partial save cannot
        // replace a working configuration with defaults.
        YamlConfiguration candidate = new YamlConfiguration();
        try {
            candidate.load(getDataFolder().toPath().resolve("config.yml").toFile());
        } catch (IOException | InvalidConfigurationException failure) {
            problems.add("config.yml: " + failure.getMessage());
            return problems;
        }
        PluginConfig.Result result = PluginConfig.load(candidate);
        if (!result.isValid()) {
            problems.addAll(result.problems());
            return problems;
        }
        PluginConfig previous = config;
        PluginConfig updated = result.config();

        // Stage a complete backup before touching the shared function registry.
        // The YAML rules validate referenced functions against that registry,
        // so functions are applied first and reverted if *any* rule fails.
        FunctionRegistry previousFunctions = new FunctionRegistry();
        previousFunctions.replaceAll(functionEngine.registry());
        RulesSnapshot previousRules = ruleEngine.snapshot();
        try {
            functionEngine.applyConfig(updated.functionLimits(),
                    new CommandPolicy(updated.allowedCommands()));
            problems.addAll(loadFunctions());
            if (problems.isEmpty()) problems.addAll(loadRules(updated));
        } catch (RuntimeException failure) {
            problems.add("Reload failed: " + failure.getMessage());
        }
        if (!problems.isEmpty()) {
            functionEngine.registry().replaceAll(previousFunctions);
            ruleEngine.apply(previousRules);
            if (previous != null) {
                functionEngine.applyConfig(previous.functionLimits(),
                        new CommandPolicy(previous.allowedCommands()));
            }
            return problems;
        }

        // Apply the typed snapshot only after all input files are valid.
        // Calls to reloadConfig() are only made after validation succeeds.
        reloadConfig();
        if (previous == null || previous.maxEntitiesPerMinute() != updated.maxEntitiesPerMinute()) {
            entityLimiter = new EntityRateLimiter(updated.maxEntitiesPerMinute());
        }
        config = updated;
        // A successful reload stops scheduled lightning, including when live
        // actions are disabled or the entity budget/target configuration changes.
        ((dev.nglmercer.tiktokminecraft.actions.LightningAction)
                actionRegistry.get("lightning")).cancelAll();
        ((dev.nglmercer.tiktokminecraft.actions.LightningAction)
                actionRegistry.get("lightning-effect")).cancelAll();
        // Do not reset event deduplication merely because an administrator
        // edited an action, function or DSL rule while connected to LIVE.
        if (previous == null || previous.maxPendingEvents() != updated.maxPendingEvents()
                || previous.deduplicationWindowSeconds() != updated.deduplicationWindowSeconds()
                || previous.eventsIgnoreHistory() != updated.eventsIgnoreHistory()) {
            eventBus.applyConfig(updated);
        }
        chatBridge.applyConfig(updated);
        reconcileConnections(previous, updated);
        if (fileWatcher != null) {
            fileWatcher.configure(updated.hotReloadEnabled(), updated.hotReloadDebounceMillis());
        }

        getLogger().info("Configuration, functions and rules reloaded.");
        return problems;
    }

    /**
     * Switches to another creator and reconnects.
     *
     * <p>The change is written to {@code config.yml}, so it survives a
     * restart, and it takes effect immediately: the plugin does not have to
     * be restarted and the file does not have to be edited by hand.</p>
     *
     * @return null when the creator was changed, or the reason it was not
     */
    public String applyCreator(String uniqueId) {
        if (uniqueId == null || !uniqueId.matches("[A-Za-z0-9._]{1,24}")) {
            return "That is not a valid TikTok username";
        }
        if (liveService == null) {
            return "The plugin is not running";
        }
        String oldCreator = getConfig().getString("tiktok.unique-id");
        getConfig().set("tiktok.unique-id", uniqueId);
        PluginConfig.Result result = PluginConfig.load(getConfig());
        if (!result.isValid()) {
            getConfig().set("tiktok.unique-id", oldCreator);
            return result.problems().get(0);
        }
        PluginConfig previous = config;
        config = result.config();
        reconcileConnections(previous, config);
        liveService.connect();
        saveConfig();
        return null;
    }

    private void reconcileConnections(PluginConfig previous, PluginConfig updated) {
        java.util.Set<String> wanted = new java.util.HashSet<>();
        for (PluginConfig.Account account : updated.accounts()) {
            String key = account.uniqueId().toLowerCase(java.util.Locale.ROOT);
            wanted.add(key);
            TikTokLiveService service = connections.get(key);
            if (service == null) {
                service = new TikTokLiveService(log, getDataFolder().toPath().resolve("cache").resolve(key),
                        new TikTokLiveService.Listener() {
                            public void onEvent(TikTokEventContext event) {
                                // Read the current account target without restarting its LIVE session.
                                PluginConfig snapshot = config;
                                PluginConfig.Account current = snapshot.accounts().stream()
                                        .filter(a -> a.uniqueId().equalsIgnoreCase(key)).findFirst().orElse(null);
                                if (current == null) return;
                                Map<String, Object> payload = new java.util.LinkedHashMap<>(event.payload());
                                payload.put("connection.uniqueId", current.uniqueId());
                                payload.put("connection.targetPlayer", current.targetPlayer());
                                eventBus.offer(new TikTokEventContext(event.eventId(), event.roomId(), event.type(),
                                        event.userId(), event.uniqueId(), event.nickname(), event.timestamp(),
                                        event.history(), payload));
                            }
                            public void onState(LiveConnectionState state, String detail) {
                                log.debug(LogCategory.CONNECTION, "@" + key + " " + state + ": " + detail);
                            }
                        }, updated, account.uniqueId());
                connections.put(key, service);
                if (previous != null && updated.autoConnect()) service.connect();
            } else {
                service.applyConfig(updated);
                if (previous != null && previous.fetchGifts() != updated.fetchGifts()) service.reconnect();
            }
        }
        connections.entrySet().removeIf(entry -> {
            if (wanted.contains(entry.getKey())) return false;
            entry.getValue().close();
            return true;
        });
        liveService = connections.get(updated.uniqueId().toLowerCase(java.util.Locale.ROOT));
    }

    /** Persists an account edit only after the complete configuration validates. */
    public String editAccount(String action, String username, String target) {
        String name = PluginConfig.normalizeUniqueId(username);
        if (!name.matches("[A-Za-z0-9._]{1,24}")) return "Invalid TikTok username";
        boolean primary = name.equalsIgnoreCase(config.uniqueId());
        List<Map<String, Object>> entries = new ArrayList<>();
        for (PluginConfig.Account account : config.accounts()) {
            if (!account.uniqueId().equalsIgnoreCase(config.uniqueId())) {
                Map<String, Object> entry = new java.util.LinkedHashMap<>();
                entry.put("unique-id", account.uniqueId());
                entry.put("target-player", account.targetPlayer());
                entries.add(entry);
            }
        }
        boolean exists = config.accounts().stream().anyMatch(a -> a.uniqueId().equalsIgnoreCase(name));
        if (action.equals("add") && exists) return "Account already exists";
        if (!action.equals("add") && !exists) return "Unknown account";
        if (action.equals("remove") && primary) return "The primary creator cannot be removed; use /tiktok creator to change it";
        Object oldEntries = getConfig().get("tiktok.accounts");
        Object oldTarget = getConfig().get("tiktok.target-player");
        if (action.equals("remove")) entries.removeIf(e -> name.equalsIgnoreCase(String.valueOf(e.get("unique-id"))));
        else if (primary) getConfig().set("tiktok.target-player", target);
        else if (action.equals("add")) entries.add(new java.util.LinkedHashMap<>(Map.of("unique-id", name, "target-player", target)));
        else entries.stream().filter(e -> name.equalsIgnoreCase(String.valueOf(e.get("unique-id"))))
                .forEach(e -> e.put("target-player", target));
        getConfig().set("tiktok.accounts", entries);
        PluginConfig.Result result = PluginConfig.load(getConfig());
        if (!result.isValid()) {
            getConfig().set("tiktok.accounts", oldEntries);
            getConfig().set("tiktok.target-player", oldTarget);
            return String.join("; ", result.problems());
        }
        PluginConfig previous = config;
        config = result.config();
        reconcileConnections(previous, config);
        saveConfig();
        return null;
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

    /**
     * Loads the rules. A broken file keeps the previous set active.
     *
     * @param forValidation the configuration the rules are checked against.
     *     It is the new one during a reload, so a change to the allowlist or
     *     to the limits takes effect at once.
     */
    private List<String> loadRules(PluginConfig forValidation) {
        List<String> problems = new ArrayList<String>();
        ActionContext validation =
                new ActionContext(
                        this, forValidation, functionEngine, log, null, null, entityLimiter);
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
        copyDefault("defaults/events/interactions.yml", "events/interactions.yml");
        copyDefault("defaults/functions/effects/welcome.json", "functions/effects/welcome.json");
        copyDefault("defaults/functions/gifts/spawn_zombies.json",
                "functions/gifts/spawn_zombies.json");
        copyDefault("defaults/functions/gifts/spawn_chicken.json",
                "functions/gifts/spawn_chicken.json");
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

    public boolean hotReloadEnabled() {
        return fileWatcher != null && fileWatcher.enabled();
    }

    public String hotReloadStatus() {
        return lastHotReload;
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
