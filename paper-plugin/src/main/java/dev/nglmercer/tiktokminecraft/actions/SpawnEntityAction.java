package dev.nglmercer.tiktokminecraft.actions;

import dev.nglmercer.tiktokminecraft.rules.ActionSpec;
import java.util.List;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/**
 * Spawns an entity next to the target.
 *
 * <p>Only the entity types in {@code actions.allowed-entities} may be
 * spawned, and both a per-event and a per-minute cap apply.</p>
 */
public final class SpawnEntityAction extends Action {

    @Override
    public String type() {
        return "spawn-entity";
    }

    @Override
    public void validate(ActionSpec spec, ActionContext context) throws ActionException {
        String entity = require(spec, "entity");
        if (key(entity) == null) {
            throw new ActionException("'entity' is not a valid entity type");
        }
        // Whether the entity exists in this Minecraft version is checked when
        // the action runs, so a rule that names an entity from a newer
        // version still loads.
        int count = optionalInt(spec, "count", 1);
        if (count < 1) {
            throw new ActionException("'count' must be at least 1");
        }
        // Rule count is a requested quantity; execution applies the configured
        // per-event and per-minute limits instead of rejecting joke-sized rules.
        if (count > 30000) throw new ActionException("'count' must not exceed 30000");
        int limit = optionalInt(spec, "max-effective-count", 128);
        if (limit < 1 || limit > 128) throw new ActionException("max-effective-count must be 1..128");
        if (spec.parameter("charged") != null &&
                !(spec.parameter("charged") instanceof Boolean)) {
            throw new ActionException("charged must be a boolean");
        }
    }

    @Override
    public ActionResult execute(ActionSpec spec, ActionContext context)
            throws ActionException {
        String name = spec.string("entity", "");
        NamespacedKey key = key(name);
        if (key == null) {
            return ActionResult.failed("Invalid entity type");
        }
        if (!context.config().allowedEntities().contains("*")
                && !context.config().allowedEntities().contains(key.getKey().toLowerCase(Locale.ROOT))
                && !context.config().allowedEntities().contains(name.toLowerCase(Locale.ROOT))) {
            return ActionResult.skipped(
                    "The entity type is not in actions.allowed-entities");
        }
        EntityType type = Registry.ENTITY_TYPE.get(key);
        if (type == null) {
            return ActionResult.failed("Unknown entity type: " + name);
        }
        String target = optional(spec, "target", null);
        List<Player> players = context.targets(target);
        if (players.isEmpty()) {
            return ActionResult.skipped("No eligible target");
        }
        int requested = optionalInt(spec, "count", 1);
        if (Boolean.parseBoolean(optional(spec, "multiply-by-repeat", "false"))
                && context.event() != null) {
            Object repeat = context.event().payload().get("gift.repeatCount");
            if (repeat instanceof Number) requested = (int) Math.min(30000L,
                    (long) requested * Math.max(1L, ((Number) repeat).longValue()));
        }
        int limit = optionalInt(spec, "max-effective-count", 128);
        int effective = Math.min(requested,
                Math.min(limit, context.config().maxEntitiesPerEvent()));
        int spawned = 0;
        for (Player player : players) {
            for (int index = 0; index < effective; index++) {
                if (!context.consumeEntitySlot()) {
                    return ActionResult.ok(
                            "Spawned " + spawned + " entity/entities before the limit");
                }
                Location location = safeLocation(player);
                if (location == null) {
                    continue;
                }
                try {
                    Entity entity = location.getWorld().spawnEntity(location, type);
                    if (Boolean.TRUE.equals(spec.parameter("charged"))
                            && entity instanceof org.bukkit.entity.Creeper) {
                        ((org.bukkit.entity.Creeper) entity).setPowered(true);
                    }
                    spawned++;
                } catch (RuntimeException failure) {
                    return ActionResult.failed(
                            "The entity could not be spawned: " + failure.getMessage());
                }
            }
        }
        return ActionResult.ok("Spawned " + spawned + " entity/entities");
    }

    /**
     * A spawn position next to the player.
     *
     * <p>The chunk is checked first: spawning into an unloaded chunk would
     * load it and stall the tick.</p>
     */
    private static Location safeLocation(Player player) {
        Location base = player.getLocation();
        if (!base.isWorldLoaded() || !base.getWorld().isChunkLoaded(
                base.getBlockX() >> 4, base.getBlockZ() >> 4)) {
            return null;
        }
        Location location = base.clone().add(0.0, 1.0, 0.0);
        if (location.getBlockY() <= location.getWorld().getMinHeight()) {
            location.setY(location.getWorld().getMinHeight() + 1);
        }
        return location;
    }

    private static NamespacedKey key(String name) {
        return ParticleAction.key(name);
    }
}
