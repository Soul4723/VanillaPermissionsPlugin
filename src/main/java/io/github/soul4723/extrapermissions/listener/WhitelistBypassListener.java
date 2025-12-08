package io.github.soul4723.extrapermissions.listener;

import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

public class WhitelistBypassListener implements Listener {
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (event.getResult() == PlayerLoginEvent.Result.KICK_WHITELIST) {
            if (event.getPlayer().hasPermission("minecraft.bypass.whitelist")) {
                event.setResult(PlayerLoginEvent.Result.ALLOWED);
            }
        }
        
        if (event.getResult() == PlayerLoginEvent.Result.KICK_FULL) {
            if (event.getPlayer().hasPermission("minecraft.bypass.player-limit")) {
                event.setResult(PlayerLoginEvent.Result.ALLOWED);
            }
        }
    }
}
