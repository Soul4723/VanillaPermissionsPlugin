package io.github.soul4723.extrapermissions.listener;

import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;

public class MoveSpeedBypassListener implements Listener {

    private static final String PLAYER_SPEED_PERMISSION = "minecraft.bypass.move-speed.player";
    private static final String VEHICLE_SPEED_PREFIX = "minecraft.bypass.move-speed.vehicle.";

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        String kickReason = event.getReason().toLowerCase();

        if (isMovementSpeedKick(kickReason)) {
            if (PermissionManager.hasPermission(player, PLAYER_SPEED_PERMISSION)) {
                event.setCancelled(true);
                return;
            }

            Entity vehicle = player.getVehicle();
            if (vehicle != null) {
                String vehicleType = vehicle.getType().toString().toLowerCase();
                String vehiclePermission = VEHICLE_SPEED_PREFIX + vehicleType;
                if (PermissionManager.hasPermission(player, vehiclePermission)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    private boolean isMovementSpeedKick(String kickReason) {
        return kickReason.contains("moved too quickly") ||
               kickReason.contains("move") ||
               kickReason.contains("speed") ||
               kickReason.contains("flying") ||
               kickReason.contains("high");
    }
}
