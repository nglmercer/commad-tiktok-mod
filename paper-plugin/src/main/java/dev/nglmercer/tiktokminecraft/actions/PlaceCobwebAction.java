package dev.nglmercer.tiktokminecraft.actions;

import dev.nglmercer.tiktokminecraft.rules.ActionSpec;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** Places temporary cobweb blocks near the target and restores only our own blocks. */
public final class PlaceCobwebAction extends Action {
    private final Set<Block> placed = new LinkedHashSet<>();
    @Override public String type() { return "place-cobweb"; }

    @Override public void validate(ActionSpec spec, ActionContext context) throws ActionException {
        int count = optionalInt(spec, "count", 6);
        int restore = optionalInt(spec, "restore-seconds", 12);
        if (count < 1 || count > 32) throw new ActionException("count must be 1..32");
        if (restore < 1 || restore > 120) throw new ActionException("restore-seconds must be 1..120");
    }

    @Override public ActionResult execute(ActionSpec spec, ActionContext context) throws ActionException {
        List<Player> targets = context.targets(optional(spec, "target", null));
        if (targets.isEmpty()) return ActionResult.skipped("No online target");
        int count = optionalInt(spec, "count", 6);
        if (Boolean.parseBoolean(optional(spec, "multiply-by-repeat", "false"))
                && context.event() != null) {
            Object repeat = context.event().payload().get("gift.repeatCount");
            if (repeat instanceof Number) count = (int) Math.min(32L,
                    count * Math.max(1L, ((Number) repeat).longValue()));
        }
        int restore = optionalInt(spec, "restore-seconds", 12);
        Player player = targets.get(0);
        Location origin = player.getLocation();
        int applied = 0;
        for (int i = 0; i < count * 3 && applied < count; i++) {
            double phi = 2.399963229728653 * i;
            double radius = 1.0 + (i % 4);
            Location at = origin.clone().add(Math.cos(phi) * radius, i % 2, Math.sin(phi) * radius);
            if (!at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) continue;
            Block block = at.getBlock();
            if (block.getType() != Material.AIR || placed.contains(block)) continue;
            block.setType(Material.COBWEB, false);
            placed.add(block);
            applied++;
            context.plugin().getServer().getScheduler().runTaskLater(context.plugin(), () -> {
                if (placed.remove(block) && block.getType() == Material.COBWEB) {
                    block.setType(Material.AIR, false);
                }
            }, restore * 20L);
        }
        return ActionResult.ok("Placed " + applied + " temporary cobwebs");
    }

    /** Called on plugin shutdown to avoid leaving prank cobwebs behind. */
    public void restoreAll() {
        for (Block block : placed) {
            if (block.getType() == Material.COBWEB) block.setType(Material.AIR, false);
        }
        placed.clear();
    }
}
