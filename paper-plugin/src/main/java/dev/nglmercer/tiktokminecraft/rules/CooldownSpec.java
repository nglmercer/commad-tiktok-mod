package dev.nglmercer.tiktokminecraft.rules;

/**
 * How long a rule stays quiet after it fired.
 *
 * <p>Every cooldown belongs to its rule, so one rule can never silence
 * another. Only the {@code global} scope is shared by everything, which is
 * what a deliberate "quiet period" looks like.</p>
 */
public final class CooldownSpec {

    /** What the cooldown is shared between. */
    public enum Scope {
        /** One timer for the whole rule, shared by every viewer. */
        RULE("rule"),
        /** One timer per viewer, for this rule. */
        USER("user"),
        /** One timer per viewer and rule. */
        USER_AND_RULE("user-and-rule"),
        /** One timer shared by every rule and every viewer. */
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
            String name = raw.trim().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
            for (Scope scope : values()) {
                if (scope.wireName.equals(name)) {
                    return scope;
                }
            }
            return null;
        }
    }

    private final long durationMillis;
    private final Scope scope;

    public CooldownSpec(long durationMillis, Scope scope) {
        this.durationMillis = durationMillis;
        this.scope = scope;
    }

    public long durationMillis() {
        return durationMillis;
    }

    public Scope scope() {
        return scope;
    }

    /**
     * The key a cooldown is stored under.
     *
     * <p>Every scope but {@code global} includes the rule, so a cooldown
     * only ever affects the rule that owns it.</p>
     *
     * @param ruleId the rule that owns the cooldown
     * @param userKey the viewer's identity, or an empty string
     */
    public String key(String ruleId, String userKey) {
        switch (scope) {
            case USER:
            case USER_AND_RULE:
                return "user:" + userKey + "|rule:" + ruleId;
            case RULE:
                return "rule:" + ruleId;
            case GLOBAL:
            default:
                return "global";
        }
    }
}
