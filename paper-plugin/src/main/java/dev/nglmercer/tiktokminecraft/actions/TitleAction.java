package dev.nglmercer.tiktokminecraft.actions;

import dev.nglmercer.tiktokminecraft.chat.ChatFormatter;
import dev.nglmercer.tiktokminecraft.rules.ActionSpec;
import dev.nglmercer.tiktokminecraft.rules.EventTemplate;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.entity.Player;

/** Shows a title and a subtitle. */
public final class TitleAction extends Action {

    @Override
    public String type() {
        return "title";
    }

    @Override
    public void validate(ActionSpec spec, ActionContext context) throws ActionException {
        require(spec, "title");
        String subtitle = optional(spec, "subtitle", "");
        if (subtitle.length() > 512) {
            throw new ActionException("'subtitle' must be at most 512 characters");
        }
        int fadeIn = optionalInt(spec, "fade-in", 10);
        int stay = optionalInt(spec, "stay", 60);
        int fadeOut = optionalInt(spec, "fade-out", 20);
        if (fadeIn < 0 || fadeIn > 1200 || stay < 0 || stay > 12000 || fadeOut < 0
                || fadeOut > 1200) {
            throw new ActionException(
                    "title timings must be between 0 and 1200 (stay up to 12000) ticks");
        }
    }

    @Override
    public ActionResult execute(ActionSpec spec, ActionContext context)
            throws ActionException {
        String target = optional(spec, "target", null);
        List<Player> players = context.targets(target);
        if (players.isEmpty()) {
            return ActionResult.skipped("No eligible target");
        }
        Component title = ChatFormatter.plain(ChatFormatter.truncate(
                EventTemplate.render(spec.string("title", ""), context.event()), 256));
        Component subtitle = ChatFormatter.plain(ChatFormatter.truncate(
                EventTemplate.render(spec.string("subtitle", ""), context.event()), 512));
        Times times =
                Times.times(
                        java.time.Duration.ofMillis(optionalInt(spec, "fade-in", 10) * 50L),
                        java.time.Duration.ofMillis(optionalInt(spec, "stay", 60) * 50L),
                        java.time.Duration.ofMillis(optionalInt(spec, "fade-out", 20) * 50L));
        for (Player player : players) {
            player.showTitle(Title.title(title, subtitle, times));
        }
        return ActionResult.ok("Title shown to " + players.size() + " player(s)");
    }
}
