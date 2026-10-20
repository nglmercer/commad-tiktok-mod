package dev.nglmercer.tiktokminecraft.actions;

import dev.nglmercer.tiktokminecraft.rules.ActionSpec;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.entity.Player;

/** Spawns particles around the target. */
public final class ParticleAction extends Action {

    @Override
    public String type() {
        return "particle";
    }

    @Override
    public void validate(ActionSpec spec, ActionContext context) throws ActionException {
        String particle = require(spec, "particle");
        NamespacedKey key = key(particle);
        if (key == null) {
            throw new ActionException("'particle' is not a valid particle name");
        }
        if (Registry.PARTICLE_TYPE.get(key) == null) {
            throw new ActionException("Unknown particle: " + particle);
        }
        int count = optionalInt(spec, "count", 20);
        if (count < 1 || count > 10000) {
            throw new ActionException("'count' must be between 1 and 10000");
        }
    }

    @Override
    public ActionResult execute(ActionSpec spec, ActionContext context)
            throws ActionException {
        NamespacedKey key = key(spec.string("particle", ""));
        if (key == null) {
            return ActionResult.failed("Invalid particle");
        }
        Particle particle = Registry.PARTICLE_TYPE.get(key);
        if (particle == null) {
            return ActionResult.failed("Unknown particle");
        }
        String target = optional(spec, "target", null);
        List<Player> players = context.targets(target);
        if (players.isEmpty()) {
            return ActionResult.skipped("No eligible target");
        }
        int count = optionalInt(spec, "count", 20);
        for (Player player : players) {
            Location location = player.getLocation();
            location.getWorld().spawnParticle(particle, location, count);
        }
        return ActionResult.ok("Particles spawned for " + players.size() + " player(s)");
    }

    /** Builds a namespaced key, defaulting to the minecraft namespace. */
    static NamespacedKey key(String name) {
        String text = name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT);
        if (text.isEmpty()) {
            return null;
        }
        if (text.contains(":")) {
            String[] parts = text.split(":", 2);
            if (parts[0].isEmpty() || parts[1].isEmpty()) {
                return null;
            }
            return NamespacedKey.fromString(parts[0] + ":" + parts[1]);
        }
        return NamespacedKey.minecraft(text);
    }
}
