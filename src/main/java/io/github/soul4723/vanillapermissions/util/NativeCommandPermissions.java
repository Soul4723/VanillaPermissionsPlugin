package io.github.soul4723.vanillapermissions.util;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.function.Predicate;
import java.util.concurrent.atomic.AtomicBoolean;

/** Installs LuckPerms checks on Paper's underlying vanilla Brigadier tree. */
public final class NativeCommandPermissions {

    private static final List<Mutation> INSTALLED = new ArrayList<>();
    private static final AtomicBoolean SENDER_LOOKUP_WARNING = new AtomicBoolean();
    private static JavaPlugin plugin;

    private NativeCommandPermissions() {}

    public static void register(JavaPlugin owner) {
        plugin = owner;
        owner.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            if (!owner.isEnabled() || !isCommandFeatureEnabled(owner)) return;
            try {
                install(event.registrar().getDispatcher());
            } catch (ReflectiveOperationException | RuntimeException error) {
                owner.getLogger().log(Level.SEVERE, "Could not install native command permissions for this Paper build", error);
                owner.getServer().getPluginManager().disablePlugin(owner);
            }
        });
    }

    public static synchronized void uninstall() {
        for (int i = INSTALLED.size() - 1; i >= 0; i--) {
            Mutation mutation = INSTALLED.get(i);
            try {
                if (mutation.node().getRequirement() == mutation.wrapper()) {
                    mutation.field().set(mutation.node(), mutation.original());
                }
            } catch (IllegalAccessException | RuntimeException ignored) {
                if (plugin != null) plugin.getLogger().warning("Could not restore a native command permission predicate");
            }
        }
        INSTALLED.clear();
        NativeGameModeTree.uninstall();
        NativeExecuteCommandExtensions.clear();
        plugin = null;
    }

    private static boolean isCommandFeatureEnabled(JavaPlugin owner) {
        return !(owner instanceof io.github.soul4723.vanillapermissions.VanillaPermissionsPlugin vanilla)
            || vanilla.isFeatureEnabled("command_permissions");
    }

    public static CommandDispatcher<?> getNativeDispatcher(CommandDispatcher<?> apiDispatcher) throws ReflectiveOperationException {
        Object root = apiDispatcher.getRoot();
        Method getNativeDispatcher = root.getClass().getMethod("getDispatcher");
        if (!getNativeDispatcher.trySetAccessible()) {
            throw new IllegalAccessException("Paper API mirror does not expose its native dispatcher");
        }
        Object value = getNativeDispatcher.invoke(root);
        if (!(value instanceof CommandDispatcher<?> nativeDispatcher)) {
            throw new IllegalStateException("Paper API mirror returned an unexpected dispatcher: " + value);
        }
        return nativeDispatcher;
    }

    private static synchronized void install(CommandDispatcher<?> apiDispatcher) throws ReflectiveOperationException {
        CommandDispatcher<?> nativeDispatcher = getNativeDispatcher(apiDispatcher);
        NativeExecuteCommandExtensions.install(plugin, nativeDispatcher);
        NativeGameModeTree.install(nativeDispatcher);

        ClassLoader serverLoader = org.bukkit.Bukkit.getServer().getClass().getClassLoader();
        Class<?> sourceClass = Class.forName("net.minecraft.commands.CommandSourceStack", false, serverLoader);
        Method getSender = sourceClass.getMethod("getBukkitSender");
        if (!CommandSender.class.isAssignableFrom(getSender.getReturnType())) {
            throw new IllegalStateException("Native command source has unexpected Bukkit sender type");
        }

        Field requirement = CommandNode.class.getDeclaredField("requirement");
        if (!requirement.trySetAccessible()) {
            throw new IllegalAccessException("Brigadier command requirement field is inaccessible");
        }

        List<Mutation> pending = new ArrayList<>();
        Set<CommandNode<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (CommandNode<?> node : nativeDispatcher.getRoot().getChildren()) {
            collect(node, node.getName(), getSender, requirement, visited, pending);
        }

        List<Mutation> applied = new ArrayList<>();
        try {
            for (Mutation mutation : pending) {
                mutation.field().set(mutation.node(), mutation.wrapper());
                applied.add(mutation);
                if (mutation.node().getRequirement() != mutation.wrapper()) {
                    throw new IllegalStateException("Brigadier did not retain the replacement requirement for " + mutation.path());
                }
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            restore(applied);
            throw error;
        }
        INSTALLED.addAll(applied);
        plugin.getLogger().info("Applied LuckPerms permissions to " + applied.size() + " native command nodes");
    }

    private static void collect(
        CommandNode<?> node,
        String path,
        Method getSender,
        Field requirementField,
        Set<CommandNode<?>> visited,
        List<Mutation> pending
    ) throws IllegalAccessException {
        if (!visited.add(node)) return;

        @SuppressWarnings("unchecked")
        Predicate<Object> current = (Predicate<Object>) requirementField.get(node);
        Predicate<Object> fallback = current;
        if (current instanceof PermissionPredicate previous) {
            fallback = previous.fallback;
            if (previous.path.equals(path)) {
                for (CommandNode<?> child : node.getChildren()) {
                    collect(child, path + "." + child.getName(), getSender, requirementField, visited, pending);
                }
                return;
            }
        }

        PermissionPredicate wrapper = new PermissionPredicate(path, fallback, getSender);
        pending.add(new Mutation(node, requirementField, current, wrapper, path));
        for (CommandNode<?> child : node.getChildren()) {
            collect(child, path + "." + child.getName(), getSender, requirementField, visited, pending);
        }
    }

    private static void restore(List<Mutation> mutations) {
        for (int i = mutations.size() - 1; i >= 0; i--) {
            Mutation mutation = mutations.get(i);
            try {
                if (mutation.node().getRequirement() == mutation.wrapper()) {
                    mutation.field().set(mutation.node(), mutation.original());
                }
            } catch (IllegalAccessException | RuntimeException ignored) {
                if (plugin != null) plugin.getLogger().severe("Failed to roll back native command permission installation");
            }
        }
    }

    private record Mutation(
        CommandNode<?> node,
        Field field,
        Predicate<?> original,
        Predicate<?> wrapper,
        String path
    ) {}

    private static final class PermissionPredicate implements Predicate<Object> {
        private final String path;
        private final Predicate<Object> fallback;
        private final Method getSender;
        private final String permission;

        private PermissionPredicate(String path, Predicate<Object> fallback, Method getSender) {
            this.path = path;
            this.fallback = fallback;
            this.getSender = getSender;
            this.permission = "minecraft.command." + path;
        }

        @Override
        public boolean test(Object source) {
            CommandSender sender;
            try {
                sender = (CommandSender) getSender.invoke(source);
            } catch (ReflectiveOperationException | RuntimeException error) {
                if (plugin != null && SENDER_LOOKUP_WARNING.compareAndSet(false, true)) {
                    plugin.getLogger().warning("Native command sender lookup failed; using vanilla requirements: " + error.getMessage());
                }
                return fallback.test(source);
            }
            return switch (PermissionManager.queryPermission(sender, permission)) {
                case TRUE -> true;
                case FALSE -> false;
                case UNDEFINED -> fallback.test(source);
            };
        }
    }
}
