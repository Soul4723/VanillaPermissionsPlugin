package io.github.soul4723.vanillapermissions.listener;

import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

public class WhitelistBypassListener implements Listener {

    static final String WHITELIST_PERMISSION = "minecraft.bypass.whitelist";
    static final String PLAYER_LIMIT_PERMISSION = "minecraft.bypass.player-limit";

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        String permission = switch (event.getResult()) {
            case KICK_WHITELIST -> WHITELIST_PERMISSION;
            case KICK_FULL -> PLAYER_LIMIT_PERMISSION;
            default -> null;
        };
        if (permission != null && PermissionManager.hasPermissionAtLogin(event.getPlayer(), permission)) {
            event.allow();
        }
    }
}
