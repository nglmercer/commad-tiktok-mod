package dev.nglmercer.tiktokminecraft.actions;

import dev.nglmercer.tiktokminecraft.rules.ActionSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Creates a vertical shaft under a player down to the world's minimum Y.
 * It includes the bedrock floor, allowing a fall into the void. Work is spread
 * across ticks and the modified blocks can be restored after the prank.
 * Never modifies tile entities / container blocks or unloaded chunks.
 */
public final class VoidShaftAction extends Action {
    private final Map<Block, BlockState> removed = new HashMap<>();

    @Override public String type() { return "void-shaft"; }

    @Override
    public void validate(ActionSpec spec, ActionContext context) throws ActionException {
        int radius = optionalInt(spec, "radius", 1);
        int perTick = optionalInt(spec, "blocks-per-tick", 96);
        int max = optionalInt(spec, "max-blocks", 2048);
        int restore = optionalInt(spec, "restore-seconds", 30);
        if (radius < 0 || radius > 1) throw new ActionException("radius must be 0..1");
        if (perTick < 1 || perTick > 128) throw new ActionException("blocks-per-tick must be 1..128");
        if (max < 1 || max > 4096) throw new ActionException("max-blocks must be 1..4096");
        if (restore < 0 || restore > 180) throw new ActionException("restore-seconds must be 0..180");
    }

    @Override
    public ActionResult execute(ActionSpec spec, ActionContext context) throws ActionException {
        List<Player> targets = context.targets(optional(spec, "target", null));
        if (targets.isEmpty()) return ActionResult.skipped("No eligible target for void shaft");
        Player target = targets.get(0);
        Location from = target.getLocation();
        World world = from.getWorld();
        if (world == null) return ActionResult.skipped("Target world unavailable");
        int radius = optionalInt(spec, "radius", 1);
        int perTick = optionalInt(spec, "blocks-per-tick", 96);
        int max = optionalInt(spec, "max-blocks", 2048);
        int restoreSeconds = optionalInt(spec, "restore-seconds", 30);
        int startY = from.getBlockY() - 1;
        int minY = world.getMinHeight();
        if (startY < minY) return ActionResult.skipped("Player already at minimum world height");

        int side = radius * 2 + 1;
        long potential = (long) side * side * (startY - minY + 1L);
        if (potential > max) return ActionResult.skipped(
                "Void shaft needs " + potential + " blocks, max-blocks is " + max);

        // Bottom-up order: the floor below the player disappears last, after
        // the entire drop is cleared. Preflight before touching the world.
        List<Block> blocks = new ArrayList<>();
        for (int y = minY; y <= startY; y++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = from.getBlockX() + dx, z = from.getBlockZ() + dz;
                    if (!world.isChunkLoaded(x >> 4, z >> 4))
                        return ActionResult.skipped("Shaft crosses an unloaded chunk");
                    Block block = world.getBlockAt(x, y, z);
                    if (removed.containsKey(block) || block.getState() instanceof TileState)
                        return ActionResult.skipped("Shaft intersects an active prank or container");
                    if (!block.getType().isAir()) blocks.add(block);
                }
            }
        }
        if (blocks.isEmpty()) return ActionResult.skipped("No blocks below target to remove");

        final UUID targetId = target.getUniqueId();
        final Location start = from.clone();
        new BukkitRunnable() {
            private int cursor;
            private final List<Block> owned = new ArrayList<>();
            @Override public void run() {
                if (!context.plugin().isEnabled()) { cancel(); return; }
                int modified = 0;
                while (modified < perTick && cursor < blocks.size()) {
                    Block block = blocks.get(cursor++);
                    if (!world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4))
                        continue;
                    if (block.getState() instanceof TileState || removed.containsKey(block))
                        continue;
                    removed.put(block, block.getState());
                    owned.add(block);
                    block.setType(Material.AIR, false);
                    modified++;
                }
                if (cursor < blocks.size()) return;
                cancel();

                // Center the target above the shaft once the full drop is open.
                Player player = Bukkit.getPlayer(targetId);
                if (player != null && player.isOnline() && player.getWorld().equals(world)
                        && player.getLocation().distanceSquared(start) < 36.0) {
                    Location centered = player.getLocation().clone();
                    centered.setX(start.getBlockX() + 0.5);
                    centered.setZ(start.getBlockZ() + 0.5);
                    player.teleport(centered);
                }
                if (restoreSeconds > 0) {
                    context.plugin().getServer().getScheduler().runTaskLater(
                            context.plugin(), () -> restoreGradually(context, owned),
                            restoreSeconds * 20L);
                }
            }
        }.runTaskTimer(context.plugin(), 1L, 1L);
        return ActionResult.ok("Preparing " + blocks.size() + "-block shaft under "
                + target.getName() + " in batches of " + perTick);
    }

    private void restoreGradually(ActionContext context, List<Block> owned) {
        Iterator<Block> iterator = owned.iterator();
        new BukkitRunnable() {
            @Override public void run() {
                if (!context.plugin().isEnabled()) { cancel(); return; }
                int restored = 0;
                while (restored < 128 && iterator.hasNext()) {
                    Block block = iterator.next();
                    BlockState old = removed.remove(block);
                    if (old != null && block.getType().isAir()
                            && block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4))
                        old.update(true, false);
                    restored++;
                }
                if (!iterator.hasNext()) cancel();
            }
        }.runTaskTimer(context.plugin(), 1L, 1L);
    }

    /** Restore all modified blocks still in loaded chunks on plugin disable. */
    public void restoreAll() {
        for (Map.Entry<Block, BlockState> entry : new ArrayList<>(removed.entrySet())) {
            Block block = entry.getKey();
            if (block.getType().isAir()
                    && block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4))
                entry.getValue().update(true, false);
        }
        removed.clear();
    }
}
