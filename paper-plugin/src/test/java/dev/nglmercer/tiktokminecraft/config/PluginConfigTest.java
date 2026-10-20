package dev.nglmercer.tiktokminecraft.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PluginConfigTest {

    private static YamlConfiguration config(String... lines) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(String.join("\n", lines));
        } catch (InvalidConfigurationException failure) {
            throw new IllegalArgumentException(failure);
        }
        return yaml;
    }

    @Test void defaultsAreSafe() {
        PluginConfig.Result result =
                PluginConfig.load(config("tiktok:", "  unique-id: \"creator\""));
        assertTrue(result.isValid(), String.valueOf(result.problems()));
        PluginConfig parsed = result.config();

        // Live actions must be off on a fresh install.
        assertFalse(parsed.executeLiveActions());
        assertFalse(parsed.allowConsoleCommands());
        assertFalse(parsed.allowRawCommands());
        assertEquals(5, parsed.chatMessagesPerSecond());
        assertEquals(200, parsed.chatMaxLength());
        assertEquals(PluginConfig.TargetPolicy.NEAREST_ONLINE, parsed.targetPolicy());
        assertEquals(PluginConfig.NoPlayerPolicy.SKIP, parsed.noPlayerPolicy());
        assertEquals(2, parsed.maxConcurrentFunctions());
        assertEquals(4, parsed.maxCommandsPerTick());
    }

    @Test void leadingAtIsStrippedFromTheCreator() {
        PluginConfig.Result result =
                PluginConfig.load(config("tiktok:", "  unique-id: \"@elpadredomingooficial\""));
        assertTrue(result.isValid(), String.valueOf(result.problems()));
        assertEquals("elpadredomingooficial", result.config().uniqueId());
    }

    @Test void emptyCreatorIsRejected() {
        PluginConfig.Result result = PluginConfig.load(config("tiktok:", "  unique-id: \"\""));
        assertFalse(result.isValid());
        assertTrue(result.problems().get(0).contains("unique-id"));
    }

    @Test void invalidCreatorIsRejected() {
        PluginConfig.Result result =
                PluginConfig.load(config("tiktok:", "  unique-id: \"not a username!\""));
        assertFalse(result.isValid());
        assertTrue(result.problems().get(0).contains("unique-id"));
    }

    @Test void numericValuesAreClampedToSafeRanges() {
        PluginConfig.Result result =
                PluginConfig.load(
                        config(
                                "tiktok:",
                                "  unique-id: \"creator\"",
                                "chat:",
                                "  max-length: 100000",
                                "  messages-per-second: 0",
                                "functions:",
                                "  max-commands-per-tick: 9999",
                                "  max-execution-seconds: 99999"));
        assertTrue(result.isValid(), String.valueOf(result.problems()));
        PluginConfig parsed = result.config();
        assertEquals(1000, parsed.chatMaxLength());
        assertEquals(1, parsed.chatMessagesPerSecond());
        assertEquals(256, parsed.maxCommandsPerTick());
        assertEquals(3600, parsed.maxExecutionSeconds());
    }

    @Test void allowedCommandsAreNormalised() {
        PluginConfig.Result result =
                PluginConfig.load(
                        config(
                                "tiktok:",
                                "  unique-id: \"creator\"",
                                "functions:",
                                "  allowed-commands:",
                                "    - Summon",
                                "    - Title",
                                "    - \"\""));
        assertTrue(result.isValid(), String.valueOf(result.problems()));
        assertEquals(Arrays.asList("summon", "title"), result.config().allowedCommands());
    }

    @Test void enabledFunctionsNeedAnAllowedCommand() {
        PluginConfig.Result result =
                PluginConfig.load(
                        config(
                                "tiktok:",
                                "  unique-id: \"creator\"",
                                "functions:",
                                "  enabled: true",
                                "  allowed-commands: []"));
        assertFalse(result.isValid());
        assertTrue(result.problems().get(0).contains("allowed-commands"));
    }
}
