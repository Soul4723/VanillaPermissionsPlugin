package io.github.soul4723.vanillapermissions.util;

import io.papermc.paper.plugin.configuration.PluginMeta;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.EventSubscription;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static io.github.soul4723.vanillapermissions.util.TestLuckPerms.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Provider choice from proxied plugin, service and logging fixtures; no live LuckPerms or Vault. */
class PermissionProviderSelectionTest {
    private final List<LogRecord> records = new ArrayList<>();
    private final Logger logger = Logger.getAnonymousLogger();
    private final Map<String, Plugin> installed = new HashMap<>();
    private final Map<Class<?>, Object> services = new HashMap<>();
    private final List<EventSubscription<?>> subscriptions = new ArrayList<>();
    private final Plugin owner;

    PermissionProviderSelectionTest() {
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() {}
            @Override public void close() {}
        });
        PluginManager plugins = proxy(PluginManager.class, (p, m, a) -> {
            if (!m.getName().equals("getPlugin")) throw new AssertionError("Unexpected plugin manager call: " + m);
            return installed.get((String) a[0]);
        });
        ServicesManager manager = proxy(ServicesManager.class, (p, m, a) -> {
            if (!m.getName().equals("getRegistration")) throw new AssertionError("Unexpected services call: " + m);
            return register((Class<?>) a[0]);
        });
        Server server = proxy(Server.class, (p, m, a) -> switch (m.getName()) {
            case "getPluginManager" -> plugins;
            case "getServicesManager" -> manager;
            default -> throw new AssertionError("Unexpected server call: " + m);
        });
        owner = proxy(Plugin.class, (p, m, a) -> switch (m.getName()) {
            case "getServer" -> server;
            case "getLogger" -> logger;
            default -> throw new AssertionError("Unexpected plugin call: " + m);
        });
    }

    private <T> RegisteredServiceProvider<?> register(Class<T> type) {
        Object service = services.get(type);
        return service == null ? null
            : new RegisteredServiceProvider<>(type, type.cast(service), ServicePriority.Normal, owner);
    }

    private Plugin installed(String name, boolean enabled) {
        Plugin plugin = proxy(Plugin.class, (p, m, a) -> switch (m.getName()) {
            case "isEnabled" -> enabled;
            case "getPluginMeta" -> proxy(PluginMeta.class, (mp, mm, ma) -> {
                if (!mm.getName().equals("getVersion")) throw new AssertionError(mm);
                return "5.4";
            });
            default -> throw new AssertionError("Unexpected " + name + " call: " + m);
        });
        installed.put(name, plugin);
        return plugin;
    }

    private void registerLuckPerms() {
        EventSubscription<?> subscription = proxy(EventSubscription.class, (p, m, a) -> {
            if (m.getName().equals("close")) return null;
            throw new AssertionError(m);
        });
        subscriptions.add(subscription);
        EventBus bus = proxy(EventBus.class, (p, m, a) -> {
            if (!m.getName().equals("subscribe")) throw new AssertionError(m);
            return subscription;
        });
        services.put(LuckPerms.class, proxy(LuckPerms.class, (p, m, a) -> {
            if (!m.getName().equals("getEventBus")) throw new AssertionError(m);
            return bus;
        }));
    }

    private List<LogRecord> at(Level level) {
        return records.stream().filter(r -> r.getLevel() == level).toList();
    }

    private boolean logged(String text) {
        return records.stream().anyMatch(r -> r.getMessage().contains(text));
    }

    @Test
    void runningLuckPermsWins() throws Exception {
        installed("LuckPerms", true);
        registerLuckPerms();
        installed("Vault", true);
        PermissionProvider provider = PermissionManager.selectProvider(owner);
        try {
            assertInstanceOf(LuckPermsPermissionProvider.class, provider);
            assertTrue(logged("Using LuckPerms 5.4"), "No LuckPerms line logged");
            assertTrue(at(Level.SEVERE).isEmpty(), "Healthy LuckPerms logged an error");
        } finally {
            provider.close();
        }
    }

    @Test
    void disabledLuckPermsFallsBackToBukkit() {
        installed("LuckPerms", false);
        PermissionProvider provider = PermissionManager.selectProvider(owner);
        BukkitPermissionProvider bukkit = assertInstanceOf(BukkitPermissionProvider.class, provider);
        assertFalse(at(Level.SEVERE).isEmpty(), "Broken LuckPerms was not reported");
        assertFalse(bukkit.hasMeta(), "Meta without a source");
    }

    @Test
    void luckPermsWithoutRegisteredApiFallsBackToBukkit() {
        installed("LuckPerms", true);
        assertInstanceOf(BukkitPermissionProvider.class, PermissionManager.selectProvider(owner));
        assertFalse(at(Level.SEVERE).isEmpty(), "Missing LuckPerms API was not reported");
    }

    @Test
    void vaultSuppliesPermissionsPluginAndChatMeta() {
        installed("Vault", true);
        services.put(Permission.class, VaultHookTest.permission("GroupManager"));
        services.put(Chat.class, chatNamed("TestChat"));
        BukkitPermissionProvider provider =
            assertInstanceOf(BukkitPermissionProvider.class, PermissionManager.selectProvider(owner));
        assertEquals("GroupManager", provider.getPermissionsPlugin());
        assertTrue(provider.hasMeta(), "Chat registration gave no meta");
        assertTrue(logged("Vault chat meta (TestChat)"), "Meta source not logged");
        assertTrue(at(Level.WARNING).isEmpty(), "Unexpected warning");
    }

    @Test
    void knownPluginWithoutVaultHasNoMeta() {
        installed("PermissionsEx", true);
        BukkitPermissionProvider provider =
            assertInstanceOf(BukkitPermissionProvider.class, PermissionManager.selectProvider(owner));
        assertEquals("PermissionsEx", provider.getPermissionsPlugin());
        assertFalse(provider.hasMeta(), "Meta without Vault");
        assertTrue(logged("Using Bukkit permissions from PermissionsEx"), "Source not logged");
        assertTrue(logged("limits and weights are off"), "Missing meta notice");
    }

    @Test
    void disabledKnownPluginIsIgnored() {
        installed("PermissionsEx", false);
        installed("GroupManager", true);
        BukkitPermissionProvider provider =
            assertInstanceOf(BukkitPermissionProvider.class, PermissionManager.selectProvider(owner));
        assertEquals("GroupManager", provider.getPermissionsPlugin());
    }

    @Test
    void noPermissionsPluginWarnsOnce() {
        BukkitPermissionProvider provider =
            assertInstanceOf(BukkitPermissionProvider.class, PermissionManager.selectProvider(owner));
        assertNull(provider.getPermissionsPlugin());
        assertFalse(provider.hasMeta(), "Meta without a source");
        List<LogRecord> warnings = at(Level.WARNING);
        assertEquals(1, warnings.size(), "Expected exactly one warning");
        assertTrue(warnings.get(0).getMessage().contains("No permissions plugin found"));
    }

    private static Chat chatNamed(String name) {
        return new Chat(null) {
            @Override public String getName() { return name; }
            @Override public boolean isEnabled() { return true; }
            @Override public String getPlayerPrefix(String world, String player) { throw new AssertionError(); }
            @Override public void setPlayerPrefix(String world, String player, String prefix) { throw new AssertionError(); }
            @Override public String getPlayerSuffix(String world, String player) { throw new AssertionError(); }
            @Override public void setPlayerSuffix(String world, String player, String suffix) { throw new AssertionError(); }
            @Override public String getGroupPrefix(String world, String group) { throw new AssertionError(); }
            @Override public void setGroupPrefix(String world, String group, String prefix) { throw new AssertionError(); }
            @Override public String getGroupSuffix(String world, String group) { throw new AssertionError(); }
            @Override public void setGroupSuffix(String world, String group, String suffix) { throw new AssertionError(); }
            @Override public int getPlayerInfoInteger(String world, String player, String node, int def) { throw new AssertionError(); }
            @Override public void setPlayerInfoInteger(String world, String player, String node, int value) { throw new AssertionError(); }
            @Override public int getGroupInfoInteger(String world, String group, String node, int def) { throw new AssertionError(); }
            @Override public void setGroupInfoInteger(String world, String group, String node, int value) { throw new AssertionError(); }
            @Override public double getPlayerInfoDouble(String world, String player, String node, double def) { throw new AssertionError(); }
            @Override public void setPlayerInfoDouble(String world, String player, String node, double value) { throw new AssertionError(); }
            @Override public double getGroupInfoDouble(String world, String group, String node, double def) { throw new AssertionError(); }
            @Override public void setGroupInfoDouble(String world, String group, String node, double value) { throw new AssertionError(); }
            @Override public boolean getPlayerInfoBoolean(String world, String player, String node, boolean def) { throw new AssertionError(); }
            @Override public void setPlayerInfoBoolean(String world, String player, String node, boolean value) { throw new AssertionError(); }
            @Override public boolean getGroupInfoBoolean(String world, String group, String node, boolean def) { throw new AssertionError(); }
            @Override public void setGroupInfoBoolean(String world, String group, String node, boolean value) { throw new AssertionError(); }
            @Override public String getPlayerInfoString(String world, String player, String node, String def) { throw new AssertionError(); }
            @Override public void setPlayerInfoString(String world, String player, String node, String value) { throw new AssertionError(); }
            @Override public String getGroupInfoString(String world, String group, String node, String def) { throw new AssertionError(); }
            @Override public void setGroupInfoString(String world, String group, String node, String value) { throw new AssertionError(); }
        };
    }
}
