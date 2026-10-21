package dev.nglmercer.tiktokminecraft.actions;

import dev.nglmercer.tiktokminecraft.rules.ActionSpec;
import java.util.List;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

/** Heals the target. */
public final class HealAction extends Action {

    @Override
    public String type() {
        return "heal";
    }

    @Override
    public void validate(ActionSpec spec, ActionContext context) throws ActionException {
        double amount = optionalDouble(spec, "amount", 4.0);
        if (amount <= 0.0 || amount > 1000.0) {
            throw new ActionException("'amount' must be greater than 0 and at most 1000");
        }
    }

    @Override
    public ActionResult execute(ActionSpec spec, ActionContext context)
            throws ActionException {
        double amount = optionalDouble(spec, "amount", 4.0);
        List<Player> players = context.targets(optional(spec, "target", null));
        if (players.isEmpty()) {
            return ActionResult.skipped("No eligible target");
        }
        for (Player player : players) {
            double maximum = player.getAttribute(Attribute.MAX_HEALTH) == null
                    ? 20.0
                    : player.getAttribute(Attribute.MAX_HEALTH).getValue();
            player.setHealth(Math.min(maximum, player.getHealth() + amount));
        }
        return ActionResult.ok("Healed " + players.size() + " player(s)");
    }
}
