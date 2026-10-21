package dev.nglmercer.tiktokminecraft.log;

/** The operational log categories used by the plugin. */
public enum LogCategory {
    CONNECTION("connection"),
    EVENTS("events"),
    CHAT("chat"),
    RULES("rules"),
    ACTIONS("actions"),
    FUNCTIONS("functions"),
    SECURITY("security"),
    PERFORMANCE("performance");

    private final String wireName;

    LogCategory(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
