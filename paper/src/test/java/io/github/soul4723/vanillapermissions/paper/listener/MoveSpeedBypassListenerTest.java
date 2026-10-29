package io.github.soul4723.vanillapermissions.paper.listener;

import io.github.soul4723.vanillapermissions.util.PermissionManager;
import io.papermc.paper.event.player.PlayerFailMoveEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs without LuckPerms, so PermissionManager uses the Bukkit permission fallback. */
class MoveSpeedBypassListenerTest {
    private static final String PERMISSION = "minecraft.bypass.move-speed.player";

    private final Set<String> grants = new HashSet<>();
    private int permissionChecks;
    private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
            (proxy, method, values) -> switch (method.getName()) {
                case "isOp" -> false;
                case "hasPermission" -> {
                    assertEquals(PERMISSION, values[0]);
                    permissionChecks++;
                    yield grants.contains(values[0]);
                }
                case "toString" -> "check-player";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == values[0];
                default -> throw new AssertionError("Unexpected Player call: " + method);
            });
    private final MoveSpeedBypassListener listener = new MoveSpeedBypassListener();

    @Test
    void speedChecksApplyWithoutPermission() {
        assertFalse(allowed(PlayerFailMoveEvent.FailReason.MOVED_TOO_QUICKLY));
        assertFalse(allowed(PlayerFailMoveEvent.FailReason.MOVED_WRONGLY));
    }

    @Test
    void permissionBypassesSpeedChecks() {
        grants.add(PERMISSION);
        assertTrue(allowed(PlayerFailMoveEvent.FailReason.MOVED_TOO_QUICKLY));
        assertTrue(allowed(PlayerFailMoveEvent.FailReason.MOVED_WRONGLY));
    }

    @Test
    void unrelatedReasonsAreNotChecked() {
        grants.add(PERMISSION);
        assertFalse(allowed(PlayerFailMoveEvent.FailReason.CLIPPED_INTO_BLOCK));
        assertFalse(allowed(PlayerFailMoveEvent.FailReason.MOVED_INTO_UNLOADED_CHUNK));
        assertEquals(0, permissionChecks, "Unrelated movement reason checked speed permission");
    }

    private boolean allowed(PlayerFailMoveEvent.FailReason reason) {
        Location location = new Location(null, 0, 0, 0);
        PlayerFailMoveEvent event = new PlayerFailMoveEvent(player, reason, false, false, location, location);
        listener.onPlayerFailMove(event);
        return event.isAllowed();
    }
}
