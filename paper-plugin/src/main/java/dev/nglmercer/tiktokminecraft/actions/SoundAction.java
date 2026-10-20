package dev.nglmercer.tiktokminecraft.actions;

import dev.nglmercer.tiktokminecraft.rules.ActionSpec;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Plays a sound. */
public final class SoundAction extends Action {

    @Override
    public String type() {
        return "sound";
    }

    @Override
    public void validate(ActionSpec spec, ActionContext context) throws ActionException {
        String sound = require(spec, "sound");
        if (!sound.matches("[A-Za-z0-9_.:/-]{1,128}")) {
            throw new ActionException("'sound' is not a valid sound name");
        }
        double volume = optionalDouble(spec, "volume", 1.0);
        double pitch = optionalDouble(spec, "pitch", 1.0);
        if (volume < 0.0 || volume > 16.0) {
            throw new ActionException("'volume' must be between 0 and 16");
        }
        if (pitch < 0.0 || pitch > 2.0) {
            throw new ActionException("'pitch' must be between 0 and 2");
        }
    }

    @Override
    public ActionResult execute(ActionSpec spec, ActionContext context)
            throws ActionException {
        String sound = spec.string("sound", "");
        String target = optional(spec, "target", null);
        List<Player> players = context.targets(target);
        if (players.isEmpty()) {
            return ActionResult.skipped("No eligible target");
        }
        float volume = (float) optionalDouble(spec, "volume", 1.0);
        float pitch = (float) optionalDouble(spec, "pitch", 1.0);
        for (Player player : players) {
            Location location = player.getLocation();
            player.playSound(location, sound, volume, pitch);
        }
        return ActionResult.ok("Sound played to " + players.size() + " player(s)");
    }
}
