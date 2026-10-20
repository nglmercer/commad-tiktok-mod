package dev.nglmercer.tiktokminecraft.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventBusTest {

    private static TikTokEventContext event(String id, String type, boolean history) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("chat.text", "hello " + id);
        return new TikTokEventContext(id, "room-1", type, "1", "u", "Nick", 1L, history, payload);
    }

    @Test void deduplicationRejectsARepeatedIdentifier() {
        EventDeduplicator deduplicator = new EventDeduplicator(16, 300);
        assertTrue(deduplicator.admit("room-1|chat|1"));
        assertFalse(deduplicator.admit("room-1|chat|1"));
        assertTrue(deduplicator.admit("room-1|chat|2"));
    }

    @Test void deduplicationCacheIsBounded() {
        EventDeduplicator deduplicator = new EventDeduplicator(8, 300);
        for (int index = 0; index < 100; index++) {
            deduplicator.admit("key-" + index);
        }
        assertTrue(deduplicator.size() <= 8, "size was " + deduplicator.size());
    }

    @Test void expiredIdentifiersAreAdmittedAgain() throws Exception {
        EventDeduplicator deduplicator = new EventDeduplicator(16, 1);
        assertTrue(deduplicator.admit("key"));
        assertFalse(deduplicator.admit("key"));
        Thread.sleep(1100L);
        assertTrue(deduplicator.admit("key"));
    }

    @Test void giftStreakCompletionUsesItsOwnKey() {
        TikTokEventContext gift = event("1", TikTokEventContext.GIFT, false);
        TikTokEventContext giftFinal = event("1", TikTokEventContext.GIFT_FINAL, false);
        // A streak completion and the gift update that preceded it are
        // different events even when they share a message id.
        assertFalse(
                EventBus.deduplicationKey(gift).equals(EventBus.deduplicationKey(giftFinal)));
    }

    @Test void duplicateEventsAreDropped() {
        EventBus bus = new EventBus(16, 300, true);
        bus.offer(event("msg-1", TikTokEventContext.CHAT, false));
        bus.offer(event("msg-1", TikTokEventContext.CHAT, false));
        assertEquals(2L, bus.received());
        assertEquals(1L, bus.duplicates());
        assertEquals(1, bus.queued());
    }

    @Test void historyEventsAreDroppedWhenConfigured() {
        EventBus bus = new EventBus(16, 300, true);
        bus.offer(event("msg-1", TikTokEventContext.CHAT, true));
        assertEquals(1L, bus.received());
        assertEquals(1L, bus.dropped());
        assertEquals(0, bus.queued());
    }

    @Test void theQueueIsBoundedAndOverflowIsCounted() {
        EventBus bus = new EventBus(3, 300, false);
        for (int index = 0; index < 10; index++) {
            bus.offer(event("msg-" + index, TikTokEventContext.CHAT, false));
        }
        assertEquals(10L, bus.received());
        assertEquals(7L, bus.dropped());
        assertEquals(3, bus.queued());
    }

    @Test void drainHandsEventsToTheConsumerInOrder() {
        EventBus bus = new EventBus(16, 300, false);
        bus.offer(event("a", TikTokEventContext.CHAT, false));
        bus.offer(event("b", TikTokEventContext.CHAT, false));
        List<String> seen = new ArrayList<String>();
        assertEquals(2, bus.drain(context -> seen.add(context.eventId())));
        assertEquals(java.util.Arrays.asList("a", "b"), seen);
        assertEquals(2L, bus.processed());
        assertEquals(0, bus.queued());
    }

    @Test void aClosedBusAcceptsNothing() {
        EventBus bus = new EventBus(16, 300, false);
        bus.close();
        bus.offer(event("a", TikTokEventContext.CHAT, false));
        assertEquals(0L, bus.received());
        assertEquals(0, bus.queued());
    }
}
