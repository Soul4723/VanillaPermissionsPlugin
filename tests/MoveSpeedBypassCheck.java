import io.github.soul4723.vanillapermissions.listener.MoveSpeedBypassListener;
import io.papermc.paper.event.player.PlayerFailMoveEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

class MoveSpeedBypassCheck {
    private static final String PERMISSION = "minecraft.bypass.move-speed.player";

    public static void main(String[] args) {
        Set<String> grants = new HashSet<>();
        int[] permissionChecks = {0};
        Set<String> permissionNodes = Set.of(PERMISSION, "minecraft.bypass.move-speed", "minecraft.bypass", "minecraft");
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, values) -> switch (method.getName()) {
                    case "isOp" -> false;
                    case "hasPermission" -> {
                        assert permissionNodes.contains(values[0]) : values[0];
                        permissionChecks[0]++;
                        yield grants.contains(values[0]);
                    }
                    case "toString" -> "check-player";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == values[0];
                    default -> throw new AssertionError("Unexpected Player call: " + method);
                });

        MoveSpeedBypassListener listener = new MoveSpeedBypassListener();
        assert !check(listener, player, PlayerFailMoveEvent.FailReason.MOVED_TOO_QUICKLY);
        assert !check(listener, player, PlayerFailMoveEvent.FailReason.MOVED_WRONGLY);
        grants.add(PERMISSION);
        assert check(listener, player, PlayerFailMoveEvent.FailReason.MOVED_TOO_QUICKLY);
        assert check(listener, player, PlayerFailMoveEvent.FailReason.MOVED_WRONGLY);
        grants.clear();
        grants.add("minecraft.bypass.move-speed");
        assert !check(listener, player, PlayerFailMoveEvent.FailReason.MOVED_TOO_QUICKLY)
                : "Bare parent permission allowed child";

        int beforeUnrelated = permissionChecks[0];
        assert !check(listener, player, PlayerFailMoveEvent.FailReason.CLIPPED_INTO_BLOCK);
        assert !check(listener, player, PlayerFailMoveEvent.FailReason.MOVED_INTO_UNLOADED_CHUNK);
        assert permissionChecks[0] == beforeUnrelated : "Unrelated movement reason checked speed permission";
        System.out.println("PASS: only permitted too-quickly and moved-wrongly checks are bypassed");
    }

    private static boolean check(MoveSpeedBypassListener listener, Player player,
                                 PlayerFailMoveEvent.FailReason reason) {
        Location location = new Location(null, 0, 0, 0);
        PlayerFailMoveEvent event = new PlayerFailMoveEvent(player, reason, false, false, location, location);
        listener.onPlayerFailMove(event);
        return event.isAllowed();
    }
}
