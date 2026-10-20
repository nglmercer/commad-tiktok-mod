package dev.nglmercer.tiktokminecraft.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nglmercer.tiktokminecraft.actions.ActionContext;
import dev.nglmercer.tiktokminecraft.actions.ActionRegistry;
import dev.nglmercer.tiktokminecraft.config.PluginConfig;
import dev.nglmercer.tiktokminecraft.rules.Aggregate;
import dev.nglmercer.tiktokminecraft.rules.CooldownSpec;
import dev.nglmercer.tiktokminecraft.rules.EventRule;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class DslCompilerTest {

    private static ActionContext validationContext() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("tiktok.unique-id", "creator");
        PluginConfig config = PluginConfig.load(yaml).config();
        return new ActionContext(null, config, null, null, null, null, null);
    }

    private static List<EventRule> compile(String source) throws DslException {
        return new DslCompiler("example.ttl", new ActionRegistry(), validationContext())
                .compile(source);
    }

    @Test void aChatRuleIsCompiled() throws Exception {
        List<EventRule> rules = compile(
                "on chat where chat.text == \"!hello\" {\n"
                        + "    cooldown 10s per user\n"
                        + "    broadcast \"Hello from TikTok!\"\n"
                        + "}\n");
        assertEquals(1, rules.size());
        EventRule rule = rules.get(0);
        assertEquals("chat", rule.trigger());
        assertEquals(10000L, rule.cooldown().durationMillis());
        assertEquals(CooldownSpec.Scope.USER, rule.cooldown().scope());
        assertEquals(1, rule.actions().size());
        assertEquals("broadcast", rule.actions().get(0).type());
        assertEquals("Hello from TikTok!",
                rule.actions().get(0).parameter("message"));
    }

    @Test void aCallCarriesItsArguments() throws Exception {
        List<EventRule> rules = compile(
                "on gift.final where gift.name == \"Rose\" {\n"
                        + "    call gifts:spawn_zombies(count = 1)\n"
                        + "}\n");
        EventRule rule = rules.get(0);
        assertEquals("function", rule.actions().get(0).type());
        assertEquals("gifts:spawn_zombies", rule.actions().get(0).parameter("name"));
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> args =
                (java.util.Map<String, Object>) rule.actions().get(0).parameter("args");
        assertEquals(1L, ((Number) args.get("count")).longValue());
    }

    @Test void aThresholdIsCompiled() throws Exception {
        List<EventRule> rules = compile("on like every 50 global {\n    broadcast \"x\"\n}\n");
        EventRule rule = rules.get(0);
        assertEquals(Aggregate.Metric.COUNT, rule.aggregate().metric());
        assertEquals(Aggregate.Scope.GLOBAL, rule.aggregate().scope());
        assertEquals(50L, rule.aggregate().threshold());
    }

    @Test void severalRulesAreCompiled() throws Exception {
        List<EventRule> rules = compile(
                "on chat {\n    broadcast \"a\"\n}\n"
                        + "on social.follow {\n    broadcast \"b\"\n}\n");
        assertEquals(2, rules.size());
        assertEquals("chat", rules.get(0).trigger());
        assertEquals("social.follow", rules.get(1).trigger());
        assertFalse(rules.get(0).id().equals(rules.get(1).id()));
    }

    @Test void anUnknownTriggerIsRejected() {
        DslException failure = assertThrowsDsl(
                "on nonsense {\n    broadcast \"x\"\n}\n");
        assertTrue(failure.getMessage().contains("nonsense"), failure.getMessage());
    }

    @Test void anUnknownFieldIsRejected() {
        DslException failure = assertThrowsDsl(
                "on chat where nonsense == \"x\" {\n    broadcast \"x\"\n}\n");
        assertTrue(failure.getMessage().contains("nonsense"), failure.getMessage());
    }

    @Test void anUnknownStatementIsRejected() {
        DslException failure = assertThrowsDsl(
                "on chat {\n    launch-missiles \"x\"\n}\n");
        assertTrue(failure.getMessage().contains("launch-missiles"), failure.getMessage());
    }

    @Test void anUnclosedRuleIsRejected() {
        DslException failure = assertThrowsDsl("on chat {\n    broadcast \"x\"\n");
        assertTrue(failure.getMessage().contains("closed"), failure.getMessage());
    }

    @Test void anUnsupportedComparisonIsRejected() {
        DslException failure = assertThrowsDsl(
                "on chat where chat.text === \"x\" {\n    broadcast \"x\"\n}\n");
        assertTrue(failure.getMessage().contains("comparison"), failure.getMessage());
    }

    @Test void anErrorCarriesThePosition() {
        DslException failure = assertThrowsDsl(
                "on chat {\n    broadcast \"x\"\n    nonsense\n}\n");
        assertEquals(3, failure.line());
        assertTrue(failure.getMessage().contains("example.ttl:3:"), failure.getMessage());
    }

    @Test void aThresholdIsOnlyAllowedOnLikes() {
        DslException failure = assertThrowsDsl(
                "on chat every 50 global {\n    broadcast \"x\"\n}\n");
        assertTrue(failure.getMessage().contains("like"), failure.getMessage());
    }

    @Test void aRuleWithoutStatementsIsRejected() {
        DslException failure = assertThrowsDsl("on chat {\n}\n");
        assertTrue(failure.getMessage().contains("statement"), failure.getMessage());
    }

    @Test void theLexerReportsThePositionOfAnUnexpectedCharacter() {
        DslException failure = assertThrowsDsl("on chat {\n    broadcast $x\n}\n");
        assertEquals(2, failure.line());
    }

    private static DslException assertThrowsDsl(String source) {
        try {
            compile(source);
        } catch (DslException failure) {
            return failure;
        }
        throw new AssertionError("The script was expected to be rejected");
    }
}
