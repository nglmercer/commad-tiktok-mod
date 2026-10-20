package dev.nglmercer.tiktokminecraft.functions;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * The identity a running job is bound to.
 *
 * <p>A job that targets a player is cancelled when that player leaves. A job
 * without a player target stays valid for the lifetime of the server
 * session.</p>
 */
public interface TargetSession {

    /** @return true while the target is still available */
    boolean isValid();

    /** The stable identity of the session. */
    Object identity();

    /** Where relative coordinates are resolved against. */
    Location origin();

    /** A short description used in diagnostics. */
    String describe();

    /** A session bound to one player. */
    final class PlayerSession implements TargetSession {
        private final UUID playerId;

        public PlayerSession(UUID playerId) {
            this.playerId = playerId;
        }

        public UUID playerId() {
            return playerId;
        }

        @Override
        public boolean isValid() {
            return playerId != null && Bukkit.getPlayer(playerId) != null;
        }

        @Override
        public Object identity() {
            return playerId;
        }

        @Override
        public Location origin() {
            Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
            return player == null ? null : player.getLocation();
        }

        @Override
        public String describe() {
            Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
            return player == null ? "player " + playerId : player.getName();
        }
    }

    /** A session that is valid for as long as the server runs. */
    final class ServerSession implements TargetSession {
        private final Object identity;

        public ServerSession(Object identity) {
            this.identity = identity;
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public Object identity() {
            return identity;
        }

        @Override
        public Location origin() {
            return null;
        }

        @Override
        public String describe() {
            return "the server";
        }
    }
}
