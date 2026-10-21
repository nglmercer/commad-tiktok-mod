package dev.nglmercer.tiktokminecraft.actions;

import dev.nglmercer.tiktokminecraft.chat.ChatFormatter;
import dev.nglmercer.tiktokminecraft.rules.ActionSpec;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Shows a message to every player. */
public final class BroadcastAction extends Action {

    @Override
    public String type() {
        return "broadcast";
    }

    @Override
    public void validate(ActionSpec spec, ActionContext context) throws ActionException {
        String message = require(spec, "message");
        if (message.length() > 512) {
            throw new ActionException("'message' must be at most 512 characters");
        }
    }

    @Override
    public ActionResult execute(ActionSpec spec, ActionContext context)
            throws ActionException {
        String message = ChatFormatter.safe(spec.string("message", ""));
        Component component = ChatFormatter.plain(message);
        List<Player> players = new ArrayList<Player>(Bukkit.getOnlinePlayers());
        for (Player player : players) {
            player.sendMessage(component);
        }
        return ActionResult.ok("Broadcast to " + players.size() + " player(s)");
    }
}
