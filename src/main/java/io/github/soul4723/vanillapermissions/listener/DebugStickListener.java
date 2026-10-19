package io.github.soul4723.vanillapermissions.listener;

import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/** Lets LuckPerms take debug stick use away; vanilla still requires op and creative mode to use it. */
public class DebugStickListener implements Listener {

    static final String PERMISSION = "minecraft.debug_stick.use.block";

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractWithDebugStick(PlayerInteractEvent event) {
        // The event's item is the stack in the interacting hand, so off-hand use is covered too.
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.DEBUG_STICK || event.getClickedBlock() == null) return;

        if (!PermissionManager.hasPermissionOrFallback(event.getPlayer(), PERMISSION, true)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou do not have permission to use the debug stick.");
        }
    }
}
