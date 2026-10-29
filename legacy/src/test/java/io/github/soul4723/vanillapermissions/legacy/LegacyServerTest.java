package io.github.soul4723.vanillapermissions.legacy;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyServerTest {
    /** Native members are deliberately absent: these paths must not use them. */
    private static LegacyServer withoutNativeMembers() throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeType.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        return (LegacyServer) unsafeType.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), LegacyServer.class);
    }

    private static void field(LegacyServer server, String name, Object value) throws Exception {
        Field field = LegacyServer.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(server, value);
    }

    @Test
    void offThreadResolutionReturnsBeforeTouchingServerState() throws Exception {
        Field bukkitServer = Bukkit.class.getDeclaredField("server");
        bukkitServer.setAccessible(true);
        Object previous = bukkitServer.get(null);
        Server server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isPrimaryThread")) return false;
                    throw new AssertionError("Off-thread server access: " + method);
                });
        try {
            bukkitServer.set(null, server);
            LegacyServer legacy = withoutNativeMembers();
            assertNull(legacy.resolve(null, "@a", true));
            assertNull(legacy.resolve(null, "Steve", true));
        } finally {
            bukkitServer.set(null, previous);
        }
    }

    @Test
    void resolveFailuresAreWarnedOnlyOnce() throws Exception {
        LegacyServer legacy = withoutNativeMembers();
        List<LogRecord> records = new ArrayList<>();
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() {}
            @Override public void close() {}
        });
        field(legacy, "logger", logger);
        Method log = LegacyServer.class.getDeclaredMethod("log", String.class, Throwable.class);
        log.setAccessible(true);
        log.invoke(legacy, "@a", new IllegalStateException("broken resolver"));
        log.invoke(legacy, "@e", new IllegalStateException("still broken"));
        assertEquals(1, records.size());
        assertEquals(Level.WARNING, records.get(0).getLevel());
    }

    public interface Overrides {
        boolean getCommandBlockOverride(String token);
    }

    @Test
    void commandBlockOverridesUseTheRawTokenAndFailSafe() throws Exception {
        LegacyServer legacy = withoutNativeMembers();
        Server craft = (Server) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Server.class, Overrides.class},
                (proxy, method, args) -> {
                    assertEquals("GaMeMode", args[0]);
                    return true;
                });
        field(legacy, "server", craft);
        assertTrue(legacy.commandBlockOverride("GaMeMode"));
        Server missing = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (proxy, method, args) -> { throw new AssertionError(method); });
        field(legacy, "server", missing);
        assertFalse(legacy.commandBlockOverride("gamemode"));
        Server broken = (Server) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Server.class, Overrides.class},
                (proxy, method, args) -> { throw new IllegalStateException("unavailable"); });
        field(legacy, "server", broken);
        assertFalse(legacy.commandBlockOverride("gamemode"));
    }
}
