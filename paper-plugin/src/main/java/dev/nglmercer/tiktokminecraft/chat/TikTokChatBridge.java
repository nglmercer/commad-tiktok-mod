package dev.nglmercer.tiktokminecraft.chat;

import dev.nglmercer.tiktokminecraft.config.PluginConfig;
import dev.nglmercer.tiktokminecraft.event.TikTokEventContext;
import dev.nglmercer.tiktokminecraft.log.LogCategory;
import dev.nglmercer.tiktokminecraft.log.PluginLog;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Mirrors TikTok chat into the server console and into Minecraft chat.
 *
 * <p>The bridge is fed from the SDK's event thread. It only formats and
 * enqueues there; the actual broadcast happens on the server thread when the
 * plugin drains the queue.</p>
 *
 * <p>Three independent bounds keep a busy LIVE from flooding the server:
 * an ingest rate limit, a bounded broadcast queue and a per-tick drain
 * budget. Everything that is dropped is counted and reported by
 * {@code /tiktok status}.</p>
 */
public final class TikTokChatBridge {

    /** The maximum number of messages broadcast from the queue per tick. */
    private static final int MAX_BROADCASTS_PER_TICK = 8;

    private final PluginLog log;
    private final Deque<Component> pending = new ArrayDeque<Component>();
    private final AtomicLong received = new AtomicLong();
    private final AtomicLong suppressed = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();

    private volatile PluginConfig config;
    private volatile boolean closed;
    private long windowStart = System.nanoTime();
    private int windowCount;

    public TikTokChatBridge(PluginLog log, PluginConfig config) {
        this.log = log;
        this.config = config;
    }

    public void applyConfig(PluginConfig config) {
        this.config = config;
    }

    /**
     * Accepts one chat event.
     *
     * <p>Called from the SDK's event thread. Never blocks and never touches
     * the Bukkit API.</p>
     */
    public void accept(TikTokEventContext event) {
        if (closed || event == null) {
            return;
        }
        PluginConfig current = config;
        if (!current.chatEnabled()) {
            return;
        }
        if (!TikTokEventContext.CHAT.equals(event.type())) {
            // Only text chat is mirrored. An emote-only message has no body,
            // and letting it through would consume the rate limit budget that
            // real messages need.
            return;
        }
        received.incrementAndGet();
        String nickname = event.nickname();
        String uniqueId = event.uniqueId();
        String text = ChatFormatter.truncate(
                ChatFormatter.safe(event.chatText()), current.chatMaxLength());
        if (text.isEmpty()) {
            // A message without a body has nothing to publish.
            return;
        }
        if (!allow(current)) {
            suppressed.incrementAndGet();
            return;
        }
        if (current.chatConsole()) {
            log.info(LogCategory.CHAT,
                    ChatFormatter.consoleLine(nickname, uniqueId, text));
        }
        if (!current.chatMinecraft()) {
            return;
        }
        Component component =
                ChatFormatter.chatComponent(
                        current.chatPrefix(), nickname, uniqueId, text);
        synchronized (pending) {
            if (pending.size() >= current.chatMaxQueue()) {
                dropped.incrementAndGet();
                return;
            }
            pending.addLast(component);
        }
    }

    /**
     * Broadcasts queued messages. Must run on the server thread.
     *
     * @return the number of messages broadcast
     */
    public int drain() {
        if (closed) {
            return 0;
        }
        int broadcast = 0;
        while (broadcast < MAX_BROADCASTS_PER_TICK) {
            Component component;
            synchronized (pending) {
                component = pending.pollFirst();
            }
            if (component == null) {
                break;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(component);
            }
            broadcast++;
        }
        return broadcast;
    }

    /** Logs how many messages were suppressed during the last window. */
    public void reportSuppressed() {
        long count = suppressed.getAndSet(0);
        if (count > 0) {
            log.warn(LogCategory.CHAT,
                    "Suppressed " + count + " chat message(s) by the ingest rate limit");
        }
    }

    public long received() {
        return received.get();
    }

    public long suppressed() {
        return suppressed.get();
    }

    public long dropped() {
        return dropped.get();
    }

    public int queued() {
        synchronized (pending) {
            return pending.size();
        }
    }

    /** Stops accepting and discards anything still queued. */
    public void close() {
        closed = true;
        synchronized (pending) {
            pending.clear();
        }
    }

    /**
     * A one-second sliding window limiter.
     *
     * @return true when the message may be delivered
     */
    private boolean allow(PluginConfig current) {
        int limit = current.chatMessagesPerSecond();
        long now = System.nanoTime();
        synchronized (this) {
            long elapsed = now - windowStart;
            if (elapsed >= 1_000_000_000L) {
                windowStart = now;
                windowCount = 0;
            }
            if (windowCount >= limit) {
                return false;
            }
            windowCount++;
            return true;
        }
    }
}
