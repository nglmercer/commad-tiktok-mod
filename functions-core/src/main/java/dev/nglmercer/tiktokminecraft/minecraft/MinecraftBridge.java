package dev.nglmercer.tiktokminecraft.minecraft;

/**
 * The seam between the shared function engine and a concrete Minecraft server.
 *
 * <p>Implementations are called from worker threads, so they are responsible
 * for hopping onto the server thread when the underlying API requires it.</p>
 */
public interface MinecraftBridge {

    /** @return true when a server session is available to receive work. */
    boolean isInWorld();

    /** @return an identity for the current session, or null when none exists. */
    String getPlayerName();

    /**
     * Sends {@code text} as the server: a chat message, or a command when it
     * starts with {@code /}.
     */
    ChatResult sendChat(String text);

    /** Checks session affinity before sending queued work. */
    default ChatResult sendChat(String text, Object session) {
        return java.util.Objects.equals(session, sessionIdentity()) ? sendChat(text)
                : ChatResult.failure("Session changed");
    }

    default java.util.concurrent.CompletableFuture<ChatResult> submitChat(
            String text, Object session, CancellationToken cancellation) {
        if (cancellation.isCancelled()) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                    ChatResult.failure("Cancelled", "CANCELLED"));
        }
        return java.util.concurrent.CompletableFuture.completedFuture(sendChat(text, session));
    }

    /** Stable identity for the current session; jobs cannot cross sessions. */
    default Object sessionIdentity() {
        return getPlayerName();
    }
}
