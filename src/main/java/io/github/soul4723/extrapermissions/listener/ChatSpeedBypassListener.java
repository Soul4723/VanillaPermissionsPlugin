package io.github.soul4723.extrapermissions.listener;

import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;

public class ChatSpeedBypassListener implements Listener {
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerKick(PlayerKickEvent event) {
        String reason = event.getReason().toLowerCase();
        if (reason.contains("chat") || reason.contains("spam") || reason.contains("too quickly")) {
            if (PermissionManager.hasPermission(event.getPlayer(), "minecraft.bypass.chat-speed")) {
                event.setCancelled(true);
            }
        }
    }
}
