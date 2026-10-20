package dev.nglmercer.tiktokminecraft.actions;

import dev.nglmercer.tiktokminecraft.rules.ActionSpec;

/**
 * One thing a rule can do when it matches.
 *
 * <p>An action is validated when the rules are loaded, so a broken action is
 * reported with the file and rule that caused it, long before a LIVE event
 * arrives. It is executed on the server thread.</p>
 */
public abstract class Action {

    /** The type name used in a rule file. */
    public abstract String type();

    /**
     * Checks the parameters.
     *
     * @throws ActionException when the action can never run
     */
    public abstract void validate(ActionSpec spec, ActionContext context) throws ActionException;

    /** Runs the action. Must be called on the server thread. */
    public abstract ActionResult execute(ActionSpec spec, ActionContext context)
            throws ActionException;

    /** A required string parameter. */
    protected static String require(ActionSpec spec, String name) throws ActionException {
        Object value = spec.parameter(name);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new ActionException("'" + name + "' is required");
        }
        return String.valueOf(value).trim();
    }

    /** An optional string parameter. */
    protected static String optional(ActionSpec spec, String name, String fallback) {
        Object value = spec.parameter(name);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    /** An optional integer parameter. */
    protected static int optionalInt(ActionSpec spec, String name, int fallback)
            throws ActionException {
        Object value = spec.parameter(name);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException failure) {
            throw new ActionException("'" + name + "' must be a whole number");
        }
    }

    /** An optional double parameter. */
    protected static double optionalDouble(ActionSpec spec, String name, double fallback)
            throws ActionException {
        Object value = spec.parameter(name);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException failure) {
            throw new ActionException("'" + name + "' must be a number");
        }
    }
}
