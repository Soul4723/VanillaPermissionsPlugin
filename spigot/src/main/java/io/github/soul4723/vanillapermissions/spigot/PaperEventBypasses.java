package io.github.soul4723.vanillapermissions.spigot;

import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code paper} adapter's force-gamemode and move-speed bypasses, registered by reflection on the Paper versions
 * before 1.20.6 whose API has the events they need. Spigot has neither: {@code PlayerGameModeChangeEvent#getCause()} and
 * {@code PlayerFailMoveEvent} are Paper API, so on Spigot both bypasses are unavailable.
 *
 * <p>Listening by class name follows spark's {@code classExists} checks in {@code spark-bukkit} and Bukkit's own
 * {@code PluginManager#registerEvent(Class, Listener, EventPriority, EventExecutor, Plugin, boolean)}, which needs no
 * compile-time reference to the event.
 */
final class PaperEventBypasses {

    static final String FORCE_GAMEMODE_PERMISSION = "minecraft.bypass.force-gamemode";
    static final String MOVE_SPEED_PERMISSION = "minecraft.bypass.move-speed.player";
    static final String FAIL_MOVE_EVENT = "io.papermc.paper.event.player.PlayerFailMoveEvent";

    private static final Listener OWNER = new Listener() {};

    private PaperEventBypasses() {}

    /** Registers whichever bypasses this server's API supports and logs which ones it could not. */
    static void register(JavaPlugin plugin) {
        List<String> missing = new ArrayList<>();
        if (!registerForceGamemode(plugin)) missing.add(FORCE_GAMEMODE_PERMISSION);
        if (!registerMoveSpeed(plugin)) missing.add(MOVE_SPEED_PERMISSION);
        if (!missing.isEmpty()) {
            plugin.getLogger().info("This server's API has no event for " + String.join(" or ", missing)
                    + (missing.size() == 1 ? ", so that bypass node has" : ", so those bypass nodes have")
                    + " no effect here (they need events from a newer Paper)");
        }
    }

    /** Paper: cancel the change to the default game mode when {@code force-gamemode} is on, for players with the node. */
    private static boolean registerForceGamemode(JavaPlugin plugin) {
        final Method getCause;
        final Object defaultGamemode;
        try {
            getCause = PlayerGameModeChangeEvent.class.getMethod("getCause");
            defaultGamemode = enumConstant(getCause.getReturnType(), "DEFAULT_GAMEMODE");
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return false;
        }
        if (defaultGamemode == null) return false;
        EventExecutor executor = (listener, event) -> {
            if (!(event instanceof PlayerGameModeChangeEvent)) return;
            PlayerGameModeChangeEvent change = (PlayerGameModeChangeEvent) event;
            if (invoke(getCause, change) != defaultGamemode) return;
            if (PermissionManager.hasPermission(change.getPlayer(), FORCE_GAMEMODE_PERMISSION)) change.setCancelled(true);
        };
        plugin.getServer().getPluginManager().registerEvent(PlayerGameModeChangeEvent.class, OWNER, EventPriority.HIGHEST,
                executor, plugin, true);
        return true;
    }

    /** Paper 1.20.4+: allow the moves vanilla would reject as too fast or wrong, for players with the node. */
    private static boolean registerMoveSpeed(JavaPlugin plugin) {
        final Class<? extends Event> eventType;
        final Method getFailReason;
        final Method setAllowed;
        final Object movedTooQuickly;
        final Object movedWrongly;
        try {
            eventType = Class.forName(FAIL_MOVE_EVENT, false, plugin.getClass().getClassLoader()).asSubclass(Event.class);
            if (!PlayerEvent.class.isAssignableFrom(eventType)) return false;
            getFailReason = eventType.getMethod("getFailReason");
            setAllowed = eventType.getMethod("setAllowed", boolean.class);
            movedTooQuickly = enumConstant(getFailReason.getReturnType(), "MOVED_TOO_QUICKLY");
            movedWrongly = enumConstant(getFailReason.getReturnType(), "MOVED_WRONGLY");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError absent) {
            return false;
        }
        if (movedTooQuickly == null || movedWrongly == null) return false;
        EventExecutor executor = (listener, event) -> {
            if (!eventType.isInstance(event)) return;
            Object reason = invoke(getFailReason, event);
            Player player = ((PlayerEvent) event).getPlayer();
            if ((reason == movedTooQuickly || reason == movedWrongly)
                    && PermissionManager.hasPermission(player, MOVE_SPEED_PERMISSION)) {
                invoke(setAllowed, event, true);
            }
        };
        plugin.getServer().getPluginManager().registerEvent(eventType, OWNER, EventPriority.NORMAL, executor, plugin, false);
        return true;
    }

    private static Object enumConstant(Class<?> type, String name) {
        if (!type.isEnum()) return null;
        for (Object constant : type.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) return constant;
        }
        return null;
    }

    private static Object invoke(Method method, Object receiver, Object... arguments) {
        try {
            return method.invoke(receiver, arguments);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not call " + method, error);
        }
    }
}
