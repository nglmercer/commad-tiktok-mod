package dev.nglmercer.tiktokminecraft.actions;

import dev.nglmercer.tiktokminecraft.rules.ActionSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Replaces blocks chunk by chunk around a player.
 *
 * <p>By default only the chunk the target player stands in is modified, over
 * the full world height. {@code material} is placed on every matching block:
 * use {@code minecraft:air} to clean a chunk, or any other block to convert
 * it. {@code from} optionally restricts the replacement to one source
 * material, so a single block type can be converted block by block while
 * everything else is left alone.</p>
 *
 * <p>{@code max-blocks} is optional and unlimited by default ({@code 0}):
 * only when it is set above zero does the action refuse to run once more
 * blocks would change than the limit allows. Work is spread across ticks
 * and modified blocks can be restored after the prank. Container blocks
 * (tile entities) and unloaded chunks are never touched.</p>
 */
public final class ChunkReplaceAction extends Action {
    private final Map<Block, BlockState> removed = new HashMap<>();
    private final Map<Block, Material> placed = new HashMap<>();

    @Override public String type() { return "chunk-replace"; }

    @Override
    public void validate(ActionSpec spec, ActionContext context) throws ActionException {
        // Cheap checks first: they need no server registry, so broken numbers
        // are reported even where Bukkit registries are unavailable.
        String materialName = require(spec, "material");
        int radius = optionalInt(spec, "radius-chunks", 0);
        int perTick = optionalInt(spec, "blocks-per-tick", 512);
        int max = optionalInt(spec, "max-blocks", 0);
        int restore = optionalInt(spec, "restore-seconds", 0);
        if (radius < 0 || radius > 1) throw new ActionException("radius-chunks must be 0..1");
        if (perTick < 1 || perTick > 4096)
            throw new ActionException("blocks-per-tick must be 1..4096");
        if (max < 0) throw new ActionException("max-blocks must be 0 or more (0 is unlimited)");
        if (restore < 0 || restore > 180)
            throw new ActionException("restore-seconds must be 0..180");
        Integer minY = optionalY(spec, "min-y");
        Integer maxY = optionalY(spec, "max-y");
        if (minY != null && maxY != null && minY > maxY)
            throw new ActionException("min-y must not be above max-y");
        // Server-backed material resolution last.
        blockMaterial(materialName, "material", true);
        Object from = spec.parameter("from");
        if (from != null && !String.valueOf(from).trim().isEmpty()) {
            blockMaterial(String.valueOf(from).trim(), "from", true);
        }
    }

    @Override
    public ActionResult execute(ActionSpec spec, ActionContext context) throws ActionException {
        List<Player> targets = context.targets(optional(spec, "target", null));
        if (targets.isEmpty()) return ActionResult.skipped("No eligible target for chunk replace");
        Player target = targets.get(0);
        Location from = target.getLocation();
        World world = from.getWorld();
        if (world == null) return ActionResult.skipped("Target world unavailable");

        Material material = blockMaterial(require(spec, "material"), "material", true);
        Object fromParam = spec.parameter("from");
        Material fromMaterial = null;
        if (fromParam != null && !String.valueOf(fromParam).trim().isEmpty()) {
            fromMaterial = blockMaterial(
                    String.valueOf(fromParam).trim(), "from", true);
        }
        int radius = optionalInt(spec, "radius-chunks", 0);
        int perTick = optionalInt(spec, "blocks-per-tick", 512);
        int max = optionalInt(spec, "max-blocks", 0);
        int restoreSeconds = optionalInt(spec, "restore-seconds", 0);

        int bottom = optionalY(spec, "min-y") == null
                ? world.getMinHeight() : optionalY(spec, "min-y");
        int top = optionalY(spec, "max-y") == null
                ? world.getMaxHeight() - 1 : optionalY(spec, "max-y");
        bottom = Math.max(bottom, world.getMinHeight());
        top = Math.min(top, world.getMaxHeight() - 1);
        if (bottom > top) return ActionResult.skipped("Chunk replace Y range is outside the world");

        int centerX = from.getBlockX() >> 4;
        int centerZ = from.getBlockZ() >> 4;
        List<Block> blocks = new ArrayList<>();
        int skippedChunks = 0;
        for (int cx = centerX - radius; cx <= centerX + radius; cx++) {
            for (int cz = centerZ - radius; cz <= centerZ + radius; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    skippedChunks++;
                    continue;
                }
                int baseX = cx << 4;
                int baseZ = cz << 4;
                for (int y = bottom; y <= top; y++) {
                    for (int x = baseX; x < baseX + 16; x++) {
                        for (int z = baseZ; z < baseZ + 16; z++) {
                            Block block = world.getBlockAt(x, y, z);
                            if (removed.containsKey(block)
                                    || block.getState() instanceof TileState) continue;
                            if (fromMaterial != null && block.getType() != fromMaterial) continue;
                            if (block.getType() == material) continue;
                            blocks.add(block);
                        }
                    }
                }
            }
        }
        if (blocks.isEmpty()) return ActionResult.skipped("No blocks to replace in target chunk(s)");
        if (max > 0 && blocks.size() > max) return ActionResult.skipped(
                "Chunk replace needs " + blocks.size() + " blocks, max-blocks is " + max);

        Material placedMaterial = material;
        new BukkitRunnable() {
            private int cursor;
            private final List<Block> owned = new ArrayList<>();
            @Override public void run() {
                if (!context.plugin().isEnabled()) { cancel(); return; }
                int modified = 0;
                while (modified < perTick && cursor < blocks.size()) {
                    Block block = blocks.get(cursor++);
                    if (!block.getWorld().isChunkLoaded(
                            block.getX() >> 4, block.getZ() >> 4)) continue;
                    if (block.getState() instanceof TileState
                            || removed.containsKey(block)
                            || block.getType() == placedMaterial) continue;
                    removed.put(block, block.getState());
                    placed.put(block, placedMaterial);
                    owned.add(block);
                    block.setType(placedMaterial, false);
                    modified++;
                }
                if (cursor < blocks.size()) return;
                cancel();
                if (restoreSeconds > 0) {
                    context.plugin().getServer().getScheduler().runTaskLater(
                            context.plugin(), () -> restoreGradually(context, owned),
                            restoreSeconds * 20L);
                } else {
                    for (Block block : owned) {
                        removed.remove(block);
                        placed.remove(block);
                    }
                }
            }
        }.runTaskTimer(context.plugin(), 1L, 1L);
        String area = radius == 0 ? "chunk " + centerX + "," + centerZ
                : ((radius * 2 + 1) + "x" + (radius * 2 + 1) + " chunks around "
                        + centerX + "," + centerZ);
        return ActionResult.ok("Replacing " + blocks.size() + " blocks with " + placedMaterial
                + " in " + area + " in batches of " + perTick
                + (skippedChunks > 0 ? " (" + skippedChunks + " unloaded chunks skipped)" : ""));
    }

    private void restoreGradually(ActionContext context, List<Block> owned) {
        Iterator<Block> iterator = owned.iterator();
        new BukkitRunnable() {
            @Override public void run() {
                if (!context.plugin().isEnabled()) { cancel(); return; }
                int restored = 0;
                while (restored < 1024 && iterator.hasNext()) {
                    Block block = iterator.next();
                    BlockState old = removed.remove(block);
                    Material was = placed.remove(block);
                    if (old != null && was != null && block.getWorld().isChunkLoaded(
                            block.getX() >> 4, block.getZ() >> 4) && block.getType() == was)
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
            Material was = placed.get(block);
            if (was != null && block.getWorld().isChunkLoaded(
                    block.getX() >> 4, block.getZ() >> 4) && block.getType() == was)
                entry.getValue().update(true, false);
        }
        removed.clear();
        placed.clear();
    }

    private static Material blockMaterial(String name, String param, boolean allowAir)
            throws ActionException {
        return BlockMaterials.resolve(name, param, allowAir, true);
    }

    private static Integer optionalY(ActionSpec spec, String name) throws ActionException {
        Object value = spec.parameter(name);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException failure) {
            throw new ActionException("'" + name + "' must be a whole number");
        }
    }
}
