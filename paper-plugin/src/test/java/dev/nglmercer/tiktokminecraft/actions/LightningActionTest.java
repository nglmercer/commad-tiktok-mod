package dev.nglmercer.tiktokminecraft.actions;

import dev.nglmercer.tiktokminecraft.rules.ActionSpec;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LightningActionTest {
    @Test void keepsLegacyAndDamagingActionsDiscoverable() {
        ActionRegistry registry = new ActionRegistry();
        assertTrue(registry.contains("lightning-effect"));
        assertTrue(registry.contains("lightning"));
    }

    @Test void validatesDurationIntervalAndBooleanOptions() {
        var action = new LightningAction("lightning", true);
        assertDoesNotThrow(() -> action.validate(new ActionSpec("lightning",
                Map.of("damage", true, "duration-seconds", 3600, "interval-ticks", 10)), null));
        for (var invalid : java.util.List.<Map<String, Object>>of(Map.of("duration-seconds", -1),
                Map.of("duration-seconds", 3601), Map.of("interval-ticks", 0),
                Map.of("interval-ticks", 1201), Map.of("damage", "true"),
                Map.of("multiply-by-repeat", "true"), Map.of("count", 33))) {
            assertThrows(ActionException.class, () -> action.validate(
                    new ActionSpec("lightning", invalid), null));
        }
    }

    @Test void continuesOnSameCadenceAfterDeadOrOfflineBurstsAreSkipped() {
        var timeline = new LightningAction.Timeline(200, 20);
        int aliveBursts = 0;
        for (int tick = 1; tick <= 200; tick++) {
            boolean due = timeline.tick();
            // Dead/offline for ticks 21..100. Runtime skips those strikes,
            // but does not discard the timeline or start a new one on respawn.
            boolean alive = tick <= 20 || tick > 100;
            if (due && alive) aliveBursts++;
            if (tick == 100) assertFalse(timeline.expired());
        }
        assertEquals(5, aliveBursts); // 20, 120, 140, 160, 180
        assertTrue(timeline.expired());
        assertFalse(timeline.tick());
    }

    @Test void longStormExpiresWithoutExtraBurstAtDeadline() {
        var timeline = new LightningAction.Timeline(3600 * 20, 40);
        int bursts = 0;
        for (int tick = 1; tick <= 3600 * 20; tick++) if (timeline.tick()) bursts++;
        assertEquals(1799, bursts); // initial burst is executed separately
        assertTrue(timeline.expired());
    }
}
