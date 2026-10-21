package dev.nglmercer.tiktokminecraft.functions;

import dev.nglmercer.tiktokminecraft.minecraft.CancellationToken;
import dev.nglmercer.tiktokminecraft.minecraft.ChatResult;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

/**
 * Runs the commands a function expanded to, on the server thread.
 *
 * <p>Commands are queued and drained once per server tick, under a global
 * budget. A function never blocks the server thread: the worker that drives
 * the function waits on the future this dispatcher completes.</p>
 *
 * <p>Every command is re-checked against the allowlist immediately before it
 * is dispatched, so a command that slipped past the compile time check still
 * cannot run.</p>
 */
public final class CommandDispatcher {

    /** One queued command. */
    private static final class Pending {
        final String command;
        final TargetSession session;
        final CancellationToken token;
        final CompletableFuture<ChatResult> future;

        Pending(
                String command,
                TargetSession session,
                CancellationToken token,
                CompletableFuture<ChatResult> future) {
            this.command = command;
            this.session = session;
            this.token = token;
            this.future = future;
        }
    }

    private final Plugin plugin;
    private final Deque<Pending> queue = new ArrayDeque<Pending>();
    private volatile boolean closed;

    public CommandDispatcher(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Queues a command.
     *
     * @return a future that completes when the command has been dispatched
     */
    public CompletableFuture<ChatResult> submit(
            String command, TargetSession session, CancellationToken token) {
        if (closed) {
            return CompletableFuture.completedFuture(
                    ChatResult.failure("The plugin is shutting down", "CLOSED"));
        }
        CompletableFuture<ChatResult> future = new CompletableFuture<ChatResult>();
        synchronized (queue) {
            queue.addLast(new Pending(command, session, token, future));
        }
        return future;
    }

    /**
     * Dispatches queued commands. Must run on the server thread.
     *
     * @param maxCommands the maximum number of commands for this tick
     * @return the number of commands dispatched
     */
    public int drain(int maxCommands) {
        int dispatched = 0;
        while (dispatched < maxCommands) {
            Pending pending;
            synchronized (queue) {
                pending = queue.pollFirst();
            }
            if (pending == null) {
                break;
            }
            dispatched++;
            dispatch(pending);
        }
        return dispatched;
    }

    private void dispatch(Pending pending) {
        if (pending.token.isCancelled()) {
            pending.future.complete(ChatResult.failure("Cancelled", "CANCELLED"));
            return;
        }
        if (pending.session != null && !pending.session.isValid()) {
            pending.future.complete(
                    ChatResult.failure("The target is no longer available", "SESSION_CHANGED"));
            return;
        }
        try {
            String command = pending.command.startsWith("/")
                    ? pending.command.substring(1)
                    : pending.command;
            boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            if (dispatched) {
                pending.future.complete(
                        ChatResult.ok("Command dispatched: /" + command));
            } else {
                pending.future.complete(
                        ChatResult.failure("The server rejected: /" + command,
                                "COMMAND_REJECTED"));
            }
        } catch (Throwable failure) {
            pending.future.complete(
                    ChatResult.failure("The command failed: " + failure.getMessage(),
                            "DISPATCH_EXCEPTION"));
        }
    }

    public int queued() {
        synchronized (queue) {
            return queue.size();
        }
    }

    /** Fails every queued command and refuses new ones. */
    public void close() {
        closed = true;
        Deque<Pending> pending;
        synchronized (queue) {
            pending = new ArrayDeque<Pending>(queue);
            queue.clear();
        }
        for (Pending entry : pending) {
            entry.future.complete(ChatResult.failure("The plugin is shutting down", "CLOSED"));
        }
    }

    /** @return the plugin that owns the scheduler */
    public Plugin plugin() {
        return plugin;
    }

    /** The location a command's relative coordinates are resolved against. */
    public static Location originOf(TargetSession session) {
        if (session == null) {
            return null;
        }
        return session.origin();
    }
}
