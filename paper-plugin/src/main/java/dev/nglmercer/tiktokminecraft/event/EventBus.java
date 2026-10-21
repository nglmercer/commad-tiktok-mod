package dev.nglmercer.tiktokminecraft.event;

import dev.nglmercer.tiktokminecraft.config.PluginConfig;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The bounded hand-off between the TikTok event thread and the server thread.
 *
 * <p>Events are offered from the SDK's thread and drained on the server
 * thread. The queue is bounded, so a flood of LIVE events can never grow the
 * heap without limit: overflow is dropped and counted.</p>
 */
public final class EventBus {

    /** The maximum number of events handed to the consumer per tick. */
    private static final int MAX_EVENTS_PER_TICK = 32;

    private final Deque<TikTokEventContext> queue = new ArrayDeque<TikTokEventContext>();
    private final AtomicLong received = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong duplicates = new AtomicLong();
    private final AtomicLong processed = new AtomicLong();

    private volatile int maxPendingEvents;
    private volatile boolean ignoreHistory;
    private volatile EventDeduplicator deduplicator;
    private volatile boolean closed;

    public EventBus(PluginConfig config) {
        applyConfig(config);
    }

    /**
     * Builds a bus from the three values it needs. Used by tests and by any
     * caller that does not hold a full configuration.
     */
    public EventBus(int maxPendingEvents, int deduplicationWindowSeconds, boolean ignoreHistory) {
        this.maxPendingEvents = maxPendingEvents;
        this.ignoreHistory = ignoreHistory;
        this.deduplicator =
                new EventDeduplicator(
                        Math.max(256, deduplicationWindowSeconds * 64), deduplicationWindowSeconds);
    }

    /** Applies a new configuration; the deduplication window is reset. */
    public void applyConfig(PluginConfig config) {
        this.maxPendingEvents = config.maxPendingEvents();
        this.ignoreHistory = config.eventsIgnoreHistory();
        this.deduplicator = newDeduplicator(config);
    }

    private static EventDeduplicator newDeduplicator(PluginConfig config) {
        // The deduplication window bounds how long an identifier is remembered.
        // The entry count is derived from it so the cache stays proportional
        // to the configured window rather than to the traffic.
        int entries = Math.max(256, config.deduplicationWindowSeconds() * 64);
        return new EventDeduplicator(entries, config.deduplicationWindowSeconds());
    }

    /**
     * Offers an event. Called from the SDK's event thread; never blocks.
     */
    public void offer(TikTokEventContext event) {
        if (closed || event == null) {
            return;
        }
        received.incrementAndGet();
        if (ignoreHistory && event.isHistory()) {
            dropped.incrementAndGet();
            return;
        }
        if (!deduplicator.admit(deduplicationKey(event))) {
            duplicates.incrementAndGet();
            return;
        }
        synchronized (queue) {
            if (queue.size() >= maxPendingEvents) {
                dropped.incrementAndGet();
                return;
            }
            queue.addLast(event);
        }
    }

    /**
     * The stable identity of an event for deduplication.
     *
     * <p>A completed gift streak is keyed by its group and gift identifiers
     * because a streak can emit several messages that all carry the same
     * {@code msgId}.</p>
     */
    public static String deduplicationKey(TikTokEventContext event) {
        StringBuilder key = new StringBuilder();
        key.append(event.roomId() == null ? "" : event.roomId());
        key.append('|').append(event.type());
        key.append('|').append(event.eventId());
        if (TikTokEventContext.GIFT_FINAL.equals(event.type())
                || TikTokEventContext.GIFT.equals(event.type())) {
            key.append('|').append(event.giftGroupId());
            key.append('|').append(event.giftId());
            if (TikTokEventContext.GIFT_FINAL.equals(event.type())) {
                key.append("|final");
            }
        }
        return key.toString();
    }

    /**
     * Hands queued events to the consumer. Must run on the server thread.
     *
     * @return the number of events handed over
     */
    public int drain(Consumer<TikTokEventContext> consumer) {
        if (closed) {
            return 0;
        }
        int handled = 0;
        while (handled < MAX_EVENTS_PER_TICK) {
            TikTokEventContext event;
            synchronized (queue) {
                event = queue.pollFirst();
            }
            if (event == null) {
                break;
            }
            processed.incrementAndGet();
            handled++;
            try {
                consumer.accept(event);
            } catch (RuntimeException ignored) {
                // One bad event must never stop the pipeline.
            }
        }
        return handled;
    }

    public long received() {
        return received.get();
    }

    public long dropped() {
        return dropped.get();
    }

    public long duplicates() {
        return duplicates.get();
    }

    public long processed() {
        return processed.get();
    }

    public int queued() {
        synchronized (queue) {
            return queue.size();
        }
    }

    /** Stops accepting events and discards anything still queued. */
    public void close() {
        closed = true;
        synchronized (queue) {
            queue.clear();
        }
    }
}
