package dev.nglmercer.tiktokminecraft.actions;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A sliding one-minute window limiter.
 *
 * <p>It bounds how many entities the plugin may spawn in a minute, no matter
 * how many events arrive.</p>
 */
public final class EntityRateLimiter {

    private final int limit;
    private final AtomicInteger used = new AtomicInteger();
    private volatile long windowStart = System.currentTimeMillis();

    public EntityRateLimiter(int limit) {
        this.limit = Math.max(0, limit);
    }

    /**
     * Takes one slot.
     *
     * @return true when the caller may spawn an entity
     */
    public boolean tryAcquire() {
        if (limit == 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        synchronized (this) {
            if (now - windowStart >= 60_000L) {
                windowStart = now;
                used.set(0);
            }
        }
        while (true) {
            int current = used.get();
            if (current >= limit) {
                return false;
            }
            if (used.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    /** The number of slots taken in the current window. */
    public int used() {
        return used.get();
    }

    public int limit() {
        return limit;
    }

    /** Forgets the current window. */
    public void reset() {
        synchronized (this) {
            windowStart = System.currentTimeMillis();
            used.set(0);
        }
    }
}
