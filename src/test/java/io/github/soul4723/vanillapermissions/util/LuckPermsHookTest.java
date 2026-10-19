package io.github.soul4723.vanillapermissions.util;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.LuckPermsEvent;
import net.luckperms.api.event.context.ContextUpdateEvent;
import net.luckperms.api.event.group.GroupDataRecalculateEvent;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Command tree refresh after LuckPerms recalculates a player's permissions. */
class LuckPermsHookTest {
    private final UUID playerId = UUID.randomUUID();
    private final List<Runnable> scheduled = new ArrayList<>();
    private final LuckPermsHook hook = new LuckPermsHook();
    private boolean pluginEnabled = true;
    private boolean schedulerRejects;
    private final Map<Class<?>, Consumer<LuckPermsEvent>> handlers = new HashMap<>();
    private int closedSubscriptions;
    private int commandUpdates;
    private Player player;
    private Plugin plugin;

    @BeforeEach
    void initializeHook() {
        player = proxy(Player.class, (p, m, a) -> switch (m.getName()) {
            case "updateCommands" -> {
                commandUpdates++;
                yield null;
            }
            case "getUniqueId" -> playerId;
            default -> throw new AssertionError("Unexpected Player call: " + m);
        });
        BukkitScheduler scheduler = proxy(BukkitScheduler.class, (p, m, a) -> {
            assertEquals("runTaskLater", m.getName());
            assertSame(plugin, a[0], "Refresh scheduled for another plugin");
            assertEquals(LuckPermsHook.REFRESH_DELAY_TICKS, a[2]);
            if (schedulerRejects) throw new IllegalPluginAccessException("Plugin attempted to register task while disabled");
            scheduled.add((Runnable) a[1]);
            return null;
        });
        @SuppressWarnings("unchecked")
        EventSubscription<LuckPermsEvent> subscription = proxy(EventSubscription.class, (p, m, a) -> {
            assertEquals("close", m.getName());
            closedSubscriptions++;
            return null;
        });
        EventBus eventBus = proxy(EventBus.class, (p, m, a) -> {
            assertEquals("subscribe", m.getName());
            assertSame(plugin, a[0], "Subscription must be owned by the plugin");
            @SuppressWarnings("unchecked")
            Consumer<LuckPermsEvent> consumer = (Consumer<LuckPermsEvent>) a[2];
            handlers.put((Class<?>) a[1], consumer);
            return subscription;
        });
        LuckPerms luckPerms = proxy(LuckPerms.class, (p, m, a) -> {
            assertEquals("getEventBus", m.getName());
            return eventBus;
        });
        Plugin luckPermsPlugin = proxy(Plugin.class, (p, m, a) -> { throw new AssertionError(m); });
        PluginManager pluginManager = proxy(PluginManager.class, (p, m, a) -> {
            assertEquals("getPlugin", m.getName());
            return "LuckPerms".equals(a[0]) ? luckPermsPlugin : null;
        });
        ServicesManager services = proxy(ServicesManager.class, (p, m, a) -> {
            assertEquals("getRegistration", m.getName());
            return new RegisteredServiceProvider<>(LuckPerms.class, luckPerms, ServicePriority.Normal, luckPermsPlugin);
        });
        Server server = proxy(Server.class, (p, m, a) -> switch (m.getName()) {
            case "getPluginManager" -> pluginManager;
            case "getServicesManager" -> services;
            case "getScheduler" -> scheduler;
            case "getPlayer" -> playerId.equals(a[0]) ? player : null;
            case "getOnlinePlayers" -> List.of(player);
            default -> throw new AssertionError("Unexpected Server call: " + m);
        });
        Logger logger = Logger.getAnonymousLogger();
        plugin = proxy(Plugin.class, (p, m, a) -> switch (m.getName()) {
            case "getServer" -> server;
            case "getLogger" -> logger;
            case "isEnabled" -> pluginEnabled;
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p == a[0];
            default -> throw new AssertionError("Unexpected Plugin call: " + m);
        });

        hook.initialize(plugin);
        assertTrue(hook.isEnabled(), "LuckPerms hook did not initialize");
        for (Class<?> event : List.of(UserDataRecalculateEvent.class, GroupDataRecalculateEvent.class, ContextUpdateEvent.class)) {
            assertNotNull(handlers.get(event), event.getSimpleName() + " was not subscribed");
        }
    }

    @Test
    void recalculationRefreshesCommandTreeOnMainThread() {
        recalculate(playerId);
        assertEquals(0, commandUpdates, "Command tree refreshed off the main thread");
        assertEquals(1, scheduled.size());
        runScheduled();
        assertEquals(1, commandUpdates);
    }

    @Test
    void burstOfRecalculationsSchedulesOneRefresh() {
        recalculate(playerId);
        recalculate(playerId);
        recalculate(playerId);
        assertEquals(1, scheduled.size(), "Pending refresh was not coalesced");
        runScheduled();
        recalculate(playerId);
        assertEquals(1, scheduled.size(), "Refresh was not rescheduled after running");
        runScheduled();
        assertEquals(2, commandUpdates);
    }

    @Test
    void groupChangeRefreshesOnlinePlayers() {
        handlers.get(GroupDataRecalculateEvent.class).accept(proxy(GroupDataRecalculateEvent.class, (p, m, a) -> {
            throw new AssertionError("Group details are not needed: " + m);
        }));
        runScheduled();
        assertEquals(1, commandUpdates, "Group permission change did not refresh online players");
    }

    @Test
    void contextChangeRefreshesThatPlayer() {
        handlers.get(ContextUpdateEvent.class).accept(proxy(ContextUpdateEvent.class, (p, m, a) -> {
            assertEquals("getSubject", m.getName());
            return a == null ? player : Optional.of(player);
        }));
        runScheduled();
        assertEquals(1, commandUpdates, "Context change did not refresh the player");
    }

    @Test
    void offlinePlayerIsIgnored() {
        recalculate(UUID.randomUUID());
        runScheduled();
        assertEquals(0, commandUpdates);
    }

    @Test
    void closeUnsubscribesAndStopsRefreshes() {
        recalculate(playerId);
        hook.close();
        assertEquals(3, closedSubscriptions, "LuckPerms subscriptions were not closed");
        runScheduled();
        recalculate(playerId);
        assertTrue(scheduled.isEmpty(), "Refresh scheduled after close");
        assertEquals(0, commandUpdates, "Command tree refreshed after close");
    }

    @Test
    void disabledPluginDoesNotSchedule() {
        pluginEnabled = false;
        recalculate(playerId);
        assertTrue(scheduled.isEmpty(), "Refresh scheduled while plugin disabled");
    }

    @Test
    void schedulerRejectionDuringDisableIsIgnored() {
        schedulerRejects = true;
        assertDoesNotThrow(() -> recalculate(playerId));
        schedulerRejects = false;
        recalculate(playerId);
        assertEquals(1, scheduled.size(), "Rejected refresh blocked later refreshes");
    }

    private void recalculate(UUID id) {
        User user = proxy(User.class, (p, m, a) -> {
            assertEquals("getUniqueId", m.getName());
            return id;
        });
        handlers.get(UserDataRecalculateEvent.class).accept(proxy(UserDataRecalculateEvent.class, (p, m, a) -> {
            assertEquals("getUser", m.getName());
            return user;
        }));
    }

    private void runScheduled() {
        List<Runnable> tasks = List.copyOf(scheduled);
        scheduled.clear();
        tasks.forEach(Runnable::run);
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
