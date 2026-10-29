package io.github.soul4723.vanillapermissions.spigot;

import com.mojang.brigadier.CommandDispatcher;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftServerAccessTest {

    /** The CraftServer members the adapter calls by name. */
    public interface FakeCraftServer extends Server {
        Object getServer();

        Object getCommandMap();

        void syncCommands();
    }

    /** MinecraftServer's field lives in a superclass here, as CraftBukkit's lives on the server base class. */
    public static class FakeServerBase {
        private Object vanillaCommandDispatcher;
    }

    public static class FakeMinecraftServer extends FakeServerBase {
        FakeMinecraftServer(Object commands) {
            super.vanillaCommandDispatcher = commands;
        }
    }

    /** Mojang Commands with one dispatcher field, a static one that must be ignored, and unrelated fields. */
    public static class FakeCommands {
        static final CommandDispatcher<Object> STATIC_DISPATCHER = new CommandDispatcher<>();
        private final String unrelated = "x";
        private final CommandDispatcher<Object> dispatcher;

        FakeCommands(CommandDispatcher<Object> dispatcher) {
            this.dispatcher = dispatcher;
        }
    }

    public static class FakeCommandsWithTwo extends FakeCommands {
        private final CommandDispatcher<Object> second = new CommandDispatcher<>();

        FakeCommandsWithTwo(CommandDispatcher<Object> dispatcher) {
            super(dispatcher);
        }
    }

    public static class FakeCommandsWithNone {
        private final Object nothing = new Object();
    }

    public static class FakeCommandMap {
        private final boolean registered;

        FakeCommandMap(boolean registered) {
            this.registered = registered;
        }

        public Command getCommand(String name) {
            return registered && name.equals("minecraft:gamemode") ? new Command("gamemode") {
                @Override
                public boolean execute(org.bukkit.command.CommandSender sender, String label, String[] args) {
                    return true;
                }
            } : null;
        }
    }

    /** Paper's and CraftCommandMap's accessor. */
    public static class FakeKnownCommandsMap {
        final Map<String, Command> known = new LinkedHashMap<>();

        public Map<String, Command> getKnownCommands() {
            return known;
        }
    }

    /** Spigot 1.13.2's SimpleCommandMap: only the protected field, declared on the superclass of CraftCommandMap. */
    public static class FakeSimpleCommandMap {
        protected final Map<String, Command> knownCommands = new LinkedHashMap<>();
    }

    public static class FakeLegacyCraftCommandMap extends FakeSimpleCommandMap {
    }

    private static Command command(String name) {
        return new Command(name) {
            @Override
            public boolean execute(org.bukkit.command.CommandSender sender, String label, String[] args) {
                return true;
            }
        };
    }

    private static FakeCraftServer server(Object minecraftServer, Object commandMap) {
        return (FakeCraftServer) Proxy.newProxyInstance(CraftServerAccessTest.class.getClassLoader(),
                new Class<?>[]{FakeCraftServer.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getServer": return minecraftServer;
                        case "getCommandMap": return commandMap;
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    private static FakeCraftServer serverWith(Object commands) {
        return server(new FakeMinecraftServer(commands), new FakeCommandMap(false));
    }

    @Test
    void findsTheDispatcherByType() throws Exception {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        CraftServerAccess access = CraftServerAccess.of(serverWith(new FakeCommands(dispatcher)));

        assertSame(dispatcher, access.vanillaDispatcher());
    }

    @Test
    void twoDispatcherFieldsAreRefused() throws Exception {
        CraftServerAccess access = CraftServerAccess.of(serverWith(new FakeCommandsWithTwo(new CommandDispatcher<>())));

        IllegalStateException error = assertThrows(IllegalStateException.class, access::vanillaDispatcher);
        assertTrue(error.getMessage().contains("More than one"), error.getMessage());
    }

    @Test
    void noDispatcherFieldIsRefused() throws Exception {
        CraftServerAccess access = CraftServerAccess.of(serverWith(new FakeCommandsWithNone()));

        assertThrows(NoSuchFieldException.class, access::vanillaDispatcher);
    }

    @Test
    void anUnsetDispatcherOrCommandsObjectIsRefused() throws Exception {
        assertThrows(IllegalStateException.class, () -> CraftServerAccess.of(serverWith(null)).vanillaDispatcher());
        assertThrows(IllegalStateException.class, () -> CraftServerAccess.of(serverWith(new FakeCommands(null))).vanillaDispatcher());
    }

    @Test
    void aServerWithoutAMinecraftServerIsRefused() {
        assertThrows(IllegalStateException.class, () -> CraftServerAccess.of(server(null, null)));
    }

    @Test
    void vanillaCommandsRegisteredFollowsTheCommandMap() throws Exception {
        Object minecraft = new FakeMinecraftServer(new FakeCommands(new CommandDispatcher<>()));

        assertTrue(CraftServerAccess.of(server(minecraft, new FakeCommandMap(true))).vanillaCommandsRegistered());
        assertFalse(CraftServerAccess.of(server(minecraft, new FakeCommandMap(false))).vanillaCommandsRegistered());
    }

    @Test
    void knownCommandsComeFromTheAccessorOrTheField() throws Exception {
        Object minecraft = new FakeMinecraftServer(new FakeCommands(new CommandDispatcher<>()));
        Command tp = command("tp");
        FakeKnownCommandsMap modern = new FakeKnownCommandsMap();
        modern.known.put("tp", tp);
        modern.known.put("minecraft:tp", tp);
        FakeLegacyCraftCommandMap legacy = new FakeLegacyCraftCommandMap();
        legacy.knownCommands.put("tp", tp);

        assertEquals(2, CraftServerAccess.of(server(minecraft, modern)).knownCommands().size());
        assertSame(tp, new ArrayList<>(CraftServerAccess.of(server(minecraft, legacy)).knownCommands()).get(0));
        assertThrows(IllegalStateException.class, () -> CraftServerAccess.of(server(minecraft, null)).knownCommands());
    }
}
