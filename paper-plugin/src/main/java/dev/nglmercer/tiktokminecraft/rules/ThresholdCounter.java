package dev.nglmercer.tiktokminecraft.rules;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Counts event deltas and reports when a threshold is crossed.
 *
 * <p>Only the delta carried by an event is counted. A cumulative room total
 * is never used as the counter, so the same likes cannot be counted twice
 * when a threshold is crossed again.</p>
 */
public final class ThresholdCounter {

    private final Map<String, Long> counters = new LinkedHashMap<String, Long>();
    private final int maxKeys;

    public ThresholdCounter(int maxKeys) {
        this.maxKeys = Math.max(16, maxKeys);
    }

    /**
     * Adds a delta to a counter.
     *
     * @param carryRemainder when true, the remainder below the threshold is
     *     kept for the next event
     * @return how many times the threshold was crossed, zero or more
     */
    public long add(String key, long delta, long threshold, boolean carryRemainder) {
        if (key == null || threshold <= 0 || delta <= 0) {
            return 0;
        }
        synchronized (counters) {
            Long current = counters.get(key);
            long value = current == null ? 0 : current.longValue();
            long total = value + delta;
            long crossed = total / threshold;
            // The counter keeps accumulating until the threshold is reached.
            // Only a crossing may reset it, and the remainder above the
            // threshold is only kept when the rule asks for it.
            long remainder = crossed > 0
                    ? (carryRemainder ? total % threshold : 0)
                    : total;
            counters.put(key, Long.valueOf(remainder));
            if (counters.size() > maxKeys) {
                Iterator<String> keys = counters.keySet().iterator();
                if (keys.hasNext()) {
                    keys.next();
                    keys.remove();
                }
            }
            return crossed;
        }
    }

    /** @return the current counter value */
    public long value(String key) {
        synchronized (counters) {
            Long value = counters.get(key);
            return value == null ? 0 : value.longValue();
        }
    }

    /** Forgets every counter. */
    public void clear() {
        synchronized (counters) {
            counters.clear();
        }
    }

    public int size() {
        synchronized (counters) {
            return counters.size();
        }
    }
}
