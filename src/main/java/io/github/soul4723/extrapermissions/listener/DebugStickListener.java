package io.github.soul4723.extrapermissions.listener;

import io.github.soul4723.extrapermissions.util.PermissionManager;
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
        
        if (item == null || item.getType() != Material.DEBUG_STICK) {
            return;
        }
        
        if (event.getClickedBlock() != null) {
            String blockType = event.getClickedBlock().getType().name().toLowerCase();
            String permission = "minecraft.debug_stick.use.block." + blockType;
            
            if (!PermissionManager.hasPermission(event.getPlayer(), permission)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cYou don't have permission to use debug stick on " + blockType + "!");
            }
        }
    }
}