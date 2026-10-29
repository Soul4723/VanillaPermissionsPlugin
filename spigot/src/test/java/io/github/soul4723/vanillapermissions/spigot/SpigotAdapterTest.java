package io.github.soul4723.vanillapermissions.spigot;

import io.github.soul4723.vanillapermissions.brigadier.BrigadierPermissions;
import io.github.soul4723.vanillapermissions.util.ServerSoftware;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpigotAdapterTest {

    /** Spigot 1.20.4 exposes hasStopped but not isRunning. */
    public static class ModernServer {
        boolean stopped;

        public final boolean hasStopped() {
            return stopped;
        }
    }

    /** Spigot 1.13.2 has no public hasStopped method. */
    public static class OldServer {
        boolean running = true;

        public boolean isRunning() {
            return running;
        }
    }

    public static class BrokenProbe {
        public boolean hasStopped() {
            throw new IllegalStateException("unavailable");
        }
    }

    @Test
    void disableResyncsTheRunningSpigot1204ShapeButNotShutdown() throws Exception {
        ModernServer minecraft = new ModernServer();
        List<String> calls = new ArrayList<>();
        CraftServerAccessTest.FakeCraftServer server = (CraftServerAccessTest.FakeCraftServer) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{CraftServerAccessTest.FakeCraftServer.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getServer": return minecraft;
                        case "getOnlinePlayers": return List.of();
                        case "syncCommands": calls.add("syncCommands"); return null;
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
        SpigotAdapter adapter = new SpigotAdapter();
        set(adapter, "craft", CraftServerAccess.of(server));
        set(adapter, "permissions", new BrigadierPermissions(null, null, null));

        Field bukkitServer = Bukkit.class.getDeclaredField("server");
        bukkitServer.setAccessible(true);
        Object previous = bukkitServer.get(null);
        try {
            bukkitServer.set(null, server);
            adapter.disable();
            assertEquals(List.of("syncCommands"), calls);
            minecraft.stopped = true;
            adapter.disable();
            assertEquals(List.of("syncCommands"), calls);
        } finally {
            bukkitServer.set(null, previous);
        }
    }

    @Test
    void olderServersUseIsRunning() {
        OldServer minecraft = new OldServer();
        assertFalse(SpigotAdapter.isStopping(minecraft));
        minecraft.running = false;
        assertTrue(SpigotAdapter.isStopping(minecraft));
    }

    @Test
    void missingOrBrokenProbesPreferResyncing() {
        assertFalse(SpigotAdapter.isStopping(new Object()));
        assertFalse(SpigotAdapter.isStopping(new BrokenProbe()));
    }

    private static void set(SpigotAdapter adapter, String name, Object value) throws Exception {
        Field field = SpigotAdapter.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(adapter, value);
    }

    @Test
    void skippedVanillaCommandsAreWarningsAndPluginCommandsACount() {
        List<String> records = new ArrayList<>();
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { records.add(record.getLevel() + " " + record.getMessage()); }
            @Override public void flush() {}
            @Override public void close() {}
        });
        SpigotAdapter.logSkipped(logger, List.of("lp", "luckperms:lp", "tp", "minecraft:give"));
        assertEquals(List.of(
            "WARNING Another plugin changed these vanilla commands, so they have no permission checks from VanillaPermissionsPlugin: tp, minecraft:give",
            "INFO Left 2 commands that other plugins added to the vanilla command dispatcher unchecked",
            "FINE Commands other plugins added: lp, luckperms:lp"), records);
    }

    @Test
    void bypassNoteSaysSpigotNeedsPaper() {
        assertEquals("These bypasses need Paper's API and do nothing on Spigot (see docs/compatibility.md#limits): "
                + "minecraft.bypass.force-gamemode",
            PaperEventBypasses.unavailableMessage(ServerSoftware.SPIGOT,
                List.of(PaperEventBypasses.FORCE_GAMEMODE_PERMISSION)));
        assertTrue(PaperEventBypasses.unavailableMessage(ServerSoftware.PAPER,
                List.of(PaperEventBypasses.MOVE_SPEED_PERMISSION)).contains("newer Paper"));
    }
}
