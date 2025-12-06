package io.github.soul4723.extrapermissions.listener;

import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerVelocityEvent;

/**
 * Listener for handling movement speed bypass permissions.
 * Allows players with appropriate permissions to bypass movement speed restrictions.
 */
public class MovementSpeedBypassListener implements Listener {
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        // Check for any movement speed bypass permission
        boolean hasGeneralBypass = PermissionManager.hasPermission(player, "minecraft.bypass.move-speed.player");
        boolean hasFlyingBypass = player.isFlying() && PermissionManager.hasPermission(player, "minecraft.bypass.move-speed.flying");
        boolean hasSprintingBypass = player.isSprinting() && PermissionManager.hasPermission(player, "minecraft.bypass.move-speed.sprinting");
        
        // If player has any bypass permission, allow movement
        if (hasGeneralBypass || hasFlyingBypass || hasSprintingBypass) {
            return;
        }
        
        // Note: Actual speed limiting logic would be implemented here
        // This event currently serves as a permission hook
    }
    
    /**
     * Handle velocity events for players with bypass permissions
     * @param event The velocity event
     */
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