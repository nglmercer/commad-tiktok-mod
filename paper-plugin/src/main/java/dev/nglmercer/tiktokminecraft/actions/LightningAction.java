package dev.nglmercer.tiktokminecraft.actions;

import dev.nglmercer.tiktokminecraft.rules.ActionSpec;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Flash lightning effects around a player without setting fires or dealing damage. */
public final class LightningAction extends Action {
    @Override public String type() { return "lightning-effect"; }

    @Override public void validate(ActionSpec spec, ActionContext context) throws ActionException {
        int count = optionalInt(spec, "count", 1);
        if (count < 1 || count > 32) throw new ActionException("count must be 1..32");
    }

    @Override public ActionResult execute(ActionSpec spec, ActionContext context) throws ActionException {
        List<Player> targets = context.targets(optional(spec, "target", null));
        if (targets.isEmpty()) return ActionResult.skipped("No online target");
        int count = optionalInt(spec, "count", 1);
        if (Boolean.parseBoolean(optional(spec, "multiply-by-repeat", "false"))
                && context.event() != null) {
            Object repeat = context.event().payload().get("gift.repeatCount");
            if (repeat instanceof Number) count = (int) Math.min(32L,
                    count * Math.max(1L, ((Number) repeat).longValue()));
        }
        Location center = targets.get(0).getLocation();
        for (int i = 0; i < count; i++) {
            double theta = i * 2.399963229728653;
            Location at = center.clone().add(Math.cos(theta) * 2, 0, Math.sin(theta) * 2);
            if (at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
                at.getWorld().strikeLightningEffect(at);
            }
        }
        return ActionResult.ok("Displayed " + count + " lightning effects");
    }
}
