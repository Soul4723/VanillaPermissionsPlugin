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
import org.bukkit.event.player.PlayerInteractEvent;

public class SpawnProtectionListener implements Listener {
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (isInSpawnProtection(event.getBlock().getLocation())) {
            if (!PermissionManager.hasPermission(player, "minecraft.bypass.spawn-protection")) {
                event.setCancelled(true);
                player.sendMessage("§cYou cannot break blocks in spawn protection!");
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (isInSpawnProtection(event.getBlock().getLocation())) {
            if (!PermissionManager.hasPermission(player, "minecraft.bypass.spawn-protection")) {
                event.setCancelled(true);
                player.sendMessage("§cYou cannot place blocks in spawn protection!");
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getClickedBlock() != null && isInSpawnProtection(event.getClickedBlock().getLocation())) {
            if (!PermissionManager.hasPermission(player, "minecraft.bypass.spawn-protection")) {
                event.setCancelled(true);
                player.sendMessage("§cYou cannot interact with blocks in spawn protection!");
            }
        }
    }
    
    private boolean isInSpawnProtection(Location location) {
        int spawnProtection = Bukkit.getServer().getSpawnRadius();
        if (spawnProtection <= 0) return false;
        
        Location spawnLocation = location.getWorld().getSpawnLocation();
        double distance = Math.sqrt(
            Math.pow(location.getX() - spawnLocation.getX(), 2) + 
            Math.pow(location.getZ() - spawnLocation.getZ(), 2)
        );
        return distance <= spawnProtection;
    }
}
