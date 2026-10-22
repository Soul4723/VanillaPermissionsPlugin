package io.github.soul4723.vanillapermissions.listener;

import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

public class WhitelistBypassListener implements Listener {

    public static final String WHITELIST_PERMISSION = "minecraft.bypass.whitelist";
    public static final String PLAYER_LIMIT_PERMISSION = "minecraft.bypass.player-limit";

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        String permission;
        switch (event.getResult()) {
            case KICK_WHITELIST:
                permission = WHITELIST_PERMISSION;
                break;
            case KICK_FULL:
                permission = PLAYER_LIMIT_PERMISSION;
                break;
            default:
                permission = null;
        }
        if (permission != null && PermissionManager.hasPermissionAtLogin(event.getPlayer(), permission)) {
            event.allow();
        }
    }
}
