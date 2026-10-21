package dev.nglmercer.tiktokminecraft.rules;

import java.util.Collections;
import java.util.List;

/**
 * A compiled rule.
 *
 * <p>Every rule format produces this exact representation, so YAML, JSON and
 * the DSL all share one engine, one set of limits and one set of
 * diagnostics.</p>
 */
public final class EventRule {

    private final String id;
    private final boolean enabled;
    private final String trigger;
    private final List<FieldMatcher> matchers;
    private final Aggregate aggregate;
    private final CooldownSpec cooldown;
    private final Condition condition;
    private final List<ActionSpec> actions;
    private final int priority;
    private final boolean stopProcessing;
    private final String source;

    public EventRule(
            String id,
            boolean enabled,
            String trigger,
            List<FieldMatcher> matchers,
            Aggregate aggregate,
            CooldownSpec cooldown,
            Condition condition,
            List<ActionSpec> actions,
            int priority,
            boolean stopProcessing,
            String source) {
        this.id = id;
        this.enabled = enabled;
        this.trigger = trigger;
        this.matchers = Collections.unmodifiableList(matchers);
        this.aggregate = aggregate;
        this.cooldown = cooldown;
        this.condition = condition;
        this.actions = Collections.unmodifiableList(actions);
        this.priority = priority;
        this.stopProcessing = stopProcessing;
        this.source = source;
    }

    public String id() {
        return id;
    }

    public boolean enabled() {
        return enabled;
    }

    public String trigger() {
        return trigger;
    }

    public List<FieldMatcher> matchers() {
        return matchers;
    }

    public Aggregate aggregate() {
        return aggregate;
    }

    public CooldownSpec cooldown() {
        return cooldown;
    }

    public Condition condition() {
        return condition;
    }

    public List<ActionSpec> actions() {
        return actions;
    }

    public int priority() {
        return priority;
    }

    public boolean stopProcessing() {
        return stopProcessing;
    }

    /** The file the rule came from, used in diagnostics. */
    public String source() {
        return source;
    }

    /** @return true when every matcher holds for the event */
    public boolean matches(dev.nglmercer.tiktokminecraft.event.TikTokEventContext event) {
        for (FieldMatcher matcher : matchers) {
            if (!matcher.matches(event)) {
                return false;
            }
        }
        return condition == null || condition.test(event);
    }

    @Override
    public String toString() {
        return id + " (" + trigger + ")";
    }
}
