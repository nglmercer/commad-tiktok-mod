package dev.nglmercer.tiktokminecraft.actions;

import dev.nglmercer.tiktokminecraft.rules.ActionSpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/** Temporarily empties an inventory; returns items on timeout, logout or shutdown. */
public final class ClearInventoryAction extends Action implements Listener {
    private final Map<UUID, ItemStack[]> escrow = new HashMap<>();
    @Override public String type() { return "temporary-clear-inventory"; }

    @Override public void validate(ActionSpec spec, ActionContext context) throws ActionException {
        int seconds = optionalInt(spec, "restore-seconds", 8);
        if (seconds < 1 || seconds > 30) throw new ActionException("restore-seconds must be 1..30");
    }

    @Override public ActionResult execute(ActionSpec spec, ActionContext context) throws ActionException {
        List<Player> targets = context.targets(optional(spec, "target", null));
        if (targets.isEmpty()) return ActionResult.skipped("No online target");
        Player player = targets.get(0);
        UUID uuid = player.getUniqueId();
        if (escrow.containsKey(uuid)) return ActionResult.skipped("Target is already in temporary inventory lock");
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack[] saved = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            saved[i] = contents[i] == null ? null : contents[i].clone();
        }
        escrow.put(uuid, saved);
        player.getInventory().clear();
        int seconds = optionalInt(spec, "restore-seconds", 8);
        context.plugin().getServer().getScheduler().runTaskLater(context.plugin(),
                () -> restore(player), seconds * 20L);
        return ActionResult.ok("Temporarily hid the inventory for " + seconds + " seconds");
    }

    public void restore(Player player) {
        if (player == null) return;
        ItemStack[] saved = escrow.remove(player.getUniqueId());
        if (saved == null) return;
        // Returning items via addItem preserves new items acquired while the inventory was hidden.
        for (ItemStack stack : saved) {
            if (stack == null || stack.getType() == Material.AIR) continue;
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack.clone());
            for (ItemStack remaining : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), remaining);
            }
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { restore(event.getPlayer()); }

    /** Restore before Paper cancels this plugin's scheduled tasks. */
    public void restoreAll() {
        for (UUID id : List.copyOf(escrow.keySet())) restore(Bukkit.getPlayer(id));
    }
}
