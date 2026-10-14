package io.github.soul4723.vanillapermissions.listener;

import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class DebugStickListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractWithDebugStick(PlayerInteractEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (item.getType() != Material.DEBUG_STICK) return;

        if (event.getClickedBlock() != null) {
            if (!PermissionManager.hasPermission(event.getPlayer(), "minecraft.debug_stick.use.block")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cYou don't have permission to use debug stick!");
            }
        }
    }
}
