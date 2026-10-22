package io.github.soul4723.vanillapermissions.spigot;

import com.mojang.brigadier.CommandDispatcher;
import org.bukkit.Server;
import org.bukkit.command.Command;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * The parts of CraftBukkit the adapter needs, through names CraftBukkit added itself and never obfuscates
 * ({@code CraftServer#getServer()}, {@code MinecraftServer#vanillaCommandDispatcher}, {@code CraftServer#syncCommands()},
 * {@code CraftServer#getCommandMap()}); the Brigadier dispatcher inside the vanilla commands object is found by type.
 *
 * <p>Finding a member by type instead of by its obfuscated name is lucko/commodore's approach
 * ({@code src/main/java/me/lucko/commodore/ReflectionCommodore.java}: the {@code MinecraftServer} method returning the commands object, then the method
 * returning Brigadier's dispatcher).
 */
final class CraftServerAccess {

    private final Server server;
    private final Object minecraftServer;

    private CraftServerAccess(Server server, Object minecraftServer) {
        this.server = server;
        this.minecraftServer = minecraftServer;
    }

    static CraftServerAccess of(Server server) throws ReflectiveOperationException {
        Object minecraftServer = server.getClass().getMethod("getServer").invoke(server);
        if (minecraftServer == null) throw new IllegalStateException("CraftServer#getServer() returned null");
        return new CraftServerAccess(server, minecraftServer);
    }

    /** The {@code MinecraftServer}; it is also the {@code CommandSource} for output sent to the console. */
    Object minecraftServer() {
        return minecraftServer;
    }

    /**
     * The Brigadier dispatcher holding only the vanilla commands: CraftBukkit's {@code vanillaCommandDispatcher}
     * (Mojang {@code Commands}), whose one non-static {@code CommandDispatcher} field is the tree. CraftBukkit wraps each
     * of its root commands in a {@code VanillaCommandWrapper} and copies them into the live dispatcher when it syncs.
     */
    CommandDispatcher<?> vanillaDispatcher() throws ReflectiveOperationException {
        Field vanilla = field(minecraftServer.getClass(), "vanillaCommandDispatcher");
        Object commands = vanilla.get(minecraftServer);
        if (commands == null) throw new IllegalStateException("MinecraftServer.vanillaCommandDispatcher is not set yet");
        Field tree = null;
        for (Class<?> type = commands.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field candidate : type.getDeclaredFields()) {
                if (Modifier.isStatic(candidate.getModifiers()) || candidate.getType() != CommandDispatcher.class) continue;
                if (tree != null) {
                    throw new IllegalStateException("More than one Brigadier dispatcher field in " + commands.getClass().getName());
                }
                tree = candidate;
            }
        }
        if (tree == null) throw new NoSuchFieldException("No Brigadier dispatcher field in " + commands.getClass().getName());
        tree.setAccessible(true);
        Object dispatcher = tree.get(commands);
        if (!(dispatcher instanceof CommandDispatcher<?>)) throw new IllegalStateException("The vanilla Brigadier dispatcher is not set");
        return (CommandDispatcher<?>) dispatcher;
    }

    /**
     * Whether CraftBukkit has already copied the vanilla commands into its command map and synced them to the live
     * dispatcher, i.e. the plugin is being enabled after startup ({@code /reload}, a plugin manager). During a normal
     * start, plugins enable before CraftBukkit's {@code setVanillaCommands}, which registers {@code minecraft:<name>}.
     */
    boolean vanillaCommandsRegistered() throws ReflectiveOperationException {
        Object commandMap = commandMap();
        return commandMap.getClass().getMethod("getCommand", String.class).invoke(commandMap, "minecraft:gamemode") != null;
    }

    /**
     * Rebuilds the live dispatcher from the command map and resends every player's command tree, so the
     * {@code minecraft:} copies take the new requirements. Public from 1.16, private before; EssentialsX's
     * {@code ReflSyncCommandsProvider} calls it the same way
     * ({@code providers/NMSReflectionProvider/src/main/java/net/ess3/nms/refl/providers/ReflSyncCommandsProvider.java}).
     */
    void syncCommands() throws ReflectiveOperationException {
        Method sync = method(server.getClass(), "syncCommands");
        sync.invoke(server);
    }

    /**
     * Every command in CraftBukkit's command map, through {@code getKnownCommands()} where the map has it
     * ({@code CraftCommandMap} or Paper's API) and the protected {@code SimpleCommandMap#knownCommands} field otherwise,
     * which is how CommandAPI reads it
     * ({@code SafeVarHandle.ofOrNull(SimpleCommandMap.class, "knownCommands", ...)}).
     */
    @SuppressWarnings("unchecked")
    Collection<Command> knownCommands() throws ReflectiveOperationException {
        Object commandMap = commandMap();
        Object known;
        try {
            known = commandMap.getClass().getMethod("getKnownCommands").invoke(commandMap);
        } catch (NoSuchMethodException absent) {
            known = field(commandMap.getClass(), "knownCommands").get(commandMap);
        }
        if (!(known instanceof Map<?, ?>)) throw new IllegalStateException("The command map has no known commands map");
        return new ArrayList<>(((Map<String, Command>) known).values());
    }

    private Object commandMap() throws ReflectiveOperationException {
        Object commandMap = server.getClass().getMethod("getCommandMap").invoke(server);
        if (commandMap == null) throw new IllegalStateException("CraftServer#getCommandMap() returned null");
        return commandMap;
    }

    /**
     * CraftBukkit's {@code VanillaCommandWrapper}, in the {@code command} package next to {@code CraftServer}
     * ({@code org.bukkit.craftbukkit.v1_16_R3.command} up to 1.20.4, {@code org.bukkit.craftbukkit.command} on Paper 1.20.5).
     */
    Class<?> vanillaCommandWrapperType() throws ClassNotFoundException {
        Package craft = server.getClass().getPackage();
        if (craft == null) throw new ClassNotFoundException("The CraftServer class has no package");
        return Class.forName(craft.getName() + ".command.VanillaCommandWrapper", false, server.getClass().getClassLoader());
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // keep walking up
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static Method method(Class<?> type, String name) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // keep walking up
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name + "()");
    }
}
