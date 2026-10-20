package dev.nglmercer.tiktokminecraft.rules;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One action a rule wants to run.
 *
 * <p>The specification is deliberately untyped: the rule format (YAML, JSON or
 * the DSL) only says which action type to run and with which parameters. The
 * action registry decides what those parameters mean and validates them
 * before anything is executed.</p>
 */
public final class ActionSpec {

    private final String type;
    private final Map<String, Object> parameters;

    public ActionSpec(String type, Map<String, Object> parameters) {
        this.type = type;
        this.parameters = Collections.unmodifiableMap(
                parameters == null
                        ? new LinkedHashMap<String, Object>()
                        : new LinkedHashMap<String, Object>(parameters));
    }

    public String type() {
        return type;
    }

    public Map<String, Object> parameters() {
        return parameters;
    }

    /** @return a parameter, or null when it is absent */
    public Object parameter(String name) {
        return parameters.get(name);
    }

    /** @return a string parameter, or the fallback when it is absent */
    public String string(String name, String fallback) {
        Object value = parameters.get(name);
        if (value == null) {
            return fallback;
        }
        return String.valueOf(value);
    }

    @Override
    public String toString() {
        return type + parameters;
    }
}
