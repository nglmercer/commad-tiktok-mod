package dev.nglmercer.tiktokminecraft.actions;

import dev.nglmercer.tiktokminecraft.rules.ActionSpec;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Removes a limited item quantity; no server console commands needed. */
public final class RemoveItemAction extends Action {
    @Override public String type() { return "remove-item"; }

    @Override public void validate(ActionSpec spec, ActionContext context) throws ActionException {
        Material material = Material.matchMaterial(require(spec, "material"));
        if (material == null || (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR)) throw new ActionException("Unknown item material");
        int count = optionalInt(spec, "count", 1);
        if (count < 1 || count > 64) throw new ActionException("count must be 1..64");
    }

    @Override public ActionResult execute(ActionSpec spec, ActionContext context) throws ActionException {
        Material material = Material.matchMaterial(require(spec, "material"));
        if (material == null || (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR)) return ActionResult.failed("Unknown item material");
        List<Player> targets = context.targets(optional(spec, "target", null));
        if (targets.isEmpty()) return ActionResult.skipped("No online target");
        int count = optionalInt(spec, "count", 1);
        if (Boolean.parseBoolean(optional(spec, "multiply-by-repeat", "false"))
                && context.event() != null) {
            Object repeat = context.event().payload().get("gift.repeatCount");
            if (repeat instanceof Number) count = (int) Math.min(64L,
                    count * Math.max(1L, ((Number) repeat).longValue()));
        }
        Map<Integer, ItemStack> leftovers = targets.get(0).getInventory()
                .removeItem(new ItemStack(material, count));
        int missing = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        return ActionResult.ok("Removed " + (count - missing) + " " + material);
    }
}
