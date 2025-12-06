package io.github.soul4723.extrapermissions.listener;

import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class OperatorBlockListener implements Listener {
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!PermissionManager.hasPermission(event.getPlayer(), "minecraft.operator_block.command_block.place")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou don't have permission to place command blocks!");
            return;
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!PermissionManager.hasPermission(event.getPlayer(), "minecraft.operator_block.command_block.break")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou don't have permission to break command blocks!");
            return;
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        
        if (item != null && item.getType() == Material.COMMAND_BLOCK) {
            if (!PermissionManager.hasPermission(event.getPlayer(), "minecraft.operator_block.command_block.view")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cYou don't have permission to view command block contents!");
                return;
            }
            
            if (!PermissionManager.hasPermission(event.getPlayer(), "minecraft.operator_block.command_block.edit")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cYou don't have permission to edit command block contents!");
                return;
            }
        }
    }
}