package io.github.soul4723.vanillapermissions.brigadier.listener;

import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Lets the permissions plugin take debug stick use away, per block like upstream. Vanilla still requires op and creative
 * mode (or Paper's own minecraft.debugstick permissions), so an unset node keeps vanilla behaviour.
 */
public class DebugStickListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractWithDebugStick(PlayerInteractEvent event) {
        // The event's item is the stack in the interacting hand, so off-hand use is covered too.
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.DEBUG_STICK || event.getClickedBlock() == null) return;

        if (!PermissionManager.hasPermissionOrFallback(event.getPlayer(), permission(event.getClickedBlock().getType()), true)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou do not have permission to use the debug stick on this block.");
        }
    }

    /** {@code minecraft.debug_stick.use.minecraft.oak_trapdoor} for {@code minecraft:oak_trapdoor}. */
    static String permission(Material block) {
        return "minecraft.debug_stick.use." + block.getKey().getNamespace() + "." + block.getKey().getKey();
    }
}
