package dev.nglmercer.tiktokminecraft.actions;

import dev.nglmercer.tiktokminecraft.rules.ActionSpec;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Gives an item to the target. */
public final class GiveItemAction extends Action {

    @Override
    public String type() {
        return "give-item";
    }

    @Override
    public void validate(ActionSpec spec, ActionContext context) throws ActionException {
        String material = require(spec, "material");
        if (Material.matchMaterial(material) == null) {
            throw new ActionException("Unknown material: " + material);
        }
        int count = optionalInt(spec, "count", 1);
        if (count < 1 || count > 64) {
            throw new ActionException("'count' must be between 1 and 64");
        }
    }

    @Override
    public ActionResult execute(ActionSpec spec, ActionContext context)
            throws ActionException {
        String material = spec.string("material", "");
        Material type = Material.matchMaterial(material);
        if (type == null) {
            return ActionResult.failed("Unknown material: " + material);
        }
        int count = optionalInt(spec, "count", 1);
        List<Player> players = context.targets(optional(spec, "target", null));
        if (players.isEmpty()) {
            return ActionResult.skipped("No eligible target");
        }
        int given = 0;
        for (Player player : players) {
            Map<Integer, ItemStack> leftover =
                    player.getInventory().addItem(new ItemStack(type, count));
            if (!leftover.isEmpty()) {
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
            given += count;
        }
        return ActionResult.ok("Gave " + given + " item(s)");
    }
}
