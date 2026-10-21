package dev.nglmercer.tiktokminecraft.actions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The actions a rule may use.
 *
 * <p>The registry is the single place that knows which action types exist.
 * A rule that names an unknown type is rejected when the rules are loaded,
 * not when a LIVE event finally arrives.</p>
 */
public final class ActionRegistry {

    private final Map<String, Action> actions = new LinkedHashMap<String, Action>();

    public ActionRegistry() {
        register(new BroadcastAction());
        register(new TitleAction());
        register(new SoundAction());
        register(new ParticleAction());
        register(new SpawnEntityAction());
        register(new GiveItemAction());
        register(new EffectAction());
        register(new HealAction());
        register(new TeleportAction());
        register(new FunctionAction());
        register(new CommandAction());
        register(new PrimeTntAction());
        register(new LightningAction());
        register(new PlaceCobwebAction());
        register(new RemoveItemAction());
        register(new ClearInventoryAction());
    }

    private void register(Action action) {
        actions.put(action.type().toLowerCase(Locale.ROOT), action);
    }

    /** @return the action, or null when the type is unknown */
    public Action get(String type) {
        if (type == null) {
            return null;
        }
        return actions.get(type.trim().toLowerCase(Locale.ROOT));
    }

    /** @return true when the type is known */
    public boolean contains(String type) {
        return get(type) != null;
    }

    /** The known action types. */
    public Set<String> types() {
        return Collections.unmodifiableSet(actions.keySet());
    }

    /** The known action types, as a sorted list. */
    public List<String> typeList() {
        List<String> types = new ArrayList<String>(actions.keySet());
        Collections.sort(types);
        return types;
    }
}
