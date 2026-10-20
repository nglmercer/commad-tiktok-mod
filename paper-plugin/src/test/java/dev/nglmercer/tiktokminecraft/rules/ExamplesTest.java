package dev.nglmercer.tiktokminecraft.rules;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nglmercer.tiktokminecraft.actions.ActionContext;
import dev.nglmercer.tiktokminecraft.actions.ActionRegistry;
import dev.nglmercer.tiktokminecraft.config.PluginConfig;
import dev.nglmercer.tiktokminecraft.functions.FunctionLimits;
import dev.nglmercer.tiktokminecraft.functions.FunctionParser;
import dev.nglmercer.tiktokminecraft.functions.FunctionRegistry;
import dev.nglmercer.tiktokminecraft.functions.FunctionValidator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * Checks that the shipped examples are valid.
 *
 * <p>An example that does not load is a broken promise in the
 * documentation, so the examples are loaded with the same code the plugin
 * uses.</p>
 */
class ExamplesTest {

    private static Path examples() {
        Path path = Paths.get("..", "examples");
        if (!Files.isDirectory(path)) {
            path = Paths.get("examples");
        }
        return path;
    }

    private static ActionContext validationContext() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("tiktok.unique-id", "creator");
        PluginConfig config = PluginConfig.load(yaml).config();
        return new ActionContext(null, config, null, null, null, null, null);
    }

    @Test void everyExampleRuleFileLoads() throws IOException {
        Path directory = examples();
        assertTrue(Files.isDirectory(directory), "the examples directory is missing");
        YamlRuleLoader loader = new YamlRuleLoader(new ActionRegistry(), validationContext());
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".yml")).toList()) {
                YamlRuleLoader.LoadResult result =
                        loader.load(file.getParent());
                assertTrue(
                        result.isValid(),
                        file.getFileName() + ": " + result.failures());
                assertTrue(
                        result.snapshot().size() > 0,
                        file.getFileName() + " contains no rules");
            }
        }
    }

    @Test void everyExampleFunctionLoads() throws Exception {
        Path directory = examples().resolve("functions");
        assertTrue(Files.isDirectory(directory), "the example functions are missing");
        FunctionRegistry registry = new FunctionRegistry();
        FunctionLimits limits = FunctionLimits.defaults();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                dev.nglmercer.tiktokminecraft.functions.FunctionDefinition definition =
                        FunctionParser.parse(
                                file.getFileName().toString(), file.getFileName().toString(),
                                text, file.toString());
                FunctionValidator.validate(definition, limits);
                assertTrue(definition.getId().contains(":"), definition.getId());
            }
        }
    }
}
