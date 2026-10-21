package dev.nglmercer.tiktokminecraft.functions;

import dev.nglmercer.tiktokminecraft.functions.steps.CommandStep;
import dev.nglmercer.tiktokminecraft.security.CommandPolicy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionParsingTest {
    @Test void plainMcFunctionParsesCommandsInOrderAndIgnoresComments() throws Exception {
        String text = "# utilities:welcome\n# a comment\n\nsay Welcome!\n  \ntitle @a title {\"text\":\"Hi\"}\n";
        FunctionDefinition definition = McFunctionParser.parse("fallback:id", text, "test");
        assertEquals("utilities:welcome", definition.getId());
        assertEquals(2, definition.getSteps().size());
        assertEquals("say Welcome!", ((CommandStep) definition.getSteps().get(0)).getValue());
        assertEquals(FunctionDefinition.Format.MCFUNCTION, definition.getFormat());
        assertTrue(definition.getParameters().isEmpty());
    }

    @Test void mcFunctionRequiresAtLeastOneCommand() {
        assertThrows(FunctionException.class, () ->
                McFunctionParser.parse("fallback:id", "# only a comment\n", "test"));
    }

    @Test void mcFunctionParamHeadersSupportDefaultsAndRequired() throws Exception {
        String text = "# ns:fn\n# @param mob entity minecraft:zombie\n"
                + "# @param title string \"Hello!\"\n"
                + "# @param target selector\n"
                + "summon ${mob} ~ ~1 ~\n";
        FunctionDefinition definition = McFunctionParser.parse("fallback:id", text, "test");
        assertEquals(3, definition.getParameters().size());
        assertTrue(!definition.getParameters().get("mob").isRequired());
        assertTrue(definition.getParameters().get("target").isRequired());
        assertEquals("minecraft:zombie",
                definition.getParameters().get("mob").getDefaultValue());
        assertEquals("Hello!", definition.getParameters().get("title").getDefaultValue());
    }

    @Test void mcFunctionDuplicateParamIsRejected() {
        String text = "# @param a string\n# @param a string\nsay hi\n";
        assertThrows(FunctionException.class, () ->
                McFunctionParser.parse("ns:fn", text, "test"));
    }

    @Test void mcFunctionUnknownParamTypeIsRejected() {
        String text = "# @param a frobnicate\nsay hi\n";
        assertThrows(FunctionException.class, () ->
                McFunctionParser.parse("ns:fn", text, "test"));
    }

    @Test void jsonFunctionParsesTypedParametersAndSteps() throws Exception {
        FunctionDefinition definition = JsonFunctionParser.parse("events:mob_attack",
                readExample("mob_attack.json"), "test");
        assertEquals("events:mob_attack", definition.getId());
        assertEquals("entity", definition.getParameters().get("mob").getType());
        assertEquals(Long.valueOf(1), definition.getParameters().get("count").getDefaultValue());
        assertEquals(3, definition.getSteps().size());
    }

    @Test void jsonUnknownStepTypeIsRejected() {
        assertThrows(FunctionException.class, () -> JsonFunctionParser.parse("ns:fn",
                "{\"schemaVersion\":1,\"name\":\"ns:fn\",\"steps\":[{\"type\":\"explode\"}]}",
                "test"));
    }

    @Test void jsonUnsupportedSchemaIsRejected() {
        assertThrows(FunctionException.class, () -> JsonFunctionParser.parse("ns:fn",
                "{\"schemaVersion\":99,\"name\":\"ns:fn\",\"steps\":[]}", "test"));
    }

    @Test void jsonMissingStepsIsRejected() {
        assertThrows(FunctionException.class, () -> JsonFunctionParser.parse("ns:fn",
                "{\"schemaVersion\":1,\"name\":\"ns:fn\"}", "test"));
    }

    @Test void dispatcherSniffsContent() throws Exception {
        FunctionDefinition fromJson = FunctionParser.parse("ns:fn", "auto.json",
                "{\"schemaVersion\":1,\"name\":\"ns:fn\",\"steps\":[{\"type\":\"command\","
                        + "\"value\":\"say hi\"}]}", "test");
        assertEquals(FunctionDefinition.Format.JSON, fromJson.getFormat());
        FunctionDefinition fromMc = FunctionParser.parse("ns:fn", null, "say hi\n", "test");
        assertEquals(FunctionDefinition.Format.MCFUNCTION, fromMc.getFormat());
    }

    @Test void missingAndDefaultParameters() throws Exception {
        FunctionRegistry registry = new FunctionRegistry();
        FunctionDefinition definition = JsonFunctionParser.parse("ns:fn",
                "{\"schemaVersion\":1,\"name\":\"ns:fn\",\"parameters\":{"
                        + "\"req\":{\"type\":\"string\"},"
                        + "\"opt\":{\"type\":\"integer\",\"default\":3}},"
                        + "\"steps\":[{\"type\":\"command\",\"value\":\"say ${opt}\"}]}", "test");
        registry.register(definition);
        FunctionCompiler compiler = new FunctionCompiler(registry, FunctionLimits.defaults(),
                new CommandPolicy(Arrays.asList("*")), "1.20.1");
        // Missing required parameter is rejected, never an empty string.
        assertThrows(FunctionException.class, () ->
                compiler.compile("ns:fn", Collections.<String, Object>emptyMap()));
        // Default applies.
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("req", "hi");
        FunctionExecutionPlan plan = compiler.compile("ns:fn", args);
        assertEquals(Collections.singletonList("/say 3"), plan.commands());
    }

    @Test void unknownParameterIsRejected() throws Exception {
        FunctionRegistry registry = new FunctionRegistry();
        registry.register(JsonFunctionParser.parse("ns:fn",
                "{\"schemaVersion\":1,\"name\":\"ns:fn\","
                        + "\"steps\":[{\"type\":\"command\",\"value\":\"say hi\"}]}", "test"));
        FunctionCompiler compiler = new FunctionCompiler(registry, FunctionLimits.defaults(),
                new CommandPolicy(Arrays.asList("*")), "1.20.1");
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("nope", "x");
        assertThrows(FunctionException.class, () -> compiler.compile("ns:fn", args));
    }

    @Test void duplicateRegistrationIsRejected() throws Exception {
        FunctionRegistry registry = new FunctionRegistry();
        FunctionDefinition definition = JsonFunctionParser.parse("ns:fn",
                "{\"schemaVersion\":1,\"name\":\"ns:fn\","
                        + "\"steps\":[{\"type\":\"command\",\"value\":\"say hi\"}]}", "test");
        registry.register(definition);
        assertThrows(FunctionException.class, () -> registry.register(definition));
    }

    /**
     * Reads a test fixture.
     *
     * <p>The fixtures live in the module's own test resources, so this module
     * does not depend on the examples of the plugin.</p>
     */
    static String readExample(String name) throws Exception {
        java.io.InputStream stream =
                FunctionParsingTest.class.getResourceAsStream("/functions/" + name);
        if (stream == null) {
            throw new java.io.FileNotFoundException("/functions/" + name);
        }
        try (java.io.InputStream input = stream) {
            return new String(input.readAllBytes(), "UTF-8");
        }
    }
}
