package dev.nglmercer.tiktokminecraft.actions;

import dev.nglmercer.tiktokminecraft.rules.ActionSpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/** Lightning bursts that follow a player's UUID across death and respawn. */
public final class LightningAction extends Action {
    private final String name;
    private final boolean defaultDamage;
    private final Map<UUID, Storm> storms = new LinkedHashMap<>();
    private BukkitRunnable ticker;

    /** Retains the legacy cosmetic action and its one-shot default. */
    public LightningAction() { this("lightning-effect", false); }

    public LightningAction(String name, boolean defaultDamage) {
        this.name = name;
        this.defaultDamage = defaultDamage;
    }

    @Override public String type() { return name; }

    @Override public void validate(ActionSpec spec, ActionContext context) throws ActionException {
        int count = optionalInt(spec, "count", 1);
        int seconds = optionalInt(spec, "duration-seconds", defaultDamage ? 60 : 0);
        int interval = optionalInt(spec, "interval-ticks", 20);
        if (count < 1 || count > 32) throw new ActionException("count must be 1..32");
        if (seconds < 0 || seconds > 3600)
            throw new ActionException("duration-seconds must be 0..3600");
        if (interval < 10 || interval > 1200)
            throw new ActionException("interval-ticks must be 10..1200");
        for (String key : List.of("damage", "multiply-by-repeat")) {
            if (spec.parameter(key) != null && !(spec.parameter(key) instanceof Boolean))
                throw new ActionException(key + " must be a boolean");
        }
    }

    @Override public ActionResult execute(ActionSpec spec, ActionContext context) throws ActionException {
        validate(spec, context);
        List<Player> targets = context.targets(optional(spec, "target", null));
        if (targets.isEmpty()) return ActionResult.skipped("No online target");
        int count = optionalInt(spec, "count", 1);
        if (Boolean.TRUE.equals(spec.parameter("multiply-by-repeat")) && context.event() != null) {
            Object repeat = context.event().payload().get("gift.repeatCount");
            if (repeat instanceof Number) count = (int) Math.min(32L,
                    count * Math.max(1L, ((Number) repeat).longValue()));
        }
        count = Math.min(count, context.config().maxEntitiesPerEvent());
        int ticks = optionalInt(spec, "duration-seconds", defaultDamage ? 60 : 0) * 20;
        int interval = optionalInt(spec, "interval-ticks", 20);
        boolean damage = spec.parameter("damage") == null ? defaultDamage
                : Boolean.TRUE.equals(spec.parameter("damage"));
        // As with the legacy action, target the first resolved player only.
        Player target = targets.get(0);
        UUID id = target.getUniqueId();
        if (ticks > 0 && !storms.containsKey(id) && storms.size() >= 128)
            return ActionResult.skipped("Lightning storm capacity reached");
        Storm storm = new Storm(count, damage, new Timeline(ticks, interval));
        // A repeat donation refreshes this player's storm rather than creating
        // another task or bypassing its strike interval with an immediate hit.
        boolean refresh = storms.containsKey(id);
        if ((!refresh || ticks == 0) && !target.isDead()) strike(target, storm, context.entityLimiter());
        if (ticks == 0) return ActionResult.ok("Lightning burst triggered");
        storms.put(id, storm);
        if (ticker == null) {
            ticker = new BukkitRunnable() {
                @Override public void run() {
                    var iterator = storms.entrySet().iterator();
                    int budget = 32;
                    while (iterator.hasNext()) {
                        var entry = iterator.next();
                        Storm active = entry.getValue();
                        boolean due = active.timeline.tick();
                        if (active.timeline.expired()) { iterator.remove(); continue; }
                        if (!due || budget <= 0) continue;
                        // Resolve again on every burst: never retain a dead
                        // Player instance, old world, or death location.
                        Player player = context.plugin().getServer().getPlayer(entry.getKey());
                        if (player == null || !player.isOnline() || player.isDead()) continue;
                        budget -= strike(player, active, context.entityLimiter(), budget);
                    }
                    if (storms.isEmpty()) { cancel(); ticker = null; }
                }
            };
            ticker.runTaskTimer(context.plugin(), 1L, 1L);
        }
        return ActionResult.ok("Lightning storm scheduled for " + ticks / 20 + " seconds");
    }

    private static int strike(Player player, Storm storm, EntityRateLimiter limiter) {
        return strike(player, storm, limiter, 32);
    }

    private static int strike(Player player, Storm storm, EntityRateLimiter limiter, int budget) {
        Location center = player.getLocation();
        int spawned = 0;
        for (int i = 0; i < Math.min(storm.count, budget); i++) {
            double theta = i * 2.399963229728653;
            Location at = storm.damage ? center
                    : center.clone().add(Math.cos(theta) * 2, 0, Math.sin(theta) * 2);
            if (!at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) continue;
            if (limiter != null && !limiter.tryAcquire()) break;
            if (storm.damage) at.getWorld().strikeLightning(at);
            else at.getWorld().strikeLightningEffect(at);
            spawned++;
        }
        return spawned;
    }

    /** Cancel storms and release UUIDs on plugin shutdown. */
    public void cancelAll() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        storms.clear();
    }

    private record Storm(int count, boolean damage, Timeline timeline) { }

    /** Server-tick lifetime; death/offline time passes without cancelling it. */
    static final class Timeline {
        private int remaining;
        private final int interval;
        private int elapsed;
        Timeline(int ticks, int interval) { this.remaining = ticks; this.interval = interval; }
        boolean tick() {
            if (remaining > 0) remaining--;
            return !expired() && ++elapsed % interval == 0;
        }
        boolean expired() { return remaining <= 0; }
    }
}
