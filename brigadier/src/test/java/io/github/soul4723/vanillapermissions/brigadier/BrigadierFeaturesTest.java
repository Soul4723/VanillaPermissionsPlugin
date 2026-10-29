package io.github.soul4723.vanillapermissions.brigadier;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.github.soul4723.vanillapermissions.util.TestLuckPerms.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BrigadierFeaturesTest {
    @Test
    void missingServerLoadEventSchedulesReselectionInsteadOfRegistering() {
        List<String> calls = new ArrayList<>();
        PluginManager plugins = proxy(PluginManager.class, (p, m, a) -> {
            assertEquals("registerEvents", m.getName());
            calls.add("register");
            return null;
        });
        BukkitScheduler scheduler = proxy(BukkitScheduler.class, (p, m, a) -> {
            assertEquals("runTask", m.getName());
            calls.add("schedule");
            return null;
        });
        Server server = proxy(Server.class, (p, m, a) -> switch (m.getName()) {
            case "getPluginManager" -> plugins;
            case "getScheduler" -> scheduler;
            default -> throw new AssertionError(m);
        });
        Plugin plugin = proxy(Plugin.class, (p, m, a) -> server);
        ClassLoader absent = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals("org.bukkit.event.server.ServerLoadEvent")) throw new ClassNotFoundException(name);
                return super.loadClass(name, resolve);
            }
        };
        BrigadierFeatures.registerProviderReselect(plugin, absent);
        assertEquals(List.of("schedule"), calls);
        calls.clear();
        BrigadierFeatures.registerProviderReselect(plugin, getClass().getClassLoader());
        assertEquals(List.of("register"), calls);
    }

}
