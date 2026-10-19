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
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.logging.Level;

/** Installs LuckPerms checks on Paper's underlying vanilla Brigadier tree. */
public final class NativeCommandPermissions {

    private static final List<Mutation> INSTALLED = new ArrayList<>();
    private static final List<CommandMutation> SELECTOR_INSTALLED = new ArrayList<>();
    private static final String VANILLA_NAMESPACE = "minecraft:";
    private static final AtomicBoolean SENDER_LOOKUP_WARNING = new AtomicBoolean();
    private static JavaPlugin plugin;
    private static int checkedNodes;
    private static Field selectorBypass;
    private static Method nativePermission;

    private NativeCommandPermissions() {}

    public static void register(JavaPlugin owner) {
        plugin = owner;
        owner.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            if (!owner.isEnabled()) return;
            try {
                install(event.registrar().getDispatcher());
            } catch (ReflectiveOperationException | RuntimeException error) {
                owner.getLogger().log(Level.SEVERE, "Could not add LuckPerms checks to vanilla commands on this server build "
                    + "(only Paper 1.20.6 is supported); disabling VanillaPermissionsPlugin so no command is left half-checked", error);
                uninstall();
                // Disabling inside a lifecycle event removes handlers Paper is iterating, which crashes the server.
                owner.getServer().getScheduler().runTask(owner, () -> owner.getServer().getPluginManager().disablePlugin(owner));
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
        checkedNodes = 0;
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

    /** Number of vanilla command nodes currently carrying a LuckPerms check. */
    public static synchronized int installedNodeCount() {
        return checkedNodes;
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

        ClassLoader serverLoader = Bukkit.getServer().getClass().getClassLoader();
        Class<?> sourceClass = Class.forName("net.minecraft.commands.CommandSourceStack", false, serverLoader);
        Method getSender = sourceClass.getMethod("getBukkitSender");
        selectorBypass = sourceClass.getField("bypassSelectorPermissions");
        nativePermission = sourceClass.getMethod("hasPermission", int.class, String.class);
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

        List<VanillaRoot> roots = vanillaRoots(nativeDispatcher.getRoot());
        if (roots.isEmpty()) throw new IllegalStateException("Paper did not register any minecraft: command aliases");
        List<Mutation> pending = new ArrayList<>();
        Set<CommandNode<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (VanillaRoot root : roots) {
            collect(root.node(), root.path(), getSender, requirement, command, visited, pending);
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
        // Counted over the live tree so a reload with a fresh dispatcher does not inflate the total.
        checkedNodes = (int) visited.stream().filter(node -> node.getRequirement() instanceof PermissionPredicate).count();
        plugin.getLogger().info("Added LuckPerms checks to " + checkedNodes + " vanilla command nodes");
        if (!applied.isEmpty()) refreshOnlinePlayers(plugin);
    }

    /**
     * The vanilla command nodes to check, with their permission paths. Paper's dispatcher also holds Bukkit and
     * plugin commands, so vanilla commands are found through the {@code minecraft:<name>} alias Paper registers for
     * each of them. The alias shares the command's node, so it cannot bypass {@code minecraft.command.<name>}, and
     * its redirect still reaches the vanilla command when a plugin has taken over the plain name.
     */
    static List<VanillaRoot> vanillaRoots(CommandNode<?> dispatcherRoot) {
        List<VanillaRoot> roots = new ArrayList<>();
        // Identity, not equals: Brigadier nodes compare by structure.
        Set<CommandNode<?>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (CommandNode<?> alias : dispatcherRoot.getChildren()) {
            if (!alias.getName().startsWith(VANILLA_NAMESPACE) || alias.getRedirect() == null) continue;
            String name = alias.getName().substring(VANILLA_NAMESPACE.length());
            CommandNode<?> target = alias.getRedirect();
            CommandNode<?> plain = dispatcherRoot.getChild(name);
            if (seen.add(alias)) roots.add(new VanillaRoot(alias, name));
            if (seen.add(target)) roots.add(new VanillaRoot(target, target.getName()));
            if (plain != null && flatten(plain) == target && seen.add(plain)) roots.add(new VanillaRoot(plain, name));
        }
        return roots;
    }

    record VanillaRoot(CommandNode<?> node, String path) {}

    /** The vanilla node behind {@code minecraft:<name>}, even if a plugin command now owns the plain name. */
    static CommandNode<?> vanillaCommand(CommandDispatcher<?> dispatcher, String name) {
        CommandNode<?> alias = dispatcher.getRoot().getChild(VANILLA_NAMESPACE + name);
        return alias != null && alias.getRedirect() != null ? alias.getRedirect() : dispatcher.getRoot().getChild(name);
    }

    private static CommandNode<?> flatten(CommandNode<?> node) {
        while (node.getRedirect() != null) node = node.getRedirect();
        return node;
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
            Object source = context.getSource();
            CommandSender sender = commandSender(source);
            boolean hasSelectors = sender instanceof Player && hasSelectorArguments(context);
            if (!hasSelectors) {
                validateSelectors(context);
                return fallback.run(context);
            }

            try {
                boolean previous = selectorBypass.getBoolean(source);
                boolean vanillaAllowed = previous || (boolean) nativePermission.invoke(
                    source, 2, "minecraft.command.selector");
                SelectorNativePermissions.validateSelectorSyntax(sender, vanillaAllowed);
                selectorBypass.setBoolean(source, true);
                try {
                    validateSelectors(context);
                    return fallback.run(context);
                } finally {
                    selectorBypass.setBoolean(source, previous);
                }
            } catch (InvocationTargetException error) {
                throw reflectionFailure(error);
            } catch (IllegalAccessException error) {
                throw new IllegalStateException("Could not apply native selector permission", error);
            }
        }
    }

    public static boolean isSelectorArgument(CommandContext<?> context, String name) {
        return hasSelectorArguments(context, name);
    }

    private static boolean hasSelectorArguments(CommandContext<?> context) {
        return hasSelectorArguments(context, null);
    }

    private static boolean hasSelectorArguments(CommandContext<?> context, String name) {
        for (ParsedCommandNode<?> parsed : context.getNodes()) {
            if (!(parsed.getNode() instanceof ArgumentCommandNode<?, ?> argument)
                    || name != null && !argument.getName().equals(name)
                    || selectorResolver(argument.getType()) == null) continue;
            if (parsed.getRange().get(context.getInput()).startsWith("@")) return true;
        }
        return false;
    }

    private static CommandSender commandSender(Object source) {
        try {
            return (CommandSender) source.getClass().getMethod("getBukkitSender").invoke(source);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return null;
        }
    }

    private static RuntimeException reflectionFailure(InvocationTargetException error) throws CommandSyntaxException {
        Throwable cause = error.getCause();
        if (cause instanceof CommandSyntaxException syntax) throw syntax;
        if (cause instanceof RuntimeException runtime) return runtime;
        return new IllegalStateException("Native selector permission check failed", cause);
    }

    private static void validateSelectors(CommandContext<?> context) throws CommandSyntaxException {
        if (!(commandSender(context.getSource()) instanceof CommandSender sender)) return;
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
                    && Modifier.isStatic(method.getModifiers())) return method;
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
