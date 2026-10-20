package dev.nglmercer.tiktokminecraft.event;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A bounded, time-bounded cache of recently seen event identifiers.
 *
 * <p>TikTok can replay messages after a reconnect, and a gift streak can
 * deliver the same completion more than once. Without deduplication a single
 * message could trigger several world actions.</p>
 *
 * <p>The cache is bounded in two dimensions: at most {@code maxEntries}
 * identifiers are retained and entries older than {@code windowSeconds} are
 * expired. Both limits are needed so a long session cannot grow the cache
 * without bound.</p>
 */
public final class EventDeduplicator {

    private final int maxEntries;
    private final long windowMillis;
    private final Map<String, Long> seen = new LinkedHashMap<String, Long>();

    public EventDeduplicator(int maxEntries, int windowSeconds) {
        this.maxEntries = Math.max(1, maxEntries);
        this.windowMillis = Math.max(1, windowSeconds) * 1000L;
    }

    /**
     * Records an identifier.
     *
     * @return true when the identifier had not been seen inside the window
     */
    public boolean admit(String key) {
        if (key == null || key.isEmpty()) {
            return true;
        }
        long now = System.currentTimeMillis();
        synchronized (seen) {
            Long previous = seen.get(key);
            if (previous != null && now - previous < windowMillis) {
                return false;
            }
            seen.put(key, Long.valueOf(now));
            evict(now);
            return true;
        }
    }

    /** Forgets everything. */
    public void clear() {
        synchronized (seen) {
            seen.clear();
        }
    }

    public int size() {
        synchronized (seen) {
            return seen.size();
        }
    }

    /** Drops expired entries and, if needed, the oldest ones. */
    private void evict(long now) {
        Iterator<Map.Entry<String, Long>> iterator = seen.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now - entry.getValue().longValue() >= windowMillis) {
                iterator.remove();
            }
        }
        while (seen.size() > maxEntries) {
            Iterator<String> keys = seen.keySet().iterator();
            if (!keys.hasNext()) {
                break;
            }
            keys.next();
            keys.remove();
        }
    }
}
