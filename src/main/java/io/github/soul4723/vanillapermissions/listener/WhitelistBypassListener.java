package io.github.soul4723.vanillapermissions.listener;

import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

public class WhitelistBypassListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (event.getResult() == PlayerLoginEvent.Result.KICK_WHITELIST) {
            if (PermissionManager.hasPermission(event.getPlayer(), "minecraft.bypass.whitelist")) {
                event.setResult(PlayerLoginEvent.Result.ALLOWED);
            }
        }

        if (event.getResult() == PlayerLoginEvent.Result.KICK_FULL) {
            if (PermissionManager.hasPermission(event.getPlayer(), "minecraft.bypass.player-limit")) {
                event.setResult(PlayerLoginEvent.Result.ALLOWED);
            }
        }
    }
}
