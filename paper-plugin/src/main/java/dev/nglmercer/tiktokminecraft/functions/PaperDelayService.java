package dev.nglmercer.tiktokminecraft.functions;

import dev.nglmercer.tiktokminecraft.functions.FunctionExecutor;
import dev.nglmercer.tiktokminecraft.minecraft.CancellationToken;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Realizes tick delays with the Paper scheduler.
 *
 * <p>The worker thread waits for the scheduled task; the server thread only
 * ever counts down a latch, so a delay never blocks a tick.</p>
 */
public final class PaperDelayService implements FunctionExecutor.DelayService {

    private final Plugin plugin;

    public PaperDelayService(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean waitTicks(
            int ticks,
            CancellationToken token,
            Object session,
            FunctionExecutor.SessionCheck check) {
        if (ticks <= 0) {
            return !token.isCancelled();
        }
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            Bukkit.getScheduler()
                    .runTaskLater(plugin, latch::countDown, Math.max(1L, ticks));
        } catch (IllegalStateException ignored) {
            // The scheduler is shutting down; the caller treats this as a
            // cancelled delay.
            return false;
        }
        try {
            while (!latch.await(50L, TimeUnit.MILLISECONDS)) {
                if (token.isCancelled()) {
                    return false;
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
        return !token.isCancelled();
    }
}
