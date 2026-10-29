package dev.nglmercer.tiktokminecraft.actions;

import dev.nglmercer.tiktokminecraft.rules.ActionSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;

/**
 * Builds a configurable block cage around one player. Supports TNT, obsidian,
 * glass and other ordinary Minecraft blocks without running console commands.
 * Optionally spawns one mob in the empty center and ignites a TNT wall.
 * The same instance also serves the legacy "tnt-cage" action name.
 *
 * Bukkit world modifications and scheduled callbacks run on the server thread.
 */
public class BlockCageAction extends Action {
    private static final int MAX_SHELL_BLOCKS = 260;

    private static final class Saved {
        final BlockState previous;
        final Material placed;
        Saved(BlockState previous, Material placed) {
            this.previous = previous;
            this.placed = placed;
        }
    }

    private final Map<Block, Saved> pendingRestorations = new HashMap<>();

    @Override public String type() { return "block-cage"; }

    @Override
    public void validate(ActionSpec spec, ActionContext context) throws ActionException {
        Material material = material(spec);
        int radius = optionalInt(spec, "radius", 2);
        int height = optionalInt(spec, "height", 3);
        int restore = optionalInt(spec, "restore-seconds", 20);
        int fuse = optionalInt(spec, "fuse-ticks", 80);
        int delay = optionalInt(spec, "ignite-delay-ticks", 40);
        if (radius < 1 || radius > 3) throw new ActionException("radius must be 1..3");
        if (height < 3 || height > 6) throw new ActionException("height must be 3..6");
        if (restore < 0 || restore > 120)
            throw new ActionException("restore-seconds must be 0..120");
        if (fuse < 20 || fuse > 240)
            throw new ActionException("fuse-ticks must be 20..240");
        if (delay < 1 || delay > 200)
            throw new ActionException("ignite-delay-ticks must be 1..200");
        requireBoolean(spec, "ignite");
        requireBoolean(spec, "occupant-charged");
        if (Boolean.TRUE.equals(spec.parameter("ignite")) && material != Material.TNT)
            throw new ActionException("ignite: true is only supported with minecraft:tnt");
        String occupant = optional(spec, "occupant", null);
        if (occupant == null) {
            if (Boolean.TRUE.equals(spec.parameter("occupant-charged")))
                throw new ActionException("occupant-charged requires occupant: minecraft:creeper");
        } else {
            if (ParticleAction.key(occupant) == null)
                throw new ActionException("Invalid occupant entity key: " + occupant);
            if (Boolean.TRUE.equals(spec.parameter("occupant-charged"))
                    && !"minecraft:creeper".equals(occupant.toLowerCase(Locale.ROOT))
                    && !"creeper".equalsIgnoreCase(occupant))
                throw new ActionException("Only a creeper can be charged");
        }
    }

    @Override
    public ActionResult execute(ActionSpec spec, ActionContext context) throws ActionException {
        Material material = material(spec);
        List<Player> players = context.targets(optional(spec, "target", null));
        if (players.isEmpty()) return ActionResult.skipped("No eligible player for block cage");
        Player player = players.get(0);
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) return ActionResult.skipped("Player world unavailable");

        int radius = optionalInt(spec, "radius", 2);
        int height = optionalInt(spec, "height", 3);
        int restore = optionalInt(spec, "restore-seconds", 20);
        boolean ignite = Boolean.TRUE.equals(spec.parameter("ignite"));
        int bottom = center.getBlockY() - 1;
        int top = center.getBlockY() + height;
        if (bottom < world.getMinHeight() || top >= world.getMaxHeight())
            return ActionResult.skipped("Block cage exceeds build height");

        String occupantName = optional(spec, "occupant", null);
        EntityType occupant = null;
        if (occupantName != null) {
            NamespacedKey key = ParticleAction.key(occupantName);
            if (key == null) return ActionResult.failed("Invalid occupant");
            String normalized = occupantName.toLowerCase(Locale.ROOT);
            if (!context.config().allowedEntities().contains("*")
                    && !context.config().allowedEntities().contains(normalized)
                    && !context.config().allowedEntities().contains(key.getKey()))
                return ActionResult.skipped("Cage occupant is not in actions.allowed-entities");
            occupant = Registry.ENTITY_TYPE.get(key);
            if (occupant == null) return ActionResult.failed("Unknown occupant: " + occupantName);
        }

        // Collect and validate the complete shell before any world modification.
        // The single interior column of a radius-1 cage stays empty for the player.
        List<Block> shell = new ArrayList<>();
        for (int y = bottom; y <= top; y++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    boolean boundary = y == bottom || y == top
                            || Math.abs(dx) == radius || Math.abs(dz) == radius;
                    if (!boundary) continue;
                    int x = center.getBlockX() + dx;
                    int z = center.getBlockZ() + dz;
                    if (!world.isChunkLoaded(x >> 4, z >> 4))
                        return ActionResult.skipped("Cage would reach an unloaded chunk");
                    Block block = world.getBlockAt(x, y, z);
                    if (pendingRestorations.containsKey(block) || block.getState() instanceof TileState)
                        return ActionResult.skipped("Cage overlaps an active cage or a container");
                    shell.add(block);
                }
            }
        }
        if (shell.size() > MAX_SHELL_BLOCKS)
            return ActionResult.skipped("Block cage budget exceeded");

        // Reserve entity slots before changing the world, including delayed TNT.
        if (occupant != null && !context.consumeEntitySlot())
            return ActionResult.skipped("Entity budget unavailable for occupant");
        if (ignite && !context.consumeEntitySlot())
            return ActionResult.skipped("Entity budget unavailable for cage ignition");

        List<Block> built = new ArrayList<>();
        try {
            for (Block block : shell) {
                pendingRestorations.put(block, new Saved(block.getState(), material));
                built.add(block);
                block.setType(material, false);
            }
            if (occupant != null) {
                Location inside = center.clone().add(0.0, 0.15, 0.0);
                Entity entity = world.spawnEntity(inside, occupant);
                if (Boolean.TRUE.equals(spec.parameter("occupant-charged"))
                        && entity instanceof Creeper) {
                    ((Creeper) entity).setPowered(true);
                }
            }
        } catch (RuntimeException failure) {
            restore(built);
            return ActionResult.failed("Could not complete block cage: " + failure.getMessage());
        }

        if (ignite) {
            int delay = optionalInt(spec, "ignite-delay-ticks", 40);
            int fuse = optionalInt(spec, "fuse-ticks", 80);
            Block ignition = world.getBlockAt(center.getBlockX() + radius,
                    center.getBlockY() + 1, center.getBlockZ());
            context.plugin().getServer().getScheduler().runTaskLater(context.plugin(), () -> {
                if (!context.plugin().isEnabled()
                        || !world.isChunkLoaded(ignition.getX() >> 4, ignition.getZ() >> 4)
                        || ignition.getType() != Material.TNT) return;
                ignition.setType(Material.AIR, false);
                TNTPrimed primed = world.spawn(
                        ignition.getLocation().add(0.5, 0.5, 0.5), TNTPrimed.class);
                primed.setFuseTicks(fuse);
            }, delay);
        }

        if (restore > 0) {
            List<Block> owned = List.copyOf(built);
            context.plugin().getServer().getScheduler().runTaskLater(context.plugin(),
                    () -> restore(owned), restore * 20L);
        } else {
            // Permanent cages are explicitly opted in; do not keep state forever.
            for (Block block : built) pendingRestorations.remove(block);
        }
        return ActionResult.ok("Built " + shell.size() + " " + material
                + " cage blocks around " + player.getName()
                + (occupant == null ? "" : " with " + occupantName + " inside"));
    }

    private static Material material(ActionSpec spec) throws ActionException {
        String name = optional(spec, "material", null);
        if (name == null && "tnt-cage".equalsIgnoreCase(spec.type())) name = "minecraft:tnt";
        if (name == null)
            throw new ActionException("material is required for block-cage");
        return BlockMaterials.resolve(name, "material", false, false);
    }

    private static void requireBoolean(ActionSpec spec, String key) throws ActionException {
        if (spec.parameter(key) != null && !(spec.parameter(key) instanceof Boolean))
            throw new ActionException(key + " must be a boolean");
    }

    private void restore(List<Block> blocks) {
        for (Block block : blocks) {
            if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4))
                continue;
            Saved saved = pendingRestorations.remove(block);
            if (saved != null && block.getType() == saved.placed)
                saved.previous.update(true, false);
        }
    }

    /** Restore temporary cages on plugin shutdown, without loading new chunks. */
    public void restoreAll() {
        restore(new ArrayList<>(pendingRestorations.keySet()));
    }
}
