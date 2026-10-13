package io.github.soul4723.extrapermissions.listener;

import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class SpawnProtectionListener implements Listener {
    
    @EventHandler(priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!event.isCancelled()) return;
        
        Player player = event.getPlayer();
        if (isInSpawnProtection(event.getBlock().getLocation())) {
            if (PermissionManager.hasPermissionOrParent(player, "minecraft.bypass.spawn-protection")) {
                event.setCancelled(false);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.LOW)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!event.isCancelled()) return;
        
        Player player = event.getPlayer();
        if (isInSpawnProtection(event.getBlock().getLocation())) {
            if (PermissionManager.hasPermissionOrParent(player, "minecraft.bypass.spawn-protection")) {
                event.setCancelled(false);
            }
        }
    }
    
    private boolean isInSpawnProtection(Location location) {
        int spawnProtection = Bukkit.getServer().getSpawnRadius();
        if (spawnProtection <= 0) return false;
        
        Location spawnLocation = location.getWorld().getSpawnLocation();
        // Use 2D distance (X/Z only) to match vanilla Minecraft spawn protection behavior
        double distance = Math.sqrt(
            Math.pow(location.getX() - spawnLocation.getX(), 2) + 
            Math.pow(location.getZ() - spawnLocation.getZ(), 2)
        );
        return distance <= spawnProtection;
    }
}
