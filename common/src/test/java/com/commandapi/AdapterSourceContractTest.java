package com.commandapi;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards source-only mixin wiring that shared-code unit tests cannot execute. */
class AdapterSourceContractTest {

    private static String source(String relativePath) throws Exception {
        return new String(Files.readAllBytes(Paths.get("..", "adapters", relativePath)), StandardCharsets.UTF_8);
    }

    @Test
    void networkCommandFeedbackTargetsLocalPlayer() throws Exception {
        String mixin = source("network-chat/src/main/java/com/commandapi/version/mixin/CommandApiSendCommandMixin.java");
        assertTrue(mixin.contains("dispatchCommand(Minecraft.getInstance().player, command)"));
        assertFalse(mixin.contains("dispatchCommand(this, command)"));
    }

    @Test
    void everyAdapterDescribesCommandsAsSubmitted() throws Exception {
        for (String family : new String[]{"legacy-chat", "signed-chat", "network-chat"}) {
            String bridge = source(family + "/src/main/java/com/commandapi/version/MinecraftBridgeImpl.java");
            assertTrue(bridge.contains("Command submitted"), family);
            assertFalse(bridge.contains("Command executed"), family);
        }
    }
}
