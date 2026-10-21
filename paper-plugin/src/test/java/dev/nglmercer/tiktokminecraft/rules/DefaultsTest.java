package dev.nglmercer.tiktokminecraft.rules;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nglmercer.tiktokminecraft.actions.ActionContext;
import dev.nglmercer.tiktokminecraft.actions.ActionRegistry;
import dev.nglmercer.tiktokminecraft.config.PluginConfig;
import dev.nglmercer.tiktokminecraft.dsl.DslCompiler;
import dev.nglmercer.tiktokminecraft.dsl.DslException;
import dev.nglmercer.tiktokminecraft.functions.FunctionDefinition;
import dev.nglmercer.tiktokminecraft.functions.FunctionParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * Checks that the rules, functions and scripts the plugin ships are
 * consistent with each other.
 *
 * <p>A default rule that calls a function the plugin does not ship would
 * stop every rule from loading on a fresh install, so the references are
 * checked here.</p>
 */
class DefaultsTest {

    private static final String[] DEFAULT_RULE_FILES = {
        "defaults/events/chat.yml",
        "defaults/events/gifts.yml",
        "defaults/events/likes.yml",
        "defaults/events/social.yml",
        "defaults/events/members.yml",
        "defaults/events/interactions.yml"
    };

    private static final String[] DEFAULT_FUNCTION_FILES = {
        "defaults/functions/effects/welcome.json",
        "defaults/functions/gifts/spawn_zombies.json",
        "defaults/functions/gifts/spawn_chicken.json"
    };

    private static final String DEFAULT_SCRIPT = "defaults/scripts/example.ttl";

    private static String resource(String path) throws IOException {
        try (InputStream stream = DefaultsTest.class.getClassLoader()
                .getResourceAsStream(path)) {
            if (stream == null) {
                throw new java.io.FileNotFoundException(path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ActionContext validationContext() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("tiktok.unique-id", "creator");
        PluginConfig config = PluginConfig.load(yaml).config();
        return new ActionContext(null, config, null, null, null, null, null);
    }

    private static Set<String> shippedFunctions() throws Exception {
        Set<String> ids = new LinkedHashSet<String>();
        for (String path : DEFAULT_FUNCTION_FILES) {
            FunctionDefinition definition = FunctionParser.parse(
                    "tmp", path, resource(path), path);
            ids.add(definition.getId());
        }
        return ids;
    }

    @Test void everyDefaultRuleLoads() throws IOException {
        YamlRuleLoader loader = new YamlRuleLoader(new ActionRegistry(), validationContext());
        for (String path : DEFAULT_RULE_FILES) {
            // The loader reads a directory, so the single file is written to a
            // temporary directory first.
            java.nio.file.Path directory = java.nio.file.Files.createTempDirectory("defaults");
            java.nio.file.Path file = directory.resolve("rules.yml");
            java.nio.file.Files.write(
                    file, resource(path).getBytes(StandardCharsets.UTF_8));
            YamlRuleLoader.LoadResult result = loader.load(directory);
            assertTrue(result.isValid(), path + ": " + result.failures());
            java.nio.file.Files.deleteIfExists(file);
            java.nio.file.Files.deleteIfExists(directory);
        }
    }

    @Test void theDefaultScriptCompiles() throws IOException, DslException {
        List<EventRule> rules = new DslCompiler(
                "example.ttl", new ActionRegistry(), validationContext())
                .compile(resource(DEFAULT_SCRIPT));
        assertTrue(!rules.isEmpty(), "the default script produced no rules");
    }

    @Test void everyFunctionTheDefaultsCallIsShipped() throws Exception {
        Set<String> shipped = shippedFunctions();
        Set<String> referenced = new LinkedHashSet<String>();
        for (String path : DEFAULT_RULE_FILES) {
            referenced.addAll(calledFunctions(resource(path)));
        }
        referenced.addAll(calledFunctions(resource(DEFAULT_SCRIPT)));

        for (String id : referenced) {
            assertTrue(
                    shipped.contains(id),
                    "the defaults call '" + id + "', which is not shipped; shipped: " + shipped);
        }
    }

    /** Extracts the function ids a rule file or a script calls. */
    private static Set<String> calledFunctions(String text) {
        Set<String> called = new LinkedHashSet<String>();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("name:")) {
                called.add(trimmed.substring("name:".length()).trim());
            } else if (trimmed.startsWith("call ")) {
                String rest = trimmed.substring("call ".length()).trim();
                int parenthesis = rest.indexOf('(');
                String id = parenthesis < 0 ? rest : rest.substring(0, parenthesis);
                called.add(id.trim());
            }
        }
        return called;
    }
}
