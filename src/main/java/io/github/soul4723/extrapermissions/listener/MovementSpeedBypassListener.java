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
        boolean hasGeneralBypass = PermissionManager.hasPermission(player, "minecraft.bypass.move-speed.player");
        boolean hasFlyingBypass = player.isFlying() && PermissionManager.hasPermission(player, "minecraft.bypass.move-speed.flying");
        boolean hasSprintingBypass = player.isSprinting() && PermissionManager.hasPermission(player, "minecraft.bypass.move-speed.sprinting");
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
    }
}