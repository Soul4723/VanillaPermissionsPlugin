package io.github.soul4723.extrapermissions.listener;

import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class OperatorBlockListener implements Listener {
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Material blockType = event.getBlock().getType();
        if (isOperatorBlock(blockType)) {
            String permission = getOperatorBlockPermission(blockType, "place");
            if (!PermissionManager.hasPermissionOrParent(event.getPlayer(), permission)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cYou don't have permission to place " + blockType.name().toLowerCase() + "!");
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Material blockType = event.getBlock().getType();
        if (isOperatorBlock(blockType)) {
            String permission = getOperatorBlockPermission(blockType, "break");
            if (!PermissionManager.hasPermissionOrParent(event.getPlayer(), permission)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cYou don't have permission to break " + blockType.name().toLowerCase() + "!");
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        
        Material blockType = event.getClickedBlock().getType();
        if (isOperatorBlock(blockType)) {
            String viewPermission = getOperatorBlockPermission(blockType, "view");
            if (!PermissionManager.hasPermissionOrParent(event.getPlayer(), viewPermission)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cYou don't have permission to view " + blockType.name().toLowerCase() + " contents!");
            }
        }
    }
    
    private boolean isOperatorBlock(Material material) {
        return material == Material.COMMAND_BLOCK ||
               material == Material.REPEATING_COMMAND_BLOCK ||
               material == Material.CHAIN_COMMAND_BLOCK ||
               material == Material.JIGSAW ||
               material == Material.STRUCTURE_BLOCK;
    }
    
    private String getOperatorBlockPermission(Material material, String action) {
        String blockType;
        if (material == Material.COMMAND_BLOCK || 
            material == Material.REPEATING_COMMAND_BLOCK || 
            material == Material.CHAIN_COMMAND_BLOCK) {
            blockType = "command_block";
        } else if (material == Material.JIGSAW) {
            blockType = "jigsaw";
        } else if (material == Material.STRUCTURE_BLOCK) {
            blockType = "structure_block";
        } else {
            blockType = "unknown";
        }
        
        return "minecraft.operator_block." + blockType + "." + action;
    }
}