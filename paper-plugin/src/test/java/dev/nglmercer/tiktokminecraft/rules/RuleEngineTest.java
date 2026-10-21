package dev.nglmercer.tiktokminecraft.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nglmercer.tiktokminecraft.event.TikTokEventContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuleEngineTest {

    private static TikTokEventContext chat(String id, String text) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("chat.text", text);
        return new TikTokEventContext(
                id, "room-1", TikTokEventContext.CHAT, "1", "viewer", "Viewer", 1L, false, payload);
    }

    private static TikTokEventContext like(String id, long count) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("like.count", Long.valueOf(count));
        payload.put("like.total", Long.valueOf(count));
        return new TikTokEventContext(
                id, "room-1", TikTokEventContext.LIKE, "1", "viewer", "Viewer", 1L, false, payload);
    }

    private static EventRule rule(
            String id, String trigger, List<FieldMatcher> matchers, Aggregate aggregate,
            CooldownSpec cooldown) {
        List<ActionSpec> actions = new ArrayList<ActionSpec>();
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("message", "x");
        actions.add(new ActionSpec("broadcast", parameters));
        return new EventRule(
                id, true, trigger, matchers, aggregate, cooldown, null, actions, 0, false, "test");
    }

    @Test void aMatchingRuleIsFound() {
        RuleEngine engine = new RuleEngine(new dev.nglmercer.tiktokminecraft.actions.ActionRegistry());
        List<FieldMatcher> matchers = new ArrayList<FieldMatcher>();
        matchers.add(new FieldMatcher(
                "chat.text",
                Comparison.Operator.EQUALS_IGNORE_CASE,
                "!hello"));
        engine.apply(new RulesSnapshot(
                Collections.singletonList(
                        rule("hello", TikTokEventContext.CHAT, matchers, null, null))));

        assertEquals(1, engine.match(chat("1", "!hello")).size());
        assertEquals(0, engine.match(chat("2", "!other")).size());
    }

    @Test void aDisabledRuleNeverMatches() {
        RuleEngine engine = new RuleEngine(new dev.nglmercer.tiktokminecraft.actions.ActionRegistry());
        EventRule disabled =
                new EventRule(
                        "off", false, TikTokEventContext.CHAT,
                        Collections.<FieldMatcher>emptyList(), null, null, null,
                        Collections.<ActionSpec>emptyList(), 0, false, "test");
        engine.apply(new RulesSnapshot(Collections.singletonList(disabled)));
        assertEquals(0, engine.match(chat("1", "anything")).size());
    }

    @Test void aCooldownSuppressesTheSecondMatch() {
        RuleEngine engine = new RuleEngine(new dev.nglmercer.tiktokminecraft.actions.ActionRegistry());
        CooldownSpec cooldown = new CooldownSpec(60_000L, CooldownSpec.Scope.USER);
        engine.apply(new RulesSnapshot(
                Collections.singletonList(
                        rule("c", TikTokEventContext.CHAT,
                                Collections.<FieldMatcher>emptyList(), null, cooldown))));

        assertEquals(1, engine.match(chat("1", "x")).size());
        assertEquals(0, engine.match(chat("2", "x")).size());
    }

    @Test void aCooldownPerRuleIsSharedByEveryViewer() {
        RuleEngine engine = new RuleEngine(new dev.nglmercer.tiktokminecraft.actions.ActionRegistry());
        CooldownSpec cooldown = new CooldownSpec(60_000L, CooldownSpec.Scope.RULE);
        engine.apply(new RulesSnapshot(
                Collections.singletonList(
                        rule("c", TikTokEventContext.CHAT,
                                Collections.<FieldMatcher>emptyList(), null, cooldown))));

        assertEquals(1, engine.match(chat("1", "x")).size());
        assertEquals(0, engine.match(chat("2", "x")).size());
    }

    @Test void aThresholdIsOnlyCrossedWhenItIsReached() {
        RuleEngine engine = new RuleEngine(new dev.nglmercer.tiktokminecraft.actions.ActionRegistry());
        Aggregate aggregate =
                new Aggregate(Aggregate.Metric.COUNT, Aggregate.Scope.GLOBAL, 50L, true);
        engine.apply(new RulesSnapshot(
                Collections.singletonList(
                        rule("likes", TikTokEventContext.LIKE,
                                Collections.<FieldMatcher>emptyList(), aggregate, null))));

        assertEquals(0, engine.match(like("1", 20)).size());
        assertEquals(0, engine.match(like("2", 20)).size());
        assertEquals(1, engine.match(like("3", 20)).size());
        // The remainder is kept: 10 more likes reach the threshold again.
        assertEquals(0, engine.match(like("4", 10)).size());
        assertEquals(1, engine.match(like("5", 40)).size());
    }

    @Test void oneLargeLikeEventTriggersEveryThresholdUpToBudget() {
        RuleEngine engine = new RuleEngine(new dev.nglmercer.tiktokminecraft.actions.ActionRegistry());
        Aggregate aggregate = new Aggregate(Aggregate.Metric.COUNT, Aggregate.Scope.GLOBAL, 150L, true);
        engine.apply(new RulesSnapshot(Collections.singletonList(
                rule("likes", TikTokEventContext.LIKE,
                        Collections.<FieldMatcher>emptyList(), aggregate, null))));
        assertEquals(3, engine.match(like("burst", 475)).size());
        assertEquals(1, engine.match(like("remainder", 125)).size());
    }

    @Test void aThresholdWithoutRemainderStartsOver() {
        RuleEngine engine = new RuleEngine(new dev.nglmercer.tiktokminecraft.actions.ActionRegistry());
        Aggregate aggregate =
                new Aggregate(Aggregate.Metric.COUNT, Aggregate.Scope.GLOBAL, 50L, false);
        engine.apply(new RulesSnapshot(
                Collections.singletonList(
                        rule("likes", TikTokEventContext.LIKE,
                                Collections.<FieldMatcher>emptyList(), aggregate, null))));

        assertEquals(0, engine.match(like("1", 20)).size());
        assertEquals(1, engine.match(like("2", 40)).size());
        assertEquals(0, engine.match(like("3", 20)).size());
    }

    @Test void aCooldownNeverAffectsAnotherRule() {
        RuleEngine engine = new RuleEngine(new dev.nglmercer.tiktokminecraft.actions.ActionRegistry());
        CooldownSpec cooldown = new CooldownSpec(60_000L, CooldownSpec.Scope.USER);
        // Only the first rule has a cooldown.
        engine.apply(new RulesSnapshot(
                java.util.Arrays.asList(
                        rule("first", TikTokEventContext.CHAT,
                                Collections.<FieldMatcher>emptyList(), null, cooldown),
                        rule("second", TikTokEventContext.CHAT,
                                Collections.<FieldMatcher>emptyList(), null, null))));

        assertEquals(2, engine.match(chat("1", "x")).size());
        // The first rule is quiet now; the second one still fires.
        List<EventRule> second = engine.match(chat("2", "x"));
        assertEquals(1, second.size());
        assertEquals("second", second.get(0).id());
    }

    @Test void everyCooldownScopeBelongsToItsRule() {
        CooldownSpec user = new CooldownSpec(1000L, CooldownSpec.Scope.USER);
        CooldownSpec rule = new CooldownSpec(1000L, CooldownSpec.Scope.RULE);
        CooldownSpec both = new CooldownSpec(1000L, CooldownSpec.Scope.USER_AND_RULE);
        CooldownSpec global = new CooldownSpec(1000L, CooldownSpec.Scope.GLOBAL);

        assertTrue(user.key("a", "viewer").contains("|rule:a"));
        assertTrue(both.key("a", "viewer").contains("|rule:a"));
        assertTrue(rule.key("a", "viewer").contains("rule:a"));
        // The global scope is the only one that is shared.
        assertEquals("global", global.key("a", "viewer"));
        // Two rules never share a cooldown.
        assertFalse(user.key("a", "viewer").equals(user.key("b", "viewer")));
    }

    @Test void aGlobalCooldownIsSharedByEveryRule() {
        RuleEngine engine = new RuleEngine(new dev.nglmercer.tiktokminecraft.actions.ActionRegistry());
        CooldownSpec cooldown = new CooldownSpec(60_000L, CooldownSpec.Scope.GLOBAL);
        engine.apply(new RulesSnapshot(
                java.util.Arrays.asList(
                        rule("first", TikTokEventContext.CHAT,
                                Collections.<FieldMatcher>emptyList(), null, cooldown),
                        rule("second", TikTokEventContext.CHAT,
                                Collections.<FieldMatcher>emptyList(), null, cooldown))));

        assertEquals(1, engine.match(chat("1", "x")).size());
        assertEquals(0, engine.match(chat("2", "x")).size());
    }

    @Test void aThresholdPerUserIsCountedSeparately() {
        RuleEngine engine = new RuleEngine(new dev.nglmercer.tiktokminecraft.actions.ActionRegistry());
        Aggregate aggregate =
                new Aggregate(Aggregate.Metric.COUNT, Aggregate.Scope.USER, 50L, true);
        engine.apply(new RulesSnapshot(
                Collections.singletonList(
                        rule("likes", TikTokEventContext.LIKE,
                                Collections.<FieldMatcher>emptyList(), aggregate, null))));

        assertEquals(0, engine.match(like("1", 30)).size());
        Map<String, Object> other = new LinkedHashMap<String, Object>();
        other.put("like.count", Long.valueOf(30));
        TikTokEventContext second =
                new TikTokEventContext(
                        "2", "room-1", TikTokEventContext.LIKE, "2", "other", "Other", 1L, false, other);
        // A different viewer has their own counter.
        assertEquals(0, engine.match(second).size());
        assertEquals(1, engine.match(like("3", 20)).size());
    }

    @Test void anOverrideForcesARuleOnOrOff() {
        RuleEngine engine = new RuleEngine(new dev.nglmercer.tiktokminecraft.actions.ActionRegistry());
        EventRule disabled =
                new EventRule(
                        "r", false, TikTokEventContext.CHAT,
                        Collections.<FieldMatcher>emptyList(), null, null, null,
                        Collections.<ActionSpec>emptyList(), 0, false, "test");
        engine.apply(new RulesSnapshot(Collections.singletonList(disabled)));
        assertEquals(0, engine.match(chat("1", "x")).size());

        assertTrue(engine.setEnabled("r", true));
        assertEquals(1, engine.match(chat("2", "x")).size());
        assertFalse(engine.setEnabled("missing", true));
    }

    @Test void rulesAreEvaluatedByPriority() {
        RuleEngine engine = new RuleEngine(new dev.nglmercer.tiktokminecraft.actions.ActionRegistry());
        EventRule low = new EventRule(
                "low", true, TikTokEventContext.CHAT, Collections.<FieldMatcher>emptyList(),
                null, null, null, Collections.<ActionSpec>emptyList(), 1, false, "test");
        EventRule high = new EventRule(
                "high", true, TikTokEventContext.CHAT, Collections.<FieldMatcher>emptyList(),
                null, null, null, Collections.<ActionSpec>emptyList(), 10, false, "test");
        engine.apply(new RulesSnapshot(Arrays.asList(low, high)));
        List<EventRule> matched = engine.match(chat("1", "x"));
        assertEquals("high", matched.get(0).id());
    }
}
