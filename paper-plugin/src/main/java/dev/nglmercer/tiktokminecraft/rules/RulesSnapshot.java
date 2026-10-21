package dev.nglmercer.tiktokminecraft.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An immutable set of rules.
 *
 * <p>A snapshot is built completely before it replaces the running one, so a
 * broken rule file can never leave the server with a half-applied
 * configuration.</p>
 */
public final class RulesSnapshot {

    /** The trigger names a rule may use. */
    private static final Map<String, String> TRIGGERS = new LinkedHashMap<String, String>();

    static {
        TRIGGERS.put("chat", "chat");
        TRIGGERS.put("chat.emote", "chat.emote");
        TRIGGERS.put("gift", "gift");
        TRIGGERS.put("gift.final", "gift.final");
        TRIGGERS.put("like", "like");
        TRIGGERS.put("member", "member");
        TRIGGERS.put("social.follow", "social.follow");
        TRIGGERS.put("social.share", "social.share");
        TRIGGERS.put("social.other", "social.other");
        TRIGGERS.put("social.unknown", "social.unknown");
        TRIGGERS.put("room.user", "room.user");
        TRIGGERS.put("unknown", "unknown");
    }

    private final List<EventRule> rules;
    private final Map<String, List<EventRule>> byTrigger;

    public RulesSnapshot(List<EventRule> rules) {
        List<EventRule> sorted = new ArrayList<EventRule>(rules);
        // Higher priority first; the original order breaks a tie.
        Collections.sort(sorted, (left, right) -> {
            int difference = right.priority() - left.priority();
            return difference != 0 ? difference : 0;
        });
        this.rules = Collections.unmodifiableList(sorted);
        Map<String, List<EventRule>> index = new LinkedHashMap<String, List<EventRule>>();
        for (EventRule rule : sorted) {
            List<EventRule> bucket = index.get(rule.trigger());
            if (bucket == null) {
                bucket = new ArrayList<EventRule>();
                index.put(rule.trigger(), bucket);
            }
            bucket.add(rule);
        }
        this.byTrigger = Collections.unmodifiableMap(index);
    }

    /** An empty snapshot. */
    public static RulesSnapshot empty() {
        return new RulesSnapshot(Collections.<EventRule>emptyList());
    }

    public List<EventRule> rules() {
        return rules;
    }

    /** @return the rules for a trigger, in evaluation order */
    public List<EventRule> forTrigger(String trigger) {
        List<EventRule> bucket = byTrigger.get(trigger);
        return bucket == null ? Collections.<EventRule>emptyList() : bucket;
    }

    /** @return the rule with an id, or null */
    public EventRule byId(String id) {
        for (EventRule rule : rules) {
            if (rule.id().equals(id)) {
                return rule;
            }
        }
        return null;
    }

    public int size() {
        return rules.size();
    }

    /** @return true when the trigger name is known */
    public static boolean isKnownTrigger(String trigger) {
        return trigger != null && TRIGGERS.containsKey(trigger.trim().toLowerCase(Locale.ROOT));
    }

    /** The known trigger names. */
    public static List<String> triggers() {
        return Collections.unmodifiableList(new ArrayList<String>(TRIGGERS.keySet()));
    }
}
