package dev.nglmercer.tiktokminecraft.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * An immutable snapshot of {@code config.yml}.
 *
 * <p>Every value is validated while loading, so a snapshot that exists is
 * always internally consistent. A reload builds a complete new snapshot and
 * only swaps it in once every section parsed successfully.</p>
 */
public final class PluginConfig {

    /** Where a rule or action looks for its target player. */
    public enum TargetPolicy {
        ALL_ONLINE,
        RANDOM_ONLINE,
        NEAREST_ONLINE,
        SPECIFIC_PLAYER,
        SPECIFIC_WORLD;

        public static TargetPolicy parse(String raw, TargetPolicy fallback) {
            if (raw == null) {
                return fallback;
            }
            String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
            for (TargetPolicy policy : values()) {
                if (policy.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                    return policy;
                }
            }
            return fallback;
        }
    }

    /** What to do when an action has no eligible target. */
    public enum NoPlayerPolicy {
        SKIP,
        CONSOLE;

        public static NoPlayerPolicy parse(String raw, NoPlayerPolicy fallback) {
            if (raw == null) {
                return fallback;
            }
            String normalized = raw.trim().toUpperCase(Locale.ROOT);
            for (NoPlayerPolicy policy : values()) {
                if (policy.name().equals(normalized)) {
                    return policy;
                }
            }
            return fallback;
        }
    }

    // TikTok connection
    private final String uniqueId;
    private final boolean autoConnect;
    private final boolean reconnect;
    private final boolean fetchGifts;
    private final boolean tiktokIgnoreHistory;

    // Chat
    private final boolean chatEnabled;
    private final boolean chatConsole;
    private final boolean chatMinecraft;
    private final String chatPrefix;
    private final int chatMaxLength;
    private final int chatMessagesPerSecond;
    private final int chatMaxQueue;

    // Events
    private final boolean eventsEnabled;
    private final boolean eventsDefaultEnabled;
    private final int deduplicationWindowSeconds;
    private final int maxPendingEvents;
    private final boolean eventsIgnoreHistory;

    // Functions
    private final boolean functionsEnabled;
    private final int maxConcurrentFunctions;
    private final int maxQueuedFunctions;
    private final int maxCommandsPerTick;
    private final int maxExecutionSeconds;
    private final int maxRepeat;
    private final int maxDepth;
    private final List<String> allowedCommands;

    // Actions
    private final TargetPolicy targetPolicy;
    private final NoPlayerPolicy noPlayerPolicy;
    private final int maxActionsPerEvent;
    private final int maxEntitiesPerEvent;
    private final int maxEntitiesPerMinute;
    private final List<String> allowedEntities;

    // Security
    private final boolean executeLiveActions;
    private final boolean allowConsoleCommands;
    private final boolean allowRawCommands;

    // Debug
    private final boolean logEvents;
    private final boolean logConnection;
    private final boolean logRuleMatches;

    /**
     * Normalizes a TikTok username.
     *
     * <p>The leading {@code @} is optional and is not part of the
     * identifier.</p>
     */
    public static String normalizeUniqueId(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (text.startsWith("@")) {
            text = text.substring(1);
        }
        return text;
    }

    private PluginConfig(FileConfiguration config) {
        ConfigurationSection tiktok = config.getConfigurationSection("tiktok");
        this.uniqueId = normalizeUniqueId(tiktok == null ? "" : tiktok.getString("unique-id", ""));
        this.autoConnect = tiktok == null || tiktok.getBoolean("auto-connect", true);
        this.reconnect = tiktok == null || tiktok.getBoolean("reconnect", true);
        this.fetchGifts = tiktok == null || tiktok.getBoolean("fetch-gifts", true);
        this.tiktokIgnoreHistory = tiktok == null || tiktok.getBoolean("ignore-history", true);

        ConfigurationSection chat = config.getConfigurationSection("chat");
        this.chatEnabled = chat == null || chat.getBoolean("enabled", true);
        this.chatConsole = chat == null || chat.getBoolean("console", true);
        this.chatMinecraft = chat == null || chat.getBoolean("minecraft", true);
        this.chatPrefix = chat == null ? "[TikTok]" : chat.getString("prefix", "[TikTok]");
        this.chatMaxLength = clamp(chat, "max-length", 200, 16, 1000);
        this.chatMessagesPerSecond = clamp(chat, "messages-per-second", 5, 1, 100);
        this.chatMaxQueue = clamp(chat, "max-queue", 128, 1, 4096);

        ConfigurationSection events = config.getConfigurationSection("events");
        this.eventsEnabled = events == null || events.getBoolean("enabled", true);
        this.eventsDefaultEnabled = events != null && events.getBoolean("default-enabled", false);
        this.deduplicationWindowSeconds =
                clamp(events, "deduplication-window-seconds", 300, 1, 86400);
        this.maxPendingEvents = clamp(events, "max-pending-events", 512, 1, 65536);
        this.eventsIgnoreHistory = events == null || events.getBoolean("ignore-history", true);

        ConfigurationSection functions = config.getConfigurationSection("functions");
        this.functionsEnabled = functions == null || functions.getBoolean("enabled", true);
        this.maxConcurrentFunctions = clamp(functions, "max-concurrent", 2, 1, 64);
        this.maxQueuedFunctions = clamp(functions, "max-queued", 64, 0, 65536);
        this.maxCommandsPerTick = clamp(functions, "max-commands-per-tick", 4, 1, 256);
        this.maxExecutionSeconds = clamp(functions, "max-execution-seconds", 60, 1, 3600);
        this.maxRepeat = clamp(functions, "max-repeat", 20, 1, 10000);
        this.maxDepth = clamp(functions, "max-depth", 8, 1, 64);
        this.allowedCommands = readStrings(functions, "allowed-commands",
                new String[] {"summon", "title", "playsound", "particle", "effect"});

        ConfigurationSection actions = config.getConfigurationSection("actions");
        this.targetPolicy =
                TargetPolicy.parse(
                        actions == null ? null : actions.getString("target-policy"),
                        TargetPolicy.NEAREST_ONLINE);
        this.noPlayerPolicy =
                NoPlayerPolicy.parse(
                        actions == null ? null : actions.getString("no-player-policy"),
                        NoPlayerPolicy.SKIP);
        this.maxActionsPerEvent = clamp(actions, "max-actions-per-event", 8, 0, 256);
        this.maxEntitiesPerEvent = clamp(actions, "max-entities-per-event", 5, 0, 1000);
        this.maxEntitiesPerMinute = clamp(actions, "max-entities-per-minute", 60, 0, 100000);
        this.allowedEntities = readStrings(actions, "allowed-entities", new String[0]);

        ConfigurationSection security = config.getConfigurationSection("security");
        this.executeLiveActions = security != null && security.getBoolean("execute-live-actions", false);
        this.allowConsoleCommands =
                security != null && security.getBoolean("allow-console-commands", false);
        this.allowRawCommands = security != null && security.getBoolean("allow-raw-commands", false);

        ConfigurationSection debug = config.getConfigurationSection("debug");
        this.logEvents = debug != null && debug.getBoolean("log-events", false);
        this.logConnection = debug == null || debug.getBoolean("log-connection", true);
        this.logRuleMatches = debug != null && debug.getBoolean("log-rule-matches", false);
    }

    /**
     * Parses and validates a configuration.
     *
     * @return the snapshot, or a list of human readable problems
     */
    public static Result load(FileConfiguration config) {
        List<String> problems = new ArrayList<String>();
        if (config == null) {
            problems.add("Configuration could not be read");
            return Result.failed(problems);
        }
        PluginConfig parsed = new PluginConfig(config);
        if (parsed.uniqueId.isEmpty()) {
            problems.add("tiktok.unique-id must not be empty");
        } else if (!parsed.uniqueId.matches("[A-Za-z0-9._]{1,24}")) {
            problems.add("tiktok.unique-id must be a TikTok username, got: " + parsed.uniqueId);
        }
        if (parsed.chatPrefix != null && parsed.chatPrefix.length() > 32) {
            problems.add("chat.prefix must be at most 32 characters");
        }
        if (parsed.functionsEnabled && parsed.allowedCommands.isEmpty()) {
            problems.add("functions.allowed-commands must list at least one command root");
        }
        if (parsed.executeLiveActions && !parsed.functionsEnabled && !parsed.eventsEnabled) {
            problems.add(
                    "security.execute-live-actions is enabled but neither events nor functions are");
        }
        if (!problems.isEmpty()) {
            return Result.failed(problems);
        }
        return Result.ok(parsed);
    }

    private static int clamp(ConfigurationSection section, String key, int fallback, int min, int max) {
        if (section == null) {
            return fallback;
        }
        int value = section.getInt(key, fallback);
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    private static List<String> readStrings(
            ConfigurationSection section, String key, String[] fallback) {
        List<String> values = new ArrayList<String>();
        if (section != null && section.isList(key)) {
            for (Object raw : section.getList(key)) {
                if (raw == null) {
                    continue;
                }
                String text = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
                if (!text.isEmpty()) {
                    values.add(text);
                }
            }
        } else {
            for (String raw : fallback) {
                values.add(raw.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableList(values);
    }

    public String uniqueId() {
        return uniqueId;
    }

    public boolean autoConnect() {
        return autoConnect;
    }

    public boolean reconnect() {
        return reconnect;
    }

    public boolean fetchGifts() {
        return fetchGifts;
    }

    public boolean tiktokIgnoreHistory() {
        return tiktokIgnoreHistory;
    }

    public boolean chatEnabled() {
        return chatEnabled;
    }

    public boolean chatConsole() {
        return chatConsole;
    }

    public boolean chatMinecraft() {
        return chatMinecraft;
    }

    public String chatPrefix() {
        return chatPrefix == null ? "" : chatPrefix;
    }

    public int chatMaxLength() {
        return chatMaxLength;
    }

    public int chatMessagesPerSecond() {
        return chatMessagesPerSecond;
    }

    public int chatMaxQueue() {
        return chatMaxQueue;
    }

    public boolean eventsEnabled() {
        return eventsEnabled;
    }

    public boolean eventsDefaultEnabled() {
        return eventsDefaultEnabled;
    }

    public int deduplicationWindowSeconds() {
        return deduplicationWindowSeconds;
    }

    public int maxPendingEvents() {
        return maxPendingEvents;
    }

    public boolean eventsIgnoreHistory() {
        return eventsIgnoreHistory;
    }

    public boolean functionsEnabled() {
        return functionsEnabled;
    }

    public int maxConcurrentFunctions() {
        return maxConcurrentFunctions;
    }

    public int maxQueuedFunctions() {
        return maxQueuedFunctions;
    }

    public int maxCommandsPerTick() {
        return maxCommandsPerTick;
    }

    public int maxExecutionSeconds() {
        return maxExecutionSeconds;
    }

    public int maxRepeat() {
        return maxRepeat;
    }

    public int maxDepth() {
        return maxDepth;
    }

    public List<String> allowedCommands() {
        return allowedCommands;
    }

    public TargetPolicy targetPolicy() {
        return targetPolicy;
    }

    public NoPlayerPolicy noPlayerPolicy() {
        return noPlayerPolicy;
    }

    public int maxActionsPerEvent() {
        return maxActionsPerEvent;
    }

    /**
     * The execution limits of the function engine, derived from the
     * configuration.
     */
    public dev.nglmercer.tiktokminecraft.functions.FunctionLimits functionLimits() {
        return new dev.nglmercer.tiktokminecraft.functions.FunctionLimits(
                256, maxRepeat, maxDepth, 1200, maxExecutionSeconds, 256L * 1024L, 32);
    }

    public int maxEntitiesPerEvent() {
        return maxEntitiesPerEvent;
    }

    public int maxEntitiesPerMinute() {
        return maxEntitiesPerMinute;
    }

    public List<String> allowedEntities() {
        return allowedEntities;
    }

    public boolean executeLiveActions() {
        return executeLiveActions;
    }

    public boolean allowConsoleCommands() {
        return allowConsoleCommands;
    }

    public boolean allowRawCommands() {
        return allowRawCommands;
    }

    public boolean logEvents() {
        return logEvents;
    }

    public boolean logConnection() {
        return logConnection;
    }

    public boolean logRuleMatches() {
        return logRuleMatches;
    }

    /** Outcome of a configuration load. */
    public static final class Result {
        private final PluginConfig config;
        private final List<String> problems;

        private Result(PluginConfig config, List<String> problems) {
            this.config = config;
            this.problems = Collections.unmodifiableList(problems);
        }

        static Result ok(PluginConfig config) {
            return new Result(config, new ArrayList<String>());
        }

        static Result failed(List<String> problems) {
            return new Result(null, problems);
        }

        public boolean isValid() {
            return config != null;
        }

        public PluginConfig config() {
            return config;
        }

        public List<String> problems() {
            return problems;
        }
    }
}
