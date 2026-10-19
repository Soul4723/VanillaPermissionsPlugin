package io.github.soul4723.vanillapermissions.listener;

import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;

public class ForceGamemodeBypassListener implements Listener {

    static final String BYPASS_PERMISSION = "minecraft.bypass.force-gamemode";

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getCause() != PlayerGameModeChangeEvent.Cause.DEFAULT_GAMEMODE) return;

        if (PermissionManager.hasPermission(event.getPlayer(), BYPASS_PERMISSION)) {
            event.setCancelled(true);
        }
    }
}
