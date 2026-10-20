package dev.nglmercer.tiktokminecraft.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nglmercer.tiktokminecraft.actions.ActionContext;
import dev.nglmercer.tiktokminecraft.actions.ActionRegistry;
import dev.nglmercer.tiktokminecraft.config.PluginConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlRuleLoaderTest {

    private static ActionContext validationContext() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("tiktok.unique-id", "creator");
        PluginConfig config = PluginConfig.load(yaml).config();
        return new ActionContext(null, config, null, null, null, null, null);
    }

    private static YamlRuleLoader loader() {
        return new YamlRuleLoader(new ActionRegistry(), validationContext());
    }

    private static void write(Path directory, String name, String content) throws IOException {
        Files.write(directory.resolve(name), content.getBytes(StandardCharsets.UTF_8));
    }

    @Test void aValidRuleIsLoaded(@TempDir Path directory) throws Exception {
        write(directory, "chat.yml",
                "rules:\n"
                        + "  - id: chat-hello\n"
                        + "    enabled: true\n"
                        + "    trigger: chat\n"
                        + "    match:\n"
                        + "      text:\n"
                        + "        equals-ignore-case: \"!hello\"\n"
                        + "    cooldown:\n"
                        + "      duration: 10s\n"
                        + "      scope: user\n"
                        + "    actions:\n"
                        + "      - type: broadcast\n"
                        + "        message: \"Hello from TikTok!\"\n");
        YamlRuleLoader.LoadResult result = loader().load(directory);

        assertTrue(result.isValid(), String.valueOf(result.failures()));
        assertEquals(1, result.snapshot().size());
        EventRule rule = result.snapshot().rules().get(0);
        assertEquals("chat-hello", rule.id());
        assertEquals("chat", rule.trigger());
        assertTrue(rule.enabled());
        assertEquals(1, rule.matchers().size());
        assertEquals("chat.text", rule.matchers().get(0).field());
        assertEquals(Comparison.Operator.EQUALS_IGNORE_CASE,
                rule.matchers().get(0).operator());
        assertEquals(10000L, rule.cooldown().durationMillis());
        assertEquals(CooldownSpec.Scope.USER, rule.cooldown().scope());
        assertEquals(1, rule.actions().size());
        assertEquals("broadcast", rule.actions().get(0).type());
    }

    @Test void aPlainValueIsAnEqualityTest(@TempDir Path directory) throws Exception {
        write(directory, "gifts.yml",
                "rules:\n"
                        + "  - id: rose\n"
                        + "    trigger: gift.final\n"
                        + "    match:\n"
                        + "      gift-name: \"Rose\"\n"
                        + "    actions:\n"
                        + "      - type: broadcast\n"
                        + "        message: \"A rose\"\n");
        YamlRuleLoader.LoadResult result = loader().load(directory);

        assertTrue(result.isValid(), String.valueOf(result.failures()));
        EventRule rule = result.snapshot().rules().get(0);
        assertEquals(Comparison.Operator.EQUALS, rule.matchers().get(0).operator());
        assertEquals("Rose", rule.matchers().get(0).value());
    }

    @Test void anUnknownTriggerIsRejected(@TempDir Path directory) throws Exception {
        write(directory, "bad.yml",
                "rules:\n"
                        + "  - id: broken\n"
                        + "    trigger: nonsense\n"
                        + "    actions:\n"
                        + "      - type: broadcast\n"
                        + "        message: \"x\"\n");
        YamlRuleLoader.LoadResult result = loader().load(directory);

        assertFalse(result.isValid());
        String problem = result.failures().get("bad.yml");
        assertTrue(problem.contains("bad.yml"), problem);
        assertTrue(problem.contains("broken"), problem);
        assertTrue(problem.contains("nonsense"), problem);
    }

    @Test void anUnknownActionTypeIsRejected(@TempDir Path directory) throws Exception {
        write(directory, "bad.yml",
                "rules:\n"
                        + "  - id: broken\n"
                        + "    trigger: chat\n"
                        + "    actions:\n"
                        + "      - type: launch-missiles\n");
        YamlRuleLoader.LoadResult result = loader().load(directory);

        assertFalse(result.isValid());
        String problem = result.failures().get("bad.yml");
        assertTrue(problem.contains("launch-missiles"), problem);
        assertTrue(problem.contains("broken"), problem);
    }

    @Test void anUnknownFieldIsRejected(@TempDir Path directory) throws Exception {
        write(directory, "bad.yml",
                "rules:\n"
                        + "  - id: broken\n"
                        + "    trigger: chat\n"
                        + "    match:\n"
                        + "      nonsense: \"x\"\n"
                        + "    actions:\n"
                        + "      - type: broadcast\n"
                        + "        message: \"x\"\n");
        YamlRuleLoader.LoadResult result = loader().load(directory);

        assertFalse(result.isValid());
        assertTrue(result.failures().get("bad.yml").contains("nonsense"));
    }

    @Test void aMalformedCooldownIsRejected(@TempDir Path directory) throws Exception {
        write(directory, "bad.yml",
                "rules:\n"
                        + "  - id: broken\n"
                        + "    trigger: chat\n"
                        + "    cooldown:\n"
                        + "      duration: soon\n"
                        + "      scope: user\n"
                        + "    actions:\n"
                        + "      - type: broadcast\n"
                        + "        message: \"x\"\n");
        YamlRuleLoader.LoadResult result = loader().load(directory);

        assertFalse(result.isValid());
        assertTrue(result.failures().get("bad.yml").contains("duration"));
    }

    @Test void anUnknownScopeIsRejected(@TempDir Path directory) throws Exception {
        write(directory, "bad.yml",
                "rules:\n"
                        + "  - id: broken\n"
                        + "    trigger: chat\n"
                        + "    cooldown:\n"
                        + "      duration: 10s\n"
                        + "      scope: whenever\n"
                        + "    actions:\n"
                        + "      - type: broadcast\n"
                        + "        message: \"x\"\n");
        YamlRuleLoader.LoadResult result = loader().load(directory);

        assertFalse(result.isValid());
        assertTrue(result.failures().get("bad.yml").contains("scope"));
    }

    @Test void aRuleWithoutActionsIsRejected(@TempDir Path directory) throws Exception {
        write(directory, "bad.yml",
                "rules:\n"
                        + "  - id: broken\n"
                        + "    trigger: chat\n"
                        + "    actions: []\n");
        YamlRuleLoader.LoadResult result = loader().load(directory);

        assertFalse(result.isValid());
        assertTrue(result.failures().get("bad.yml").contains("actions"));
    }

    @Test void anAggregateIsOnlyAllowedOnLikes(@TempDir Path directory) throws Exception {
        write(directory, "bad.yml",
                "rules:\n"
                        + "  - id: broken\n"
                        + "    trigger: chat\n"
                        + "    aggregate:\n"
                        + "      metric: count\n"
                        + "      scope: global\n"
                        + "      threshold: 50\n"
                        + "    actions:\n"
                        + "      - type: broadcast\n"
                        + "        message: \"x\"\n");
        YamlRuleLoader.LoadResult result = loader().load(directory);

        assertFalse(result.isValid());
        assertTrue(result.failures().get("bad.yml").contains("like"));
    }

    @Test void aBrokenFileDoesNotStopTheOtherFiles(@TempDir Path directory) throws Exception {
        write(directory, "good.yml",
                "rules:\n"
                        + "  - id: good\n"
                        + "    trigger: chat\n"
                        + "    actions:\n"
                        + "      - type: broadcast\n"
                        + "        message: \"x\"\n");
        write(directory, "bad.yml", "rules: [ this is not a mapping\n");
        YamlRuleLoader.LoadResult result = loader().load(directory);

        assertFalse(result.isValid());
        assertTrue(result.failures().containsKey("bad.yml"));
        assertEquals(1, result.snapshot().size());
        assertEquals("good", result.snapshot().rules().get(0).id());
    }

    @Test void aFileWithoutRulesIsAccepted(@TempDir Path directory) throws Exception {
        write(directory, "empty.yml", "rules: []\n");
        YamlRuleLoader.LoadResult result = loader().load(directory);

        assertTrue(result.isValid(), String.valueOf(result.failures()));
        assertEquals(0, result.snapshot().size());
    }

    @Test void anAggregateIsParsed(@TempDir Path directory) throws Exception {
        write(directory, "likes.yml",
                "rules:\n"
                        + "  - id: fifty\n"
                        + "    trigger: like\n"
                        + "    aggregate:\n"
                        + "      metric: count\n"
                        + "      scope: global\n"
                        + "      threshold: 50\n"
                        + "      carry-remainder: true\n"
                        + "    actions:\n"
                        + "      - type: broadcast\n"
                        + "        message: \"fifty\"\n");
        YamlRuleLoader.LoadResult result = loader().load(directory);

        assertTrue(result.isValid(), String.valueOf(result.failures()));
        Aggregate aggregate = result.snapshot().rules().get(0).aggregate();
        assertEquals(Aggregate.Metric.COUNT, aggregate.metric());
        assertEquals(Aggregate.Scope.GLOBAL, aggregate.scope());
        assertEquals(50L, aggregate.threshold());
        assertTrue(aggregate.carryRemainder());
    }
}
