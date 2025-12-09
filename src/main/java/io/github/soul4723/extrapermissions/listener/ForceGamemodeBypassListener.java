package io.github.soul4723.extrapermissions.listener;

import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.entity.Player;

public class ForceGamemodeBypassListener implements Listener {

    private static final String BYPASS_PERMISSION = "minecraft.bypass.force-gamemode";

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (PermissionManager.hasPermission(player, BYPASS_PERMISSION)) {
            event.setCancelled(false);
        }
    }
}
