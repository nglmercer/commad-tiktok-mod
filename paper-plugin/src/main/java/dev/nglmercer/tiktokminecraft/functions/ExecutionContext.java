package dev.nglmercer.tiktokminecraft.functions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The context a function runs in.
 *
 * <p>It carries the target the function acts on. A function that targets a
 * player is cancelled when that player leaves the server.</p>
 */
public final class ExecutionContext {

    private final UUID targetPlayer;
    private final String ruleId;
    private final String origin;

    public ExecutionContext(UUID targetPlayer, String ruleId, String origin) {
        this.targetPlayer = targetPlayer;
        this.ruleId = ruleId;
        this.origin = origin;
    }

    /** A context without a player target. */
    public static ExecutionContext server(String origin) {
        return new ExecutionContext(null, null, origin);
    }

    public UUID targetPlayer() {
        return targetPlayer;
    }

    /** The rule that started the run, if any. */
    public String ruleId() {
        return ruleId;
    }

    /** A human readable description of what started the run. */
    public String origin() {
        return origin;
    }

    /** The parameters the function was called with. */
    public static Map<String, Object> emptyArguments() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, Object>());
    }

    @Override
    public String toString() {
        return origin == null ? "run" : origin;
    }
}
