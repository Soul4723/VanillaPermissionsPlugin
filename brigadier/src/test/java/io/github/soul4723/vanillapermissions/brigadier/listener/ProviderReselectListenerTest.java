package io.github.soul4723.vanillapermissions.brigadier.listener;

import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.Server;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static io.github.soul4723.vanillapermissions.util.TestLuckPerms.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reselection on ServerLoadEvent, with no plugins installed; PermissionProviderSelectionTest covers the choice itself. */
class ProviderReselectListenerTest {
    private final List<LogRecord> records = new ArrayList<>();
    private final Plugin owner;

    ProviderReselectListenerTest() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() {}
            @Override public void close() {}
        });
        PluginManager plugins = proxy(PluginManager.class, (p, m, a) -> {
            if (!m.getName().equals("getPlugin")) throw new AssertionError("Unexpected plugin manager call: " + m);
            return null;
        });
        Server server = proxy(Server.class, (p, m, a) -> {
            if (!m.getName().equals("getPluginManager")) throw new AssertionError("Unexpected server call: " + m);
            return plugins;
        });
        owner = proxy(Plugin.class, (p, m, a) -> switch (m.getName()) {
            case "getServer" -> server;
            case "getLogger" -> logger;
            default -> throw new AssertionError("Unexpected plugin call: " + m);
        });
    }

    private List<LogRecord> at(Level level) {
        return records.stream().filter(r -> r.getLevel() == level).toList();
    }

    @Test
    void onlyStartupLoadReselects() {
        PermissionManager.initialize(PermissionManager.selectProvider(owner), owner);
        ProviderReselectListener listener = new ProviderReselectListener(owner);
        listener.reselectOn(ServerLoadEvent.LoadType.RELOAD);
        assertTrue(at(Level.WARNING).isEmpty(), "A reload selected again");
        listener.reselectOn(ServerLoadEvent.LoadType.STARTUP);
        assertEquals(1, at(Level.WARNING).size(), "Startup did not select again");
    }
}
