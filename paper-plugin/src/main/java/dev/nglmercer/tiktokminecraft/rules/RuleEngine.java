package dev.nglmercer.tiktokminecraft.rules;

import dev.nglmercer.tiktokminecraft.actions.ActionContext;
import dev.nglmercer.tiktokminecraft.actions.ActionRegistry;
import dev.nglmercer.tiktokminecraft.actions.ActionResult;
import dev.nglmercer.tiktokminecraft.event.TikTokEventContext;
import dev.nglmercer.tiktokminecraft.log.LogCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Matches events against the rules and runs the actions of the rules that
 * match.
 *
 * <p>A rule fires when its trigger matches, when every matcher and condition
 * holds, when its threshold has been crossed and when it is not on
 * cooldown. The threshold is checked before the cooldown, so a counter
 * always reflects the events that arrived.</p>
 */
public final class RuleEngine {

    private final ActionRegistry registry;
    private final CooldownStore cooldowns = new CooldownStore(8192);
    private final ThresholdCounter counters = new ThresholdCounter(4096);
    private final Map<String, Boolean> overrides = new ConcurrentHashMap<String, Boolean>();
    private final java.util.concurrent.atomic.AtomicLong actionsExecuted =
            new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong rejected =
            new java.util.concurrent.atomic.AtomicLong();
    private volatile RulesSnapshot snapshot = RulesSnapshot.empty();

    public RuleEngine(ActionRegistry registry) {
        this.registry = registry;
    }

    /**
     * Forces a rule on or off, regardless of what its file says.
     *
     * @return true when the override was applied
     */
    public boolean setEnabled(String ruleId, boolean enabled) {
        if (ruleId == null || snapshot.byId(ruleId) == null) {
            return false;
        }
        overrides.put(ruleId, Boolean.valueOf(enabled));
        return true;
    }

    /** @return the override for a rule, or null when there is none */
    public Boolean override(String ruleId) {
        return overrides.get(ruleId);
    }

    /** Replaces the active rules. */
    public void apply(RulesSnapshot rules) {
        this.snapshot = rules == null ? RulesSnapshot.empty() : rules;
    }

    public RulesSnapshot snapshot() {
        return snapshot;
    }

    /** @return the rules that would fire for an event */
    public List<EventRule> match(TikTokEventContext event) {
        List<EventRule> matched = new ArrayList<EventRule>();
        for (EventRule rule : snapshot.forTrigger(event.type())) {
            if (matches(rule, event)) {
                matched.add(rule);
            }
        }
        return matched;
    }

    private boolean matches(EventRule rule, TikTokEventContext event) {
        Boolean override = overrides.get(rule.id());
        boolean enabled = override == null ? rule.enabled() : override.booleanValue();
        if (!enabled) {
            return false;
        }
        if (!rule.matches(event)) {
            return false;
        }
        Aggregate aggregate = rule.aggregate();
        if (aggregate != null) {
            long delta = metricValue(aggregate, event);
            String key = aggregateKey(aggregate, rule, event);
            long crossed = counters.add(key, delta, aggregate.threshold(), aggregate.carryRemainder());
            if (crossed < 1) {
                return false;
            }
        }
        CooldownSpec cooldown = rule.cooldown();
        if (cooldown != null) {
            String key = cooldown.key(rule.id(), userKey(event));
            if (!cooldowns.tryAcquire(key, cooldown.durationMillis())) {
                return false;
            }
        }
        return true;
    }

    private static long metricValue(Aggregate aggregate, TikTokEventContext event) {
        if (aggregate.metric() == Aggregate.Metric.TOTAL) {
            return event.likeTotal();
        }
        return event.likeCount();
    }

    private static String aggregateKey(Aggregate aggregate, EventRule rule,
            TikTokEventContext event) {
        switch (aggregate.scope()) {
            case USER:
                return rule.id() + "|user:" + userKey(event);
            case EVENT:
                return rule.id() + "|event:" + event.eventId();
            case GLOBAL:
            default:
                return rule.id() + "|global";
        }
    }

    private static String userKey(TikTokEventContext event) {
        if (!event.uniqueId().isEmpty()) {
            return event.uniqueId();
        }
        if (!event.userId().isEmpty()) {
            return event.userId();
        }
        return "";
    }

    /**
     * Runs the actions of a rule.
     *
     * @return the number of actions that ran
     */
    public int execute(EventRule rule, TikTokEventContext event, ActionContext context) {
        int executed = 0;
        for (ActionSpec spec : rule.actions()) {
            if (!context.consumeActionSlot()) {
                rejected.incrementAndGet();
                break;
            }
            dev.nglmercer.tiktokminecraft.actions.Action action = registry.get(spec.type());
            if (action == null) {
                rejected.incrementAndGet();
                continue;
            }
            try {
                ActionResult result = action.execute(spec, context);
                if (result.isSuccess()) {
                    executed++;
                    actionsExecuted.incrementAndGet();
                } else {
                    rejected.incrementAndGet();
                }
                context.log().debug(LogCategory.ACTIONS,
                        rule.id() + " -> " + spec.type() + ": " + result);
            } catch (dev.nglmercer.tiktokminecraft.actions.ActionException failure) {
                rejected.incrementAndGet();
                context.log().warn(LogCategory.ACTIONS,
                        rule.id() + " -> " + spec.type() + " failed: " + failure.getMessage());
            } catch (RuntimeException failure) {
                rejected.incrementAndGet();
                context.log().warn(LogCategory.ACTIONS,
                        rule.id() + " -> " + spec.type() + " failed: " + failure.getMessage());
            }
        }
        return executed;
    }

    /** Forgets the cooldowns and the counters. */
    public void reset() {
        cooldowns.clear();
        counters.clear();
    }

    public int cooldownKeys() {
        return cooldowns.size();
    }

    /** The number of actions that ran. */
    public long actionsExecuted() {
        return actionsExecuted.get();
    }

    /** The number of actions that were skipped or failed. */
    public long actionsRejected() {
        return rejected.get();
    }

    public int counterKeys() {
        return counters.size();
    }
}
