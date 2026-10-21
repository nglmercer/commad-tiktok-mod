package dev.nglmercer.tiktokminecraft.actions;

import dev.nglmercer.tiktokminecraft.rules.ActionSpec;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.scheduler.BukkitRunnable;

/** Minecraft-only primed TNT in short bursts; huge requests are capped. */
public final class PrimeTntAction extends Action {
    @Override public String type() { return "prime-tnt"; }

    @Override public void validate(ActionSpec spec, ActionContext context) throws ActionException {
        int count = optionalInt(spec, "count", 1);
        int fuse = optionalInt(spec, "fuse-ticks", 80);
        int burst = optionalInt(spec, "burst-size", 6);
        if (count < 1 || count > 30000) throw new ActionException("count must be 1..30000");
        if (fuse < 20 || fuse > 240) throw new ActionException("fuse-ticks must be 20..240");
        if (burst < 1 || burst > 12) throw new ActionException("burst-size must be 1..12");
    }

    @Override public ActionResult execute(ActionSpec spec, ActionContext context) throws ActionException {
        int requested = optionalInt(spec, "count", 1);
        if (Boolean.parseBoolean(optional(spec, "multiply-by-repeat", "false"))
                && context.event() != null) {
            Object repeat = context.event().payload().get("gift.repeatCount");
            if (repeat instanceof Number) requested = (int) Math.min(30000L,
                    (long) requested * Math.max(1L, ((Number) repeat).longValue()));
        }
        // 30,000 TNT is a joke preset, not an instruction to allocate 30,000 entities.
        int allowed = Math.min(requested, Math.min(128, context.config().maxEntitiesPerEvent()));
        List<Player> players = context.targets(optional(spec, "target", null));
        if (players.isEmpty()) return ActionResult.skipped("No online target for TNT");
        UUID target = players.get(0).getUniqueId();
        int fuseTicks = optionalInt(spec, "fuse-ticks", 80);
        int burstSize = optionalInt(spec, "burst-size", 6);
        if (allowed == 0) return ActionResult.skipped("Entity budget is zero");
        new BukkitRunnable() {
            int index;
            @Override public void run() {
                if (!context.plugin().isEnabled()) { cancel(); return; }
                Player player = Bukkit.getPlayer(target);
                if (player == null || !player.isOnline()) { cancel(); return; }
                int attempted = 0;
                while (attempted++ < burstSize && index < allowed) {
                    if (!context.consumeEntitySlot()) { cancel(); return; }
                    Location base = player.getLocation();
                    if (!base.getWorld().isChunkLoaded(base.getBlockX() >> 4, base.getBlockZ() >> 4)) {
                        cancel(); return;
                    }
                    double theta = (index + 1) * 2.399963229728653;
                    double radius = 2.5 + index % 3;
                    Location at = base.clone().add(Math.cos(theta) * radius, 1.0, Math.sin(theta) * radius);
                    index++;
                    if (!at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) continue;
                    try {
                        TNTPrimed tnt = at.getWorld().spawn(at, TNTPrimed.class);
                        tnt.setFuseTicks(fuseTicks);
                    } catch (RuntimeException failure) {
                        cancel(); return;
                    }
                }
                if (index >= allowed) cancel();
            }
        }.runTaskTimer(context.plugin(), 1L, 1L);
        return ActionResult.ok("Scheduled up to " + allowed + " primed TNT (requested "
                + requested + "; bounded bursts and shared per-minute budget)");
    }
}
