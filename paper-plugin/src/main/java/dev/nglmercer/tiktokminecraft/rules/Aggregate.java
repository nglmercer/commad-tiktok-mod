package dev.nglmercer.tiktokminecraft.rules;

/**
 * A threshold over a counter.
 *
 * <p>The metric is always an event delta. The cumulative room total is never
 * used as the counter, because a total would make the same likes count twice
 * when the threshold is crossed again.</p>
 */
public final class Aggregate {

    /** What is counted. */
    public enum Metric {
        /** The number of likes carried by the event. */
        COUNT("count"),
        /** The cumulative room total carried by the event. */
        TOTAL("total");

        private final String wireName;

        Metric(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static Metric parse(String raw) {
            if (raw == null) {
                return null;
            }
            for (Metric metric : values()) {
                if (metric.wireName.equalsIgnoreCase(raw.trim())) {
                    return metric;
                }
            }
            return null;
        }
    }

    /** What the counter is shared between. */
    public enum Scope {
        /** One counter per event, which never reaches a threshold. */
        EVENT("event"),
        /** One counter per viewer. */
        USER("user"),
        /** One counter for the whole room. */
        GLOBAL("global");

        private final String wireName;

        Scope(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static Scope parse(String raw) {
            if (raw == null) {
                return null;
            }
            for (Scope scope : values()) {
                if (scope.wireName.equalsIgnoreCase(raw.trim())) {
                    return scope;
                }
            }
            return null;
        }
    }

    private final Metric metric;
    private final Scope scope;
    private final long threshold;
    private final boolean carryRemainder;

    public Aggregate(Metric metric, Scope scope, long threshold, boolean carryRemainder) {
        this.metric = metric;
        this.scope = scope;
        this.threshold = threshold;
        this.carryRemainder = carryRemainder;
    }

    public Metric metric() {
        return metric;
    }

    public Scope scope() {
        return scope;
    }

    public long threshold() {
        return threshold;
    }

    public boolean carryRemainder() {
        return carryRemainder;
    }
}
