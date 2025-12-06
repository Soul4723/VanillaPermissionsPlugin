package io.github.soul4723.extrapermissions.listener;

import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class AdminBroadcastListener implements Listener {
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        
        if (event.isCancelled()) {
            return;
        }
        
        for (Player online : player.getServer().getOnlinePlayers()) {
            if (online != player && PermissionManager.hasPermission(online, "minecraft.adminbroadcast.receive")) {
                online.sendMessage("§7" + player.getName() + " ran: " + event.getMessage());
            }
        }
    }
}