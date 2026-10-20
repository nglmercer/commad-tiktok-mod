package dev.nglmercer.tiktokminecraft.actions;

import dev.nglmercer.tiktokminecraft.config.PluginConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Resolves the players an action applies to.
 *
 * <p>Players are addressed by UUID, never by display name, so a target is
 * stable. A target that resolves to nobody is never silently redirected
 * somewhere else: the caller applies the configured no-player policy.</p>
 */
public final class TargetResolver {

    private TargetResolver() {
    }

    /**
     * Resolves a target specification.
     *
     * @param spec the target from the rule, or null for the configured default
     * @param policy the configured default policy
     * @param origin the position the event happened at, may be null
     */
    public static List<Player> resolve(String spec, PluginConfig.TargetPolicy policy,
            Location origin) {
        String normalized = spec == null ? "" : spec.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return resolveDefault(policy, origin);
        }
        int colon = normalized.indexOf(':');
        String kind = colon < 0 ? normalized : normalized.substring(0, colon);
        String argument = colon < 0 ? "" : normalized.substring(colon + 1);
        switch (kind) {
            case "all-online":
                return new ArrayList<Player>(Bukkit.getOnlinePlayers());
            case "random-online": {
                List<Player> online = new ArrayList<Player>(Bukkit.getOnlinePlayers());
                if (online.isEmpty()) {
                    return Collections.emptyList();
                }
                java.util.Collections.shuffle(online);
                return online.subList(0, 1);
            }
            case "nearest-online":
                return nearest(origin);
            case "specific-player": {
                Player player = Bukkit.getPlayerExact(argument);
                if (player == null) {
                    UUID id = parseUuid(argument);
                    player = id == null ? null : Bukkit.getPlayer(id);
                }
                return player == null
                        ? Collections.<Player>emptyList()
                        : Collections.singletonList(player);
            }
            case "specific-world": {
                World world = Bukkit.getWorld(argument);
                if (world == null) {
                    return Collections.emptyList();
                }
                List<Player> players = new ArrayList<Player>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getWorld().equals(world)) {
                        players.add(player);
                    }
                }
                return players;
            }
            default:
                return resolveDefault(policy, origin);
        }
    }

    private static List<Player> resolveDefault(
            PluginConfig.TargetPolicy policy, Location origin) {
        switch (policy) {
            case ALL_ONLINE:
                return new ArrayList<Player>(Bukkit.getOnlinePlayers());
            case RANDOM_ONLINE: {
                List<Player> online = new ArrayList<Player>(Bukkit.getOnlinePlayers());
                if (online.isEmpty()) {
                    return Collections.emptyList();
                }
                java.util.Collections.shuffle(online);
                return online.subList(0, 1);
            }
            case NEAREST_ONLINE:
                return nearest(origin);
            case SPECIFIC_PLAYER:
            case SPECIFIC_WORLD:
            default:
                return Collections.emptyList();
        }
    }

    private static List<Player> nearest(Location origin) {
        List<Player> online = new ArrayList<Player>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            return Collections.emptyList();
        }
        if (origin == null) {
            return Collections.singletonList(online.get(0));
        }
        Player best = online.get(0);
        double bestDistance = best.getLocation().distanceSquared(origin);
        for (Player player : online) {
            double distance = player.getLocation().distanceSquared(origin);
            if (distance < bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return Collections.singletonList(best);
    }

    private static UUID parseUuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
