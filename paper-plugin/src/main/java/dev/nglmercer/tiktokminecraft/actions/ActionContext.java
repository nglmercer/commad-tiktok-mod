package dev.nglmercer.tiktokminecraft.actions;

import dev.nglmercer.tiktokminecraft.config.PluginConfig;
import dev.nglmercer.tiktokminecraft.event.TikTokEventContext;
import dev.nglmercer.tiktokminecraft.functions.FunctionEngineService;
import dev.nglmercer.tiktokminecraft.log.PluginLog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Everything an action needs to validate and run.
 *
 * <p>The context also carries the budget: an event may only run a bounded
 * number of actions and spawn a bounded number of entities, so a burst of
 * LIVE events cannot flood the world.</p>
 */
public final class ActionContext {

    private final Plugin plugin;
    private final PluginConfig config;
    private final FunctionEngineService functions;
    private final PluginLog log;
    private final TikTokEventContext event;
    private final String ruleId;
    private final EntityRateLimiter entityLimiter;

    private int actionsRun;
    private int entitiesThisEvent;

    public ActionContext(
            Plugin plugin,
            PluginConfig config,
            FunctionEngineService functions,
            PluginLog log,
            TikTokEventContext event,
            String ruleId,
            EntityRateLimiter entityLimiter) {
        this.plugin = plugin;
        this.config = config;
        this.functions = functions;
        this.log = log;
        this.event = event;
        this.ruleId = ruleId;
        this.entityLimiter = entityLimiter;
    }

    public Plugin plugin() {
        return plugin;
    }

    public PluginConfig config() {
        return config;
    }

    public FunctionEngineService functions() {
        return functions;
    }

    public PluginLog log() {
        return log;
    }

    /** The event that triggered the action. */
    public TikTokEventContext event() {
        return event;
    }

    /** The rule that triggered the action. */
    public String ruleId() {
        return ruleId;
    }

    /** The shared, per-minute entity limiter. */
    public EntityRateLimiter entityLimiter() {
        return entityLimiter;
    }

    /**
     * Takes one action slot.
     *
     * @return false when the event already ran the maximum number of actions
     */
    public boolean consumeActionSlot() {
        if (actionsRun >= config.maxActionsPerEvent()) {
            return false;
        }
        actionsRun++;
        return true;
    }

    /**
     * Takes one entity slot.
     *
     * @return false when the per-event or the per-minute cap is reached
     */
    public boolean consumeEntitySlot() {
        if (entitiesThisEvent >= config.maxEntitiesPerEvent()) {
            return false;
        }
        if (entityLimiter != null && !entityLimiter.tryAcquire()) {
            return false;
        }
        entitiesThisEvent++;
        return true;
    }

    /** The players an action applies to. */
    public List<Player> targets(String spec) {
        return TargetResolver.resolve(spec, config.targetPolicy(), origin());
    }

    /** The location relative actions are resolved against. */
    public Location origin() {
        List<Player> players = TargetResolver.resolve(
                null, config.targetPolicy(), null);
        if (players.isEmpty()) {
            return null;
        }
        return players.get(0).getLocation();
    }

    /** The UUID of the first eligible target, or null. */
    public UUID primaryTarget() {
        List<Player> players = targets(null);
        return players.isEmpty() ? null : players.get(0).getUniqueId();
    }

    /** The viewers that produced the event. */
    public List<String> viewerNames() {
        List<String> names = new ArrayList<String>();
        if (event == null) {
            return names;
        }
        if (!event.nickname().isEmpty()) {
            names.add(event.nickname());
        }
        if (!event.uniqueId().isEmpty()) {
            names.add(event.uniqueId());
        }
        return Collections.unmodifiableList(names);
    }
}
