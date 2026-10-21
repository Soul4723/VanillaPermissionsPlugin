package io.github.soul4723.vanillapermissions.paper.listener;

import io.github.soul4723.vanillapermissions.util.PermissionManager;
import io.papermc.paper.event.player.PlayerFailMoveEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MoveSpeedBypassListener implements Listener {

    public static final String PLAYER_SPEED_PERMISSION = "minecraft.bypass.move-speed.player";

    @EventHandler
    public void onPlayerFailMove(PlayerFailMoveEvent event) {
        PlayerFailMoveEvent.FailReason reason = event.getFailReason();
        if ((reason == PlayerFailMoveEvent.FailReason.MOVED_TOO_QUICKLY ||
                reason == PlayerFailMoveEvent.FailReason.MOVED_WRONGLY) &&
                PermissionManager.hasPermission(event.getPlayer(), PLAYER_SPEED_PERMISSION)) {
            event.setAllowed(true);
        }
    }
}
