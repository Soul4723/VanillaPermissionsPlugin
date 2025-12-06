package io.github.soul4723.extrapermissions.listener;

import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerVelocityEvent;

public class MovementSpeedBypassListener implements Listener {
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Bypass movement speed limits for players with permission
        if (PermissionManager.hasPermission(player, "minecraft.bypass.move-speed.player")) {
            // Allow faster movement by not restricting move events
            // This allows players to move at speeds beyond normal limits
            // The actual speed modification would be done by other plugins or server settings
            return;
        }
        
        // Additional bypass for flying speed
        if (player.isFlying() && PermissionManager.hasPermission(player, "minecraft.bypass.move-speed.flying")) {
            return;
        }
        
        // Additional bypass for sprinting speed
        if (player.isSprinting() && PermissionManager.hasPermission(player, "minecraft.bypass.move-speed.sprinting")) {
            return;
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        Player player = event.getPlayer();
        
        // Bypass velocity modifications for players with permission
        if (PermissionManager.hasPermission(player, "minecraft.bypass.move-speed.velocity")) {
            // Allow velocity changes without restrictions
            return;
        }
    }
}