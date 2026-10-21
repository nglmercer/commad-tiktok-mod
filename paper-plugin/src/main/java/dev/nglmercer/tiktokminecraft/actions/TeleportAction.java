package dev.nglmercer.tiktokminecraft.actions;

import dev.nglmercer.tiktokminecraft.rules.ActionSpec;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Teleports the target.
 *
 * <p>The destination must be explicit. A relative or implicit destination is
 * rejected, and the target is never moved to another world than the one it
 * is already in.</p>
 */
public final class TeleportAction extends Action {

    /** The greatest distance a target may be moved. */
    private static final double MAX_DISTANCE = 1000.0;

    @Override
    public String type() {
        return "teleport";
    }

    @Override
    public void validate(ActionSpec spec, ActionContext context) throws ActionException {
        String destination = require(spec, "destination");
        if (!isSupported(destination)) {
            throw new ActionException(
                    "'destination' must be 'x,y,z' or 'player:<name>'");
        }
    }

    @Override
    public ActionResult execute(ActionSpec spec, ActionContext context)
            throws ActionException {
        String destination = spec.string("destination", "").trim();
        List<Player> players = context.targets(optional(spec, "target", null));
        if (players.isEmpty()) {
            return ActionResult.skipped("No eligible target");
        }
        Location target = null;
        if (destination.toLowerCase(Locale.ROOT).startsWith("player:")) {
            Player other = Bukkit.getPlayerExact(destination.substring("player:".length()));
            if (other == null) {
                return ActionResult.skipped("The destination player is not online");
            }
            target = other.getLocation();
        } else {
            String[] parts = destination.split(",");
            if (parts.length != 3) {
                return ActionResult.failed("The destination is not a coordinate triple");
            }
            try {
                double x = Double.parseDouble(parts[0].trim());
                double y = Double.parseDouble(parts[1].trim());
                double z = Double.parseDouble(parts[2].trim());
                Player first = players.get(0);
                target = new Location(first.getWorld(), x, y, z);
            } catch (NumberFormatException failure) {
                return ActionResult.failed("The destination is not a coordinate triple");
            }
        }
        int moved = 0;
        for (Player player : players) {
            Location destination2 = target.clone();
            // A target is never moved out of the world it is already in.
            destination2.setWorld(player.getWorld());
            if (player.getWorld().equals(target.getWorld())
                    && player.getLocation().distance(target) > MAX_DISTANCE) {
                return ActionResult.failed("The destination is too far away");
            }
            if (player.teleport(destination2)) {
                moved++;
            }
        }
        return ActionResult.ok("Teleported " + moved + " player(s)");
    }

    private static boolean isSupported(String destination) {
        String text = destination.trim().toLowerCase(Locale.ROOT);
        if (text.startsWith("player:")) {
            return text.length() > "player:".length();
        }
        String[] parts = text.split(",");
        if (parts.length != 3) {
            return false;
        }
        for (String part : parts) {
            try {
                Double.parseDouble(part.trim());
            } catch (NumberFormatException failure) {
                return false;
            }
        }
        return true;
    }
}
