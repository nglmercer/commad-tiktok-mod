package dev.nglmercer.tiktokminecraft.rules;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The typed comparisons a rule may use.
 *
 * <p>No expression is ever evaluated. A comparison always takes one field
 * and one literal value, which keeps rule files declarative and free of any
 * code path a viewer could influence.</p>
 */
public final class Comparison {

    /** The supported operators. */
    public enum Operator {
        EQUALS("equals"),
        EQUALS_IGNORE_CASE("equals-ignore-case"),
        NOT_EQUAL("not-equal"),
        CONTAINS("contains"),
        STARTS_WITH("starts-with"),
        ENDS_WITH("ends-with"),
        GREATER_THAN("greater-than"),
        GREATER_OR_EQUAL("greater-or-equal"),
        LESS_THAN("less-than"),
        LESS_OR_EQUAL("less-or-equal"),
        IN("in");

        private final String wireName;

        Operator(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }
    }

    private static final List<String> NAMES = Arrays.asList(
            "equals", "equals-ignore-case", "not-equal", "contains", "starts-with", "ends-with",
            "greater-than", "greater-or-equal", "less-than", "less-or-equal", "in");

    /** The symbolic names, which the script format uses. */
    private static final java.util.Map<String, Operator> SYMBOLS =
            new java.util.LinkedHashMap<String, Operator>();

    static {
        SYMBOLS.put("==", Operator.EQUALS);
        SYMBOLS.put("!=", Operator.NOT_EQUAL);
        SYMBOLS.put(">", Operator.GREATER_THAN);
        SYMBOLS.put(">=", Operator.GREATER_OR_EQUAL);
        SYMBOLS.put("<", Operator.LESS_THAN);
        SYMBOLS.put("<=", Operator.LESS_OR_EQUAL);
    }

    private Comparison() {
    }

    /** @return the operator, or null when the name is not supported */
    public static Operator parse(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.trim().toLowerCase(Locale.ROOT);
        Operator symbolic = SYMBOLS.get(name);
        if (symbolic != null) {
            return symbolic;
        }
        for (Operator operator : Operator.values()) {
            if (operator.wireName.equals(name)) {
                return operator;
            }
        }
        return null;
    }

    /** The names a rule file may use. */
    public static Set<String> names() {
        return new LinkedHashSet<String>(NAMES);
    }

    /**
     * Applies a comparison.
     *
     * @param actual the value read from the event
     * @param expected the literal value from the rule
     */
    public static boolean test(Operator operator, Object actual, Object expected) {
        if (operator == null) {
            return false;
        }
        switch (operator) {
            case IN:
                return containsIn(actual, expected);
            case EQUALS:
                return equal(actual, expected, false);
            case EQUALS_IGNORE_CASE:
                return equal(actual, expected, true);
            case NOT_EQUAL:
                return !equal(actual, expected, false);
            case CONTAINS:
                return text(actual).contains(text(expected));
            case STARTS_WITH:
                return text(actual).startsWith(text(expected));
            case ENDS_WITH:
                return text(actual).endsWith(text(expected));
            case GREATER_THAN:
                return isNumeric(actual, expected) && compare(actual, expected) > 0;
            case GREATER_OR_EQUAL:
                return isNumeric(actual, expected) && compare(actual, expected) >= 0;
            case LESS_THAN:
                return isNumeric(actual, expected) && compare(actual, expected) < 0;
            case LESS_OR_EQUAL:
                return isNumeric(actual, expected) && compare(actual, expected) <= 0;
            default:
                return false;
        }
    }

    private static boolean equal(Object actual, Object expected, boolean ignoreCase) {
        if (actual instanceof Number && expected instanceof Number) {
            return ((Number) actual).longValue() == ((Number) expected).longValue();
        }
        String left = text(actual);
        String right = text(expected);
        if (ignoreCase) {
            return left.equalsIgnoreCase(right);
        }
        return left.equals(right);
    }

    @SuppressWarnings("unchecked")
    private static boolean containsIn(Object actual, Object expected) {
        if (expected instanceof Iterable) {
            for (Object candidate : (Iterable<Object>) expected) {
                if (equal(actual, candidate, false)) {
                    return true;
                }
            }
            return false;
        }
        if (expected instanceof Object[]) {
            for (Object candidate : (Object[]) expected) {
                if (equal(actual, candidate, false)) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    /**
     * @return true when both values can be read as numbers
     */
    public static boolean isNumeric(Object actual, Object expected) {
        return number(actual) != null && number(expected) != null;
    }

    /**
     * Compares two values.
     *
     * <p>Numbers are compared numerically, everything else as text. A value
     * that is not a number never satisfies a numeric comparison.</p>
     */
    public static int compare(Object actual, Object expected) {
        Double left = number(actual);
        Double right = number(expected);
        if (left != null && right != null) {
            return Double.compare(left.doubleValue(), right.doubleValue());
        }
        return text(actual).compareTo(text(expected));
    }

    private static Double number(Object value) {
        if (value instanceof Number) {
            return Double.valueOf(((Number) value).doubleValue());
        }
        if (value instanceof Boolean) {
            return null;
        }
        if (value instanceof String) {
            try {
                return Double.valueOf(Double.parseDouble(((String) value).trim()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Iterable) {
            StringBuilder builder = new StringBuilder();
            for (Object item : (Iterable<?>) value) {
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(String.valueOf(item));
            }
            return builder.toString();
        }
        return String.valueOf(value);
    }
}
