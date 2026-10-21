package dev.nglmercer.tiktokminecraft.rules;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A bounded store of active cooldowns.
 *
 * <p>Entries expire on their own and the store never grows past its bound,
 * so a rule that fires for every viewer cannot exhaust the heap.</p>
 */
public final class CooldownStore {

    private final Map<String, Long> expiries = new LinkedHashMap<String, Long>();
    private final int maxKeys;

    public CooldownStore(int maxKeys) {
        this.maxKeys = Math.max(16, maxKeys);
    }

    /**
     * Takes a cooldown.
     *
     * @return true when the caller may act; false when the key is still on
     *     cooldown
     */
    public boolean tryAcquire(String key, long durationMillis) {
        if (key == null || durationMillis <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        synchronized (expiries) {
            Long expiry = expiries.get(key);
            if (expiry != null && expiry.longValue() > now) {
                return false;
            }
            expiries.put(key, Long.valueOf(now + durationMillis));
            evict(now);
            return true;
        }
    }

    /** Forgets every cooldown. */
    public void clear() {
        synchronized (expiries) {
            expiries.clear();
        }
    }

    public int size() {
        synchronized (expiries) {
            return expiries.size();
        }
    }

    private void evict(long now) {
        Iterator<Map.Entry<String, Long>> iterator = expiries.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().longValue() <= now) {
                iterator.remove();
            }
        }
        while (expiries.size() > maxKeys) {
            Iterator<String> keys = expiries.keySet().iterator();
            if (!keys.hasNext()) {
                break;
            }
            keys.next();
            keys.remove();
        }
    }
}
