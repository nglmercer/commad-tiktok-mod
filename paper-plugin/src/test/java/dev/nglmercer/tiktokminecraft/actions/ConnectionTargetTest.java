package dev.nglmercer.tiktokminecraft.actions;

import static org.junit.jupiter.api.Assertions.*;
import dev.nglmercer.tiktokminecraft.event.TikTokEventContext;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConnectionTargetTest {
    private ActionContext context(String player) {
        var event = new TikTokEventContext("event", "room", "chat", "viewer", "viewer",
                "Viewer", 0L, false, Map.of("connection.targetPlayer", player));
        return new ActionContext(null, null, null, null, event, "rule", null);
    }

    @Test void eachStreamersEventsUseTheirAssignedPlayer() {
        assertEquals("specific-player:Alice", context("Alice").targetSpec(null));
        assertEquals("specific-player:Bob", context("Bob").targetSpec(null));
        assertEquals("specific-player:Alice", context("Alice").targetSpec("nearest-online"));
    }

    @Test void explicitTargetsRemainAvailableForSharedEffects() {
        assertEquals("all-online", context("Alice").targetSpec("all-online"));
        assertEquals("specific-player:Bob", context("Alice").targetSpec("specific-player:Bob"));
    }

    @Test void clearingAnAssignmentRestoresRuleDefaults() {
        assertNull(context("").targetSpec(null));
        assertEquals("nearest-online", context("").targetSpec("nearest-online"));
    }
}
