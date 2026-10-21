package dev.nglmercer.tiktokminecraft.actions;

import dev.nglmercer.tiktokminecraft.rules.ActionSpec;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Removes a bounded item count from a target's inventory. */
public final class RemoveItemAction extends Action {
    @Override public String type() { return "remove-item"; }

    @Override public void validate(ActionSpec spec, ActionContext context) throws ActionException {
        Material material = Registry.MATERIAL.get(ParticleAction.key(require(spec, "material")));
        if (material == null || material.isAir()) throw new ActionException("Unknown material");
        int count = optionalInt(spec, "count", 1);
        if (count < 1 || count > 64) throw new ActionException("count must be 1..64");
    }

    @Override public ActionResult execute(ActionSpec spec, ActionContext context) throws ActionException {
        Material material = Registry.MATERIAL.get(ParticleAction.key(require(spec, "material")));
        if (material == null || material.isAir()) return ActionResult.failed("Unknown material");
        List<Player> targets = context.targets(optional(spec, "target", null));
        if (targets.isEmpty()) return ActionResult.skipped("No online target");
        int count = optionalInt(spec, "count", 1);
        Map<Integer, ItemStack> leftovers = targets.get(0).getInventory()
                .removeItem(new ItemStack(material, count));
        int missing = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        return ActionResult.ok("Removed " + (count - missing) + " " + material);
    }
}