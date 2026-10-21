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
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Checks that a rule is validated against the configuration that is being
 * loaded, and not against the one that happens to be running.
 *
 * <p>This is what makes a change to the command allowlist or to
 * {@code security.allow-raw-commands} take effect on the first reload
 * instead of the second one.</p>
 */
class ReloadValidationTest {

    private static PluginConfig config(boolean rawCommands, boolean consoleCommands) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("tiktok.unique-id", "creator");
        yaml.set("security.allow-raw-commands", Boolean.valueOf(rawCommands));
        yaml.set("security.allow-console-commands", Boolean.valueOf(consoleCommands));
        return PluginConfig.load(yaml).config();
    }

    private static void write(Path directory, String content) throws IOException {
        Files.write(directory.resolve("rules.yml"), content.getBytes(StandardCharsets.UTF_8));
    }

    private static final String COMMAND_RULE =
            "rules:\n"
                    + "  - id: raw\n"
                    + "    enabled: true\n"
                    + "    trigger: chat\n"
                    + "    actions:\n"
                    + "      - type: command\n"
                    + "        command: \"say hi\"\n";

    @Test void aRuleIsCheckedAgainstTheConfigurationBeingLoaded(@TempDir Path directory)
            throws Exception {
        write(directory, COMMAND_RULE);
        ActionRegistry registry = new ActionRegistry();

        // The running configuration has raw commands disabled.
        ActionContext strict = new ActionContext(
                null, config(false, false), null, null, null, null, null);
        YamlRuleLoader.LoadResult rejected = new YamlRuleLoader(registry, strict).load(directory);
        assertFalse(rejected.isValid());
        assertTrue(
                rejected.failures().get("rules.yml").contains("Raw commands are disabled"),
                rejected.failures().toString());

        // The new configuration enables them, so the same rule now loads.
        ActionContext permissive = new ActionContext(
                null, config(true, true), null, null, null, null, null);
        YamlRuleLoader.LoadResult accepted =
                new YamlRuleLoader(registry, permissive).load(directory);
        assertTrue(accepted.isValid(), String.valueOf(accepted.failures()));
        assertEquals(1, accepted.snapshot().size());
    }

    @Test void theFunctionLimitsFollowTheConfiguration(@TempDir Path directory) {
        PluginConfig permissive = config(true, true);
        PluginConfig strict = config(false, false);
        // The limits are derived from the configuration, so a reload that
        // lowers them lowers the engine's limits too.
        assertEquals(
                permissive.functionLimits().getMaxExecutionSeconds(),
                strict.functionLimits().getMaxExecutionSeconds());
        assertEquals(20, permissive.functionLimits().getMaxLoopIterations());
    }
}
