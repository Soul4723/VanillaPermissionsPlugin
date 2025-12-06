package io.github.soul4723.extrapermissions.listener;

import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

public class WhitelistBypassListener implements Listener {
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (event.getResult() == PlayerLoginEvent.Result.KICK_WHITELIST) {
            if (PermissionManager.hasPermission(event.getPlayer(), "minecraft.bypass.whitelist")) {
                event.setResult(PlayerLoginEvent.Result.ALLOWED);
            }
        }
    }
}