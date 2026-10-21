package dev.nglmercer.tiktokminecraft.log;

import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A thin wrapper over the plugin logger that keeps the operational categories
 * separate and can silence whole categories at runtime.
 *
 * <p>Every message is prefixed with the plugin name so it is obvious in a
 * shared server console which plugin produced it.</p>
 */
public final class PluginLog {

    private final Logger logger;
    private final Set<LogCategory> muted = EnumSet.noneOf(LogCategory.class);

    public PluginLog(Logger logger) {
        this.logger = logger;
    }

    public void info(LogCategory category, String message) {
        logger.info(message);
    }

    public void warn(LogCategory category, String message) {
        logger.warning(message);
    }

    public void error(LogCategory category, String message, Throwable error) {
        logger.log(Level.SEVERE, message, error);
    }

    /** Logs only when the category is not muted. */
    public void debug(LogCategory category, String message) {
        synchronized (muted) {
            if (muted.contains(category)) {
                return;
            }
        }
        logger.info("[" + category.wireName() + "] " + message);
    }

    public boolean isMuted(LogCategory category) {
        synchronized (muted) {
            return muted.contains(category);
        }
    }

    public void mute(LogCategory category, boolean value) {
        synchronized (muted) {
            if (value) {
                muted.add(category);
            } else {
                muted.remove(category);
            }
        }
    }

    /** Enables every diagnostic category. */
    public void enableDebug() {
        synchronized (muted) {
            muted.clear();
        }
    }

    /** Silences every diagnostic category. */
    public void disableDebug() {
        synchronized (muted) {
            muted.addAll(EnumSet.allOf(LogCategory.class));
        }
    }
}
