package io.github.soul4723.vanillapermissions.util;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
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
    private static final List<CommandMutation> SELECTOR_INSTALLED = new ArrayList<>();
    private static final AtomicBoolean SENDER_LOOKUP_WARNING = new AtomicBoolean();
    private static JavaPlugin plugin;

    private NativeCommandPermissions() {}

    public static void register(JavaPlugin owner) {
        plugin = owner;
        owner.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            if (!owner.isEnabled()) return;
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
        for (int i = SELECTOR_INSTALLED.size() - 1; i >= 0; i--) {
            CommandMutation mutation = SELECTOR_INSTALLED.get(i);
            try {
                if (mutation.node().getCommand() == mutation.wrapper()) {
                    mutation.field().set(mutation.node(), mutation.original());
                }
            } catch (IllegalAccessException | RuntimeException error) {
                if (plugin != null) plugin.getLogger().warning("Could not restore native selector command executor");
            }
        }
        SELECTOR_INSTALLED.clear();
        JavaPlugin owner = plugin;
        try {
            NativeGameModeTree.uninstall();
        } catch (RuntimeException error) {
            if (owner != null) owner.getLogger().log(Level.SEVERE, "Could not restore native gamemode command tree", error);
        }
        try {
            NativeExecuteCommandExtensions.clear();
        } catch (RuntimeException error) {
            if (owner != null) owner.getLogger().log(Level.SEVERE, "Could not remove native /execute extensions", error);
        } finally {
            try {
                refreshOnlinePlayers(owner);
            } finally {
                plugin = null;
            }
        }
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
        Field command = CommandNode.class.getDeclaredField("command");
        if (!command.trySetAccessible()) {
            throw new IllegalAccessException("Brigadier command field is inaccessible");
        }

        List<Mutation> pending = new ArrayList<>();
        Set<CommandNode<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (CommandNode<?> node : nativeDispatcher.getRoot().getChildren()) {
            collect(node, node.getName(), getSender, requirement, command, visited, pending);
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
        if (!applied.isEmpty()) refreshOnlinePlayers(plugin);
    }

    private static void refreshOnlinePlayers(JavaPlugin owner) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                player.updateCommands();
            } catch (RuntimeException error) {
                if (owner != null) owner.getLogger().log(Level.WARNING,
                    "Could not refresh command tree for " + player.getName(), error);
            }
        }
    }

    private static void collect(
        CommandNode<?> node,
        String path,
        Method getSender,
        Field requirementField,
        Field commandField,
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
                    collect(child, path + "." + child.getName(), getSender, requirementField, commandField, visited, pending);
                }
                return;
            }
        }

        PermissionPredicate wrapper = new PermissionPredicate(path, fallback, getSender);
        pending.add(new Mutation(node, requirementField, current, wrapper, path));
        installSelectorExecutor(node, commandField);
        for (CommandNode<?> child : node.getChildren()) {
            collect(child, path + "." + child.getName(), getSender, requirementField, commandField, visited, pending);
        }
    }

    @SuppressWarnings("unchecked")
    private static void installSelectorExecutor(CommandNode<?> node, Field commandField) throws IllegalAccessException {
        Command<?> original = node.getCommand();
        if (original == null || original instanceof SelectorCommand) return;
        SelectorCommand wrapper = new SelectorCommand((Command<Object>) original);
        commandField.set(node, wrapper);
        if (node.getCommand() != wrapper) throw new IllegalStateException("Brigadier did not retain selector executor wrapper");
        SELECTOR_INSTALLED.add(new CommandMutation(node, commandField, original, wrapper));
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

    private record CommandMutation(CommandNode<?> node, Field field, Command<?> original, Command<?> wrapper) {}

    private static final class SelectorCommand implements Command<Object> {
        private final Command<Object> fallback;

        private SelectorCommand(Command<Object> fallback) {
            this.fallback = fallback;
        }

        @Override
        public int run(CommandContext<Object> context) throws CommandSyntaxException {
            validateSelectors(context);
            return fallback.run(context);
        }
    }

    private static void validateSelectors(CommandContext<?> context) throws CommandSyntaxException {
        Object rawSource = context.getSource();
        CommandSender sender;
        Method getSender;
        try {
            getSender = rawSource.getClass().getMethod("getBukkitSender");
        } catch (NoSuchMethodException error) {
            return;
        }
        Object value;
        try {
            value = getSender.invoke(rawSource);
        } catch (ReflectiveOperationException error) {
            return;
        }
        if (!(value instanceof CommandSender commandSender)) return;
        sender = commandSender;
        List<String> path = context.getNodes().stream().map(ParsedCommandNode::getNode)
            .map(CommandNode::getName).toList();
        for (ParsedCommandNode<?> parsed : context.getNodes()) {
            if (!(parsed.getNode() instanceof ArgumentCommandNode<?, ?> argument)) continue;
            Method resolver = selectorResolver(argument.getType());
            if (resolver == null) continue;
            Object result;
            try {
                result = resolver.invoke(null, context, argument.getName());
            } catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                if (cause instanceof CommandSyntaxException syntax) throw syntax;
                throw new IllegalStateException("Native selector resolver failed", cause);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Native selector resolver failed", error);
            }
            SelectorNativePermissions.validate(sender, path, argument.getName(), result);
        }
    }

    private static Method selectorResolver(Object argumentType) {
        String name = argumentType.getClass().getName();
        String methodName = switch (name) {
            case "net.minecraft.commands.arguments.EntityArgument" -> "getEntities";
            case "net.minecraft.commands.arguments.GameProfileArgument" -> "getGameProfiles";
            case "net.minecraft.commands.arguments.ScoreHolderArgument" -> "getNames";
            default -> null;
        };
        if (methodName == null) return null;
        for (Method method : argumentType.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 2
                    && java.lang.reflect.Modifier.isStatic(method.getModifiers())) return method;
        }
        throw new IllegalStateException("Native selector resolver is missing: " + name + "." + methodName);
    }

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
