package dev.nglmercer.tiktokminecraft.actions;

import dev.nglmercer.tiktokminecraft.rules.ActionSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;

/**
 * Builds a hollow TNT shell around a player without requiring console /fill.
 * Existing block states are preserved and restored if the TNT still remains.
 * Runs exclusively on Paper's main thread.
 */
public final class TntCageAction extends Action {
    private final Map<Block, BlockState> pendingRestorations = new HashMap<>();

    @Override public String type() { return "tnt-cage"; }

    @Override
    public void validate(ActionSpec spec, ActionContext context) throws ActionException {
        int radius = optionalInt(spec, "radius", 2);
        int height = optionalInt(spec, "height", 3);
        int restore = optionalInt(spec, "restore-seconds", 20);
        int fuse = optionalInt(spec, "fuse-ticks", 80);
        int delay = optionalInt(spec, "ignite-delay-ticks", 40);
        if (radius < 1 || radius > 3) throw new ActionException("radius must be 1..3");
        if (height < 3 || height > 6) throw new ActionException("height must be 3..6");
        if (restore < 0 || restore > 120) throw new ActionException("restore-seconds must be 0..120");
        if (fuse < 20 || fuse > 240) throw new ActionException("fuse-ticks must be 20..240");
        if (delay < 1 || delay > 200) throw new ActionException("ignite-delay-ticks must be 1..200");
        requireBoolean(spec, "ignite");
    }

    @Override
    public ActionResult execute(ActionSpec spec, ActionContext context) throws ActionException {
        List<Player> players = context.targets(optional(spec, "target", null));
        if (players.isEmpty()) return ActionResult.skipped("No eligible player for TNT cage");
        Player player = players.get(0);
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) return ActionResult.skipped("Player world unavailable");

        int radius = optionalInt(spec, "radius", 2);
        int height = optionalInt(spec, "height", 3);
        int restore = optionalInt(spec, "restore-seconds", 20);
        boolean ignite = Boolean.TRUE.equals(spec.parameter("ignite"));
        int minY = center.getBlockY() - 1;
        int maxY = center.getBlockY() + height;
        if (minY < world.getMinHeight() || maxY >= world.getMaxHeight())
            return ActionResult.skipped("TNT cage exceeds world build height");

        // Preflight all blocks first: never replace containers, other tile entities,
        // or blocks owned by an overlapping temporary cage.
        List<Block> shell = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (y != minY && y != maxY && Math.abs(dx) != radius && Math.abs(dz) != radius)
                        continue;
                    int x = center.getBlockX() + dx, z = center.getBlockZ() + dz;
                    if (!world.isChunkLoaded(x >> 4, z >> 4))
                        return ActionResult.skipped("Cage reaches an unloaded chunk");
                    Block block = world.getBlockAt(x, y, z);
                    if (pendingRestorations.containsKey(block) || block.getState() instanceof TileState)
                        return ActionResult.skipped("Cage intersects a container or another cage");
                    shell.add(block);
                }
            }
        }
        if (shell.size() > 260) return ActionResult.skipped("TNT cage block budget exceeded");

        // Reserve the primed-entity budget now, not inside a delayed task.
        // Confetti may also enqueue bursts of primed TNT during this same event.
        if (ignite && !context.consumeEntitySlot())
            return ActionResult.skipped("Entity budget unavailable for cage ignition");
        for (Block block : shell) {
            pendingRestorations.put(block, block.getState());
            block.setType(Material.TNT, false);
        }

        // An optional single primed TNT starts a chain reaction against the cage.
        if (ignite) {
            int delay = optionalInt(spec, "ignite-delay-ticks", 40);
            int fuse = optionalInt(spec, "fuse-ticks", 80);
            Block ignition = world.getBlockAt(center.getBlockX() + radius, center.getBlockY() + 1,
                    center.getBlockZ());
            context.plugin().getServer().getScheduler().runTaskLater(context.plugin(), () -> {
                if (!context.plugin().isEnabled() || ignition.getType() != Material.TNT
                        || !world.isChunkLoaded(ignition.getX() >> 4, ignition.getZ() >> 4))
                    return;
                ignition.setType(Material.AIR, false);
                Location pos = ignition.getLocation().add(0.5, 0.5, 0.5);
                TNTPrimed primed = world.spawn(pos, TNTPrimed.class);
                primed.setFuseTicks(fuse);
            }, delay);
        }

        if (restore > 0) {
            List<Block> owned = List.copyOf(shell);
            context.plugin().getServer().getScheduler().runTaskLater(context.plugin(),
                    () -> restore(owned), restore * 20L);
        }
        return ActionResult.ok("Built " + shell.size() + "-block TNT cage around "
                + player.getName() + (ignite ? " (ignition scheduled)" : ""));
    }

    private static void requireBoolean(ActionSpec spec, String key) throws ActionException {
        if (spec.parameter(key) != null && !(spec.parameter(key) instanceof Boolean))
            throw new ActionException(key + " must be a boolean");
    }

    private void restore(List<Block> blocks) {
        for (Block block : blocks) {
            if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) continue;
            BlockState old = pendingRestorations.remove(block);
            if (old != null && block.getType() == Material.TNT) old.update(true, false);
        }
    }

    /** Restore unignited cages when the plugin stops. */
    public void restoreAll() {
        restore(new ArrayList<>(pendingRestorations.keySet()));
    }
}
