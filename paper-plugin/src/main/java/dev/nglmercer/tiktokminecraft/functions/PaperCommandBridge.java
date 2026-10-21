package dev.nglmercer.tiktokminecraft.functions;

import dev.nglmercer.tiktokminecraft.minecraft.CancellationToken;
import dev.nglmercer.tiktokminecraft.minecraft.ChatResult;
import dev.nglmercer.tiktokminecraft.minecraft.MinecraftBridge;
import dev.nglmercer.tiktokminecraft.security.CommandPolicy;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;

/**
 * The Paper side of the function engine.
 *
 * <p>Every command a function expands to is queued and dispatched on the
 * server thread. Relative coordinates are rewritten against the job's
 * target, so a step such as {@code summon minecraft:zombie ~ ~1 ~} spawns
 * next to the intended player instead of at the world spawn.</p>
 */
public final class PaperCommandBridge implements MinecraftBridge {

    private final CommandDispatcher dispatcher;
    private final TargetSession session;
    private final CommandPolicy policy;
    private final boolean consoleCommandsAllowed;

    public PaperCommandBridge(
            CommandDispatcher dispatcher,
            TargetSession session,
            CommandPolicy policy,
            boolean consoleCommandsAllowed) {
        this.dispatcher = dispatcher;
        this.session = session;
        this.policy = policy;
        this.consoleCommandsAllowed = consoleCommandsAllowed;
    }

    @Override
    public boolean isInWorld() {
        return session != null && session.isValid();
    }

    @Override
    public String getPlayerName() {
        return session == null ? "server" : session.describe();
    }

    @Override
    public Object sessionIdentity() {
        return session == null ? null : session.identity();
    }

    @Override
    public ChatResult sendChat(String text) {
        // Only meaningful on the server thread; the dispatcher is the normal
        // path because it applies the tick budget.
        if (text == null || text.isEmpty()) {
            return ChatResult.failure("Empty command", "EMPTY_COMMAND");
        }
        return ChatResult.failure(
                "Commands must be queued through submitChat", "NOT_QUEUED");
    }

    @Override
    public CompletableFuture<ChatResult> submitChat(
            String text, Object ignored, CancellationToken cancellation) {
        if (text == null || text.isEmpty()) {
            return CompletableFuture.completedFuture(
                    ChatResult.failure("Empty command", "EMPTY_COMMAND"));
        }
        if (cancellation != null && cancellation.isCancelled()) {
            return CompletableFuture.completedFuture(
                    ChatResult.failure("Cancelled", "CANCELLED"));
        }
        if (!consoleCommandsAllowed) {
            return CompletableFuture.completedFuture(
                    ChatResult.failure(
                            "Console commands are disabled (security.allow-console-commands)",
                            "CONSOLE_COMMANDS_DISABLED"));
        }
        // The allowlist is checked again here: the compile time check is not
        // the last line of defence.
        if (policy != null && !policy.allows(text)) {
            return CompletableFuture.completedFuture(
                    ChatResult.failure("Command denied by policy: " + text, "COMMAND_DENIED"));
        }
        String command = text;
        Location origin = session == null ? null : session.origin();
        if (CommandTargetResolver.usesRelativeCoordinates(command)) {
            if (origin == null) {
                // A relative command without a target would silently act at
                // the world spawn.
                return CompletableFuture.completedFuture(
                        ChatResult.failure(
                                "The command uses relative coordinates but no target is available",
                                "NO_TARGET"));
            }
            command = CommandTargetResolver.resolve(command, origin);
        }
        return dispatcher.submit(command, session, cancellation);
    }

    /** The session this bridge is bound to. */
    public TargetSession session() {
        return session;
    }
}
