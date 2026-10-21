package io.github.soul4723.vanillapermissions.util;

import net.luckperms.api.LuckPerms;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static io.github.soul4723.vanillapermissions.util.TestLuckPerms.proxy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Connecting to LuckPerms. The hook only reads the API: it subscribes to no events, because LuckPerms resends
 * command trees itself (a LuckPerms proxy that is not the event bus would fail the test if it were asked for one).
 */
class LuckPermsHookTest {
    private final LuckPerms luckPerms = proxy(LuckPerms.class, (p, m, a) -> {
        throw new AssertionError("The hook must not call LuckPerms while connecting: " + m);
    });
    private boolean luckPermsEnabled = true;
    private boolean registered = true;

    @Test
    void connectsToAnEnabledLuckPermsWithoutSubscribingToEvents() {
        LuckPermsHook hook = new LuckPermsHook();
        hook.initialize(plugin());
        assertTrue(hook.isEnabled());
        assertSame(luckPerms, hook.getLuckPerms());
        hook.close();
        assertFalse(hook.isEnabled());
    }

    @Test
    void disabledOrUnregisteredLuckPermsLeavesTheHookOff() {
        luckPermsEnabled = false;
        LuckPermsHook disabled = new LuckPermsHook();
        disabled.initialize(plugin());
        assertFalse(disabled.isEnabled(), "Hook connected to a LuckPerms that failed to start");

        luckPermsEnabled = true;
        registered = false;
        LuckPermsHook unregistered = new LuckPermsHook();
        unregistered.initialize(plugin());
        assertFalse(unregistered.isEnabled());
    }

    private Plugin plugin() {
        Plugin luckPermsPlugin = proxy(Plugin.class, (p, m, a) -> {
            if (m.getName().equals("isEnabled")) return luckPermsEnabled;
            throw new AssertionError(m);
        });
        PluginManager plugins = proxy(PluginManager.class, (p, m, a) -> "LuckPerms".equals(a[0]) ? luckPermsPlugin : null);
        ServicesManager services = proxy(ServicesManager.class, (p, m, a) -> {
            assertTrue(luckPermsEnabled, "Looked up the API of a disabled LuckPerms");
            return registered ? new RegisteredServiceProvider<>(LuckPerms.class, luckPerms, ServicePriority.Normal, luckPermsPlugin) : null;
        });
        Server server = proxy(Server.class, (p, m, a) -> switch (m.getName()) {
            case "getPluginManager" -> plugins;
            case "getServicesManager" -> services;
            default -> throw new AssertionError(m);
        });
        Logger logger = Logger.getAnonymousLogger();
        return proxy(Plugin.class, (p, m, a) -> switch (m.getName()) {
            case "getServer" -> server;
            case "getLogger" -> logger;
            default -> throw new AssertionError(m);
        });
    }
}
