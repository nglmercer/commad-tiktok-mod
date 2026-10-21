package dev.nglmercer.tiktokminecraft.actions;

import dev.nglmercer.tiktokminecraft.rules.ActionSpec;
import java.util.List;
import java.util.Locale;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Applies a potion effect to the target. */
public final class EffectAction extends Action {

    @Override
    public String type() {
        return "effect";
    }

    @Override
    public void validate(ActionSpec spec, ActionContext context) throws ActionException {
        String effect = require(spec, "effect");
        if (!effect.matches("[A-Za-z0-9_.:/-]{1,128}")) {
            throw new ActionException("'effect' is not a valid potion effect name");
        }
        // Whether the effect exists in this Minecraft version is checked when
        // the action runs.
        int duration = optionalInt(spec, "duration", 5);
        if (duration < 1 || duration > 3600) {
            throw new ActionException("'duration' must be between 1 and 3600 seconds");
        }
        int amplifier = optionalInt(spec, "amplifier", 0);
        if (amplifier < 0 || amplifier > 255) {
            throw new ActionException("'amplifier' must be between 0 and 255");
        }
    }

    @Override
    public ActionResult execute(ActionSpec spec, ActionContext context)
            throws ActionException {
        PotionEffectType type = resolve(spec.string("effect", ""));
        if (type == null) {
            return ActionResult.failed("Unknown potion effect");
        }
        List<Player> players = context.targets(optional(spec, "target", null));
        if (players.isEmpty()) {
            return ActionResult.skipped("No eligible target");
        }
        int duration = optionalInt(spec, "duration", 5);
        int amplifier = optionalInt(spec, "amplifier", 0);
        PotionEffect potion =
                new PotionEffect(type, duration * 20, amplifier, true, false, true);
        for (Player player : players) {
            player.addPotionEffect(potion);
        }
        return ActionResult.ok("Effect applied to " + players.size() + " player(s)");
    }

    private static PotionEffectType resolve(String name) {
        String text = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return null;
        }
        if (text.contains(":")) {
            String key = text.substring(text.indexOf(':') + 1);
            return Registry.MOB_EFFECT.get(ParticleAction.key(key));
        }
        return PotionEffectType.getByName(text.toUpperCase(Locale.ROOT));
    }
}
