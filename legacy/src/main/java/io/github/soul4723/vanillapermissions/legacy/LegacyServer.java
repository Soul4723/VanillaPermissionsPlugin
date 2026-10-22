package io.github.soul4723.vanillapermissions.legacy;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.FormattedCommandAlias;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The server side of {@link CommandGuard} on CraftBukkit 1.8.8 - 1.12.2: the command map, {@code commands.yml}
 * aliases, and {@code @} selector resolution through NMS {@code PlayerSelector}.
 *
 * <p>Every NMS member used here has the same CraftBukkit-mapped name on v1_8_R3 through v1_12_R1:
 * {@code MinecraftServer.getServer()}, its public {@code worldServer} array and {@code worlds} list,
 * {@code CraftEntity.getHandle()}, {@code Entity.getBukkitEntity()}, and {@code PlayerSelector}'s static
 * {@code (ICommandListener, String, Class)} method, which is found by signature.
 */
final class LegacyServer implements CommandGuard.Server {

    private final Logger logger;
    private final Server server;
    private boolean resolveFailureLogged;
    private final SimpleCommandMap commandMap;
    private final Class<?> vanillaWrapper;
    private final Field aliasFormats;
    private final Method aliasBuild;
    private final boolean aliasBuildTakesSender;

    private final Class<?> listenerType;
    private final Method getPlayers;
    private final Method getHandle;
    private final Method getBukkitEntity;
    private final Method listenerWorld;
    private final Class<?> nmsEntity;
    private final Class<?> nmsPlayer;
    private final Object minecraftServer;
    private final Field worldServer;
    private final Field worlds;

    /** Looks every member up now, so a server this cannot handle fails at start-up instead of failing open later. */
    LegacyServer(Server server, Logger logger) throws ReflectiveOperationException {
        this.logger = logger;
        this.server = server;
        String craft = server.getClass().getPackage().getName(); // org.bukkit.craftbukkit.v1_8_R3
        String nms = "net.minecraft.server." + craft.substring(craft.lastIndexOf('.') + 1);
        ClassLoader loader = server.getClass().getClassLoader();

        commandMap = (SimpleCommandMap) server.getClass().getMethod("getCommandMap").invoke(server);
        vanillaWrapper = Class.forName(craft + ".command.VanillaCommandWrapper", false, loader);
        // EssentialsX's ReflFormattedCommandAliasProvider reads the same two private members.
        aliasFormats = FormattedCommandAlias.class.getDeclaredField("formatStrings");
        aliasFormats.setAccessible(true);
        // Paper 1.12.2 added the sender as a first parameter; Spigot has (String, String[]).
        Method build;
        try {
            build = FormattedCommandAlias.class.getDeclaredMethod("buildCommand", CommandSender.class, String.class, String[].class);
        } catch (NoSuchMethodException e) {
            build = FormattedCommandAlias.class.getDeclaredMethod("buildCommand", String.class, String[].class);
        }
        build.setAccessible(true);
        aliasBuild = build;
        aliasBuildTakesSender = build.getParameterTypes().length == 3;

        listenerType = Class.forName(nms + ".ICommandListener", false, loader);
        nmsEntity = Class.forName(nms + ".Entity", false, loader);
        nmsPlayer = Class.forName(nms + ".EntityPlayer", false, loader);
        Method found = null;
        for (Method method : Class.forName(nms + ".PlayerSelector", false, loader).getMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (Modifier.isStatic(method.getModifiers()) && List.class.isAssignableFrom(method.getReturnType())
                    && types.length == 3 && types[0] == listenerType && types[1] == String.class && types[2] == Class.class) {
                if (found != null) throw new NoSuchMethodException("two PlayerSelector(ICommandListener, String, Class) methods in " + nms);
                found = method;
            }
        }
        if (found == null) throw new NoSuchMethodException("no PlayerSelector(ICommandListener, String, Class) method in " + nms);
        getPlayers = found;
        getHandle = Class.forName(craft + ".entity.CraftEntity", false, loader).getMethod("getHandle");
        getBukkitEntity = nmsEntity.getMethod("getBukkitEntity");
        listenerWorld = listenerType.getMethod("getWorld");
        Class<?> serverType = Class.forName(nms + ".MinecraftServer", false, loader);
        minecraftServer = serverType.getMethod("getServer").invoke(null);
        worldServer = serverType.getField("worldServer");
        worlds = serverType.getField("worlds");
        if (minecraftServer == null || !worldServer.getType().isArray() || !List.class.isAssignableFrom(worlds.getType())) {
            throw new IllegalStateException("unexpected MinecraftServer world fields in " + nms);
        }
    }

    @Override
    public Command command(String label) {
        return commandMap.getCommand(label);
    }

    @Override
    public boolean commandBlockOverride(String token) {
        try {
            return Boolean.TRUE.equals(server.getClass().getMethod("getCommandBlockOverride", String.class).invoke(server, token));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            return false;
        }
    }

    @Override
    public List<String> scoreHolders() {
        return new ArrayList<>(server.getScoreboardManager().getMainScoreboard().getEntries());
    }

    @Override
    public boolean isVanilla(Command command) {
        return vanillaWrapper.isInstance(command);
    }

    /** Names of the vanilla commands registered now (CraftBukkit's wrapper names, without {@code minecraft:}). */
    List<String> vanillaCommandNames() {
        List<String> names = new ArrayList<>();
        for (Command command : new ArrayList<>(commandMap.getCommands())) {
            if (isVanilla(command) && !names.contains(command.getName())) names.add(command.getName());
        }
        return names;
    }

    @Override
    public List<String> expandAlias(Command command, CommandSender sender, String[] args) {
        if (!(command instanceof FormattedCommandAlias)) return null;
        List<String> lines = new ArrayList<>();
        try {
            for (String format : (String[]) aliasFormats.get(command)) {
                try {
                    String line = (String) (aliasBuildTakesSender
                        ? aliasBuild.invoke(command, sender, format, args) : aliasBuild.invoke(command, format, args));
                    if (line != null) lines.add(line.trim());
                } catch (InvocationTargetException e) {
                    // Missing arguments: CraftBukkit refuses the alias the same way when it runs it.
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return lines;
    }

    @Override
    public List<Entity> resolve(Entity origin, String token, boolean playersOnly) {
        if (!Bukkit.isPrimaryThread()) return null;
        if (!token.startsWith("@")) return byNameOrId(token, playersOnly);
        Object previous = null;
        boolean swapped = false;
        try {
            Object handle = getHandle.invoke(origin);
            Object listener = permissiveListener(handle);
            previous = worldServer.get(minecraftServer);
            worldServer.set(minecraftServer, worldsFor(listenerWorld.invoke(handle)));
            swapped = true;
            List<?> found = (List<?>) getPlayers.invoke(null, listener, token, playersOnly ? nmsPlayer : nmsEntity);
            List<Entity> entities = new ArrayList<>(found.size());
            for (Object entity : found) entities.add((Entity) getBukkitEntity.invoke(entity));
            return entities;
        } catch (InvocationTargetException e) {
            // Includes vanilla's CommandException for a malformed selector: vanilla would refuse it too.
            log(token, e.getCause());
            return null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            log(token, e);
            return null;
        } finally {
            if (swapped) {
                try {
                    worldServer.set(minecraftServer, previous);
                } catch (IllegalAccessException e) {
                    logger.log(Level.SEVERE, "Could not restore MinecraftServer.worldServer after resolving a selector", e);
                }
            }
        }
    }

    private void log(String token, Throwable cause) {
        if (resolveFailureLogged) return;
        resolveFailureLogged = true;
        logger.log(Level.WARNING, "Could not resolve selector " + token, cause);
    }

    /**
     * {@code MinecraftServer.worldServer} as {@code VanillaCommandWrapper#dispatchVanillaCommand} fills it: the
     * sender's world first, then the others. CraftBukkit leaves it full of {@code null}s otherwise, and
     * {@code PlayerSelector} then finds nothing, so the check would pass.
     */
    private Object worldsFor(Object own) throws IllegalAccessException {
        List<?> all = (List<?>) worlds.get(minecraftServer);
        Object array = Array.newInstance(worldServer.getType().getComponentType(), all.size());
        if (all.isEmpty()) return array;
        int index = 0;
        Array.set(array, index++, own);
        for (Object world : all) {
            if (world != own && index < all.size()) Array.set(array, index++, world);
        }
        return array;
    }

    /**
     * The entity as an {@code ICommandListener} that may use selectors. Vanilla resolves a selector with the sender's
     * selector permission, but inside {@code /execute} that is the original player's, not the target's; and the
     * plugin's own {@code minecraft.selector} check already ran. Resolving as if allowed gives every entity vanilla
     * could select, so the selector rules see the full set.
     */
    private Object permissiveListener(final Object handle) {
        InvocationHandler handler = (proxy, method, args) -> {
            Class<?>[] types = method.getParameterTypes();
            if (method.getReturnType() == boolean.class && types.length == 2 && types[0] == int.class && types[1] == String.class) {
                return true;
            }
            if (method.getName().equals("sendMessage") || method.getReturnType() == void.class && types.length == 2) {
                return null; // No chat output and no command stats from a check.
            }
            if (method.getDeclaringClass() == Object.class) {
                if (method.getName().equals("equals")) return proxy == args[0];
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("toString")) return "VanillaPermissions selector check for " + handle;
            }
            try {
                return method.invoke(handle, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        };
        return Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[] {listenerType}, handler);
    }

    /** A plain argument: an online player by name (ignoring case, as vanilla), else an entity by UUID. */
    private static List<Entity> byNameOrId(String token, boolean playersOnly) {
        Player player = Bukkit.getPlayerExact(token);
        if (player != null) return Collections.<Entity>singletonList(player);
        UUID id;
        try {
            id = UUID.fromString(token);
        } catch (IllegalArgumentException e) {
            return Collections.emptyList(); // Vanilla refuses an unknown name itself.
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getUniqueId().equals(id)) {
                    return playersOnly && !(entity instanceof Player) ? Collections.<Entity>emptyList() : Collections.singletonList(entity);
                }
            }
        }
        return Collections.emptyList();
    }
}
