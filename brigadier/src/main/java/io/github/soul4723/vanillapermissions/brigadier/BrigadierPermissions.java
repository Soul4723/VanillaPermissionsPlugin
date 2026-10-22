package io.github.soul4723.vanillapermissions.brigadier;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import io.github.soul4723.vanillapermissions.platform.AdapterLog;
import io.github.soul4723.vanillapermissions.platform.PluginScheduler;
import io.github.soul4723.vanillapermissions.selector.RandomSelectors;
import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Installs permission checks on a server's vanilla Brigadier tree: a {@code minecraft.command.<path>} requirement on
 * every vanilla node, a selector check around every vanilla executor, the literal {@code /gamemode} modes and the
 * {@code /execute} extensions. Everything server-specific comes from a {@link BrigadierBridge}. One instance per
 * enabled adapter.
 */
public final class BrigadierPermissions {

    private static volatile Field contextArgumentsField;

    private final List<Mutation> installed = new ArrayList<>();
    private final List<CommandMutation> selectorInstalled = new ArrayList<>();
    private final AtomicBoolean senderLookupWarning = new AtomicBoolean();
    private final BrigadierBridge bridge;
    private final GameModeLiterals gameModes;
    private final ExecuteExtensions execute;
    private final JavaPlugin owner;
    private final PluginScheduler scheduler;
    /** Where warnings go; {@code null} once uninstalled, so late callbacks stay quiet. */
    private JavaPlugin plugin;
    private int checkedNodes;

    public BrigadierPermissions(JavaPlugin plugin, BrigadierBridge bridge, PluginScheduler scheduler) {
        this.owner = plugin;
        this.scheduler = scheduler;
        this.plugin = plugin;
        this.bridge = bridge;
        this.gameModes = new GameModeLiterals(bridge);
        this.execute = new ExecuteExtensions(this, bridge);
    }

    /**
     * Patches {@code dispatcher}, the server's native vanilla dispatcher. Safe to call again for a rebuilt dispatcher
     * (Paper fires its COMMANDS event again on {@code /minecraft:reload}). On failure the requirements set by this
     * call are rolled back and the error is thrown; call {@link #uninstall()} to also undo the other changes.
     */
    public synchronized void install(CommandDispatcher<?> dispatcher) throws ReflectiveOperationException {
        plugin = owner;
        bridge.prepare(dispatcher);
        execute.install(owner, dispatcher);
        gameModes.install(dispatcher);

        Field requirement = VanillaTree.accessible(CommandNode.class.getDeclaredField("requirement"),
                "Brigadier command requirement field is inaccessible");
        Field command = VanillaTree.accessible(CommandNode.class.getDeclaredField("command"),
                "Brigadier command field is inaccessible");

        List<VanillaTree.VanillaRoot> roots = bridge.vanillaRoots(dispatcher);
        if (roots.isEmpty()) throw new IllegalStateException("No vanilla commands were found in the server's command dispatcher");
        List<Mutation> pending = new ArrayList<>();
        Set<CommandNode<?>> visited = Collections.newSetFromMap(new IdentityHashMap<CommandNode<?>, Boolean>());
        for (VanillaTree.VanillaRoot root : roots) {
            collect(root.node(), root.path(), requirement, command, visited, pending);
        }

        List<Mutation> applied = new ArrayList<>();
        try {
            for (Mutation mutation : pending) {
                mutation.field.set(mutation.node, mutation.wrapper);
                applied.add(mutation);
                if (mutation.node.getRequirement() != mutation.wrapper) {
                    throw new IllegalStateException("Brigadier did not retain the replacement requirement for " + mutation.path);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            restore(applied);
            throw error;
        }
        installed.addAll(applied);
        // Counted over the live tree so a reload with a fresh dispatcher does not inflate the total.
        int count = 0;
        for (CommandNode<?> node : visited) {
            if (node.getRequirement() instanceof PermissionPredicate) count++;
        }
        checkedNodes = count;
        owner.getLogger().info(AdapterLog.added(checkedNodes));
        if (!applied.isEmpty()) refreshOnlinePlayers(owner);
    }

    /**
     * Logs {@code message} (which should start with {@link AdapterLog#INSTALL_FAILED}) as SEVERE, restores vanilla
     * commands and disables the plugin on the next tick, so no command is left half-checked.
     */
    public void failAndDisable(String message, Throwable error) {
        owner.getLogger().log(Level.SEVERE, message, error);
        uninstall();
        // Disabling inside a server event (Paper's lifecycle event, plugin enable) can break the caller's iteration.
        scheduler.runGlobal(() -> owner.getServer().getPluginManager().disablePlugin(owner));
    }

    public synchronized void uninstall() {
        for (int i = installed.size() - 1; i >= 0; i--) {
            Mutation mutation = installed.get(i);
            try {
                if (mutation.node.getRequirement() == mutation.wrapper) {
                    mutation.field.set(mutation.node, mutation.original);
                }
            } catch (IllegalAccessException | RuntimeException ignored) {
                if (plugin != null) plugin.getLogger().warning("Could not restore a native command permission predicate; restart the server to restore vanilla commands");
            }
        }
        installed.clear();
        checkedNodes = 0;
        for (int i = selectorInstalled.size() - 1; i >= 0; i--) {
            CommandMutation mutation = selectorInstalled.get(i);
            try {
                if (mutation.node.getCommand() == mutation.wrapper) {
                    mutation.field.set(mutation.node, mutation.original);
                }
            } catch (IllegalAccessException | RuntimeException error) {
                if (plugin != null) plugin.getLogger().warning("Could not restore native selector command executor; restart the server to restore vanilla commands");
            }
        }
        selectorInstalled.clear();
        JavaPlugin logTarget = plugin;
        try {
            gameModes.uninstall();
        } catch (RuntimeException error) {
            if (logTarget != null) logTarget.getLogger().log(Level.SEVERE, "Could not restore native gamemode command tree", error);
        }
        try {
            execute.clear();
        } catch (RuntimeException error) {
            if (logTarget != null) logTarget.getLogger().log(Level.SEVERE, "Could not remove native /execute extensions", error);
        } finally {
            try {
                refreshOnlinePlayers(logTarget);
            } finally {
                plugin = null;
            }
        }
    }

    /** Number of vanilla command nodes currently carrying a permission check. */
    public synchronized int installedNodeCount() {
        return checkedNodes;
    }

    /**
     * Resends the command tree to online players, so their tab completion matches the patched tree. Each resend runs on
     * the player's own thread (Folia's entity scheduler), one tick later; when nothing can be scheduled any more
     * (the plugin is being disabled), it runs at once, as the server is stopping or reloading on this thread.
     */
    private void refreshOnlinePlayers(JavaPlugin logTarget) {
        refresh(new ArrayList<>(Bukkit.getOnlinePlayers()), logTarget);
    }

    void refresh(List<? extends Player> players, JavaPlugin logTarget) {
        for (Player player : players) {
            if (scheduler == null) {
                updateCommands(player, logTarget);
                continue;
            }
            try {
                scheduler.runLater(player, () -> updateCommands(player, logTarget), 1);
            } catch (RuntimeException unschedulable) {
                updateCommands(player, logTarget);
            }
        }
    }

    private static void updateCommands(Player player, JavaPlugin logTarget) {
        try {
            player.updateCommands();
        } catch (RuntimeException error) {
            if (logTarget != null) logTarget.getLogger().log(Level.WARNING,
                "Could not refresh the command list for " + player.getName(), error);
        }
    }

    private void collect(
        CommandNode<?> node,
        String path,
        Field requirementField,
        Field commandField,
        Set<CommandNode<?>> visited,
        List<Mutation> pending
    ) throws IllegalAccessException {
        if (!visited.add(node)) return;

        @SuppressWarnings("unchecked")
        Predicate<Object> current = (Predicate<Object>) requirementField.get(node);
        Predicate<Object> fallback = current;
        if (current instanceof PermissionPredicate) {
            PermissionPredicate previous = (PermissionPredicate) current;
            fallback = previous.fallback;
            if (previous.path.equals(path)) {
                for (CommandNode<?> child : node.getChildren()) {
                    collect(child, path + "." + child.getName(), requirementField, commandField, visited, pending);
                }
                return;
            }
        }

        PermissionPredicate wrapper = permissionPredicate(path, fallback);
        pending.add(new Mutation(node, requirementField, current, wrapper, path));
        installSelectorExecutor(node, commandField);
        for (CommandNode<?> child : node.getChildren()) {
            collect(child, path + "." + child.getName(), requirementField, commandField, visited, pending);
        }
    }

    @SuppressWarnings("unchecked")
    private void installSelectorExecutor(CommandNode<?> node, Field commandField) throws IllegalAccessException {
        Command<?> original = node.getCommand();
        if (original == null || original instanceof SelectorCommand) return;
        SelectorCommand wrapper = new SelectorCommand((Command<Object>) original);
        commandField.set(node, wrapper);
        if (node.getCommand() != wrapper) throw new IllegalStateException("Brigadier did not retain selector executor wrapper");
        selectorInstalled.add(new CommandMutation(node, commandField, original, wrapper));
    }

    private void restore(List<Mutation> mutations) {
        for (int i = mutations.size() - 1; i >= 0; i--) {
            Mutation mutation = mutations.get(i);
            try {
                if (mutation.node.getRequirement() == mutation.wrapper) {
                    mutation.field.set(mutation.node, mutation.original);
                }
            } catch (IllegalAccessException | RuntimeException ignored) {
                if (plugin != null) plugin.getLogger().severe("Could not roll back the native command permission installation");
            }
        }
    }

    private static final class Mutation {
        final CommandNode<?> node;
        final Field field;
        final Predicate<?> original;
        final Predicate<?> wrapper;
        final String path;

        Mutation(CommandNode<?> node, Field field, Predicate<?> original, Predicate<?> wrapper, String path) {
            this.node = node;
            this.field = field;
            this.original = original;
            this.wrapper = wrapper;
            this.path = path;
        }
    }

    private static final class CommandMutation {
        final CommandNode<?> node;
        final Field field;
        final Command<?> original;
        final Command<?> wrapper;

        CommandMutation(CommandNode<?> node, Field field, Command<?> original, Command<?> wrapper) {
            this.node = node;
            this.field = field;
            this.original = original;
            this.wrapper = wrapper;
        }
    }

    /** Checks selector permissions on a vanilla executor's arguments before it runs. */
    final class SelectorCommand implements Command<Object> {
        final Command<Object> fallback;

        SelectorCommand(Command<Object> fallback) {
            this.fallback = fallback;
        }

        @Override
        public int run(CommandContext<Object> context) throws CommandSyntaxException {
            Object source = context.getSource();
            CommandSender sender = commandSender(source);
            if (!(sender instanceof Player)) return fallback.run(context);
            boolean hasSelectors = hasSelectorArguments(context, null);
            if (!hasSelectors) {
                validateSelectors(context);
                return fallback.run(context);
            }

            try {
                boolean vanillaAllowed = bridge.selectorCheckBypassed(source) || bridge.allowsSelectors(source);
                SelectorTargets.validateSelectorSyntax(sender, vanillaAllowed);
                SelectorOverride override = bridge.bypassSelectorCheck(context, null);
                try {
                    validateSelectors(context);
                    return fallback.run(context);
                } finally {
                    override.restore();
                }
            } catch (InvocationTargetException error) {
                throw reflectionFailure(error);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Could not apply native selector permission", error);
            }
        }
    }

    /** Whether argument {@code name} (any argument if {@code null}) holds a selector the player typed with {@code @}. */
    boolean hasSelectorArguments(CommandContext<?> context, String name) {
        for (VanillaTree.ParsedNode parsed : VanillaTree.parsedNodes(context)) {
            if (!(parsed.node() instanceof ArgumentCommandNode<?, ?>)) continue;
            ArgumentCommandNode<?, ?> argument = (ArgumentCommandNode<?, ?>) parsed.node();
            if (name != null && !argument.getName().equals(name)
                    || bridge.selectorResolver(argument.getType()) == null) continue;
            if (parsed.range().get(context.getInput()).startsWith("@")) return true;
        }
        return false;
    }

    CommandSender commandSender(Object source) {
        try {
            return bridge.sender(source);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return null;
        }
    }

    private static RuntimeException reflectionFailure(InvocationTargetException error) throws CommandSyntaxException {
        Throwable cause = error.getCause();
        if (cause instanceof CommandSyntaxException) throw (CommandSyntaxException) cause;
        if (cause instanceof RuntimeException) return (RuntimeException) cause;
        return new IllegalStateException("Native selector permission check failed", cause);
    }

    private void validateSelectors(CommandContext<?> context) throws CommandSyntaxException {
        CommandSender sender = commandSender(context.getSource());
        if (!(sender instanceof Player)) return;
        List<String> path = VanillaTree.parsedPath(context);
        for (VanillaTree.ParsedNode parsed : VanillaTree.parsedNodes(context)) {
            if (!(parsed.node() instanceof ArgumentCommandNode<?, ?>)) continue;
            ArgumentCommandNode<?, ?> argument = (ArgumentCommandNode<?, ?>) parsed.node();
            ArgumentResolver resolver = bridge.selectorResolver(argument.getType());
            if (resolver == null) continue;
            Object result = resolve(resolver, context, argument.getName());
            String pool = RandomSelectors.candidates(parsed.range().get(context.getInput()), false);
            if (pool == null) {
                SelectorTargets.validate(sender, path, argument.getName(), result, bridge::selectorTarget);
            } else {
                Object candidates = resolveCandidates(context, argument, resolver, pool);
                SelectorTargets.validate(sender, path, argument.getName(), result, candidates, bridge::selectorTarget);
            }
        }
    }

    private static Object resolve(ArgumentResolver resolver, CommandContext<?> context, String name) throws CommandSyntaxException {
        try {
            return resolver.resolve(context, name);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof CommandSyntaxException) throw (CommandSyntaxException) cause;
            throw new IllegalStateException("Native selector resolver failed", cause);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Native selector resolver failed", error);
        }
    }

    /**
     * Resolves {@code pool}, the random selector typed for {@code argument} without its limit and random order, through
     * the same resolver: the argument's parsed value is swapped for the pool's while it resolves, then put back.
     * Brigadier's own API only (its field names are not obfuscated), so it works the same on every server.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object resolveCandidates(CommandContext<?> context, ArgumentCommandNode<?, ?> argument, ArgumentResolver resolver,
                                     String pool) throws CommandSyntaxException {
        String name = argument.getName();
        Map<String, ParsedArgument<?, ?>> arguments;
        try {
            arguments = (Map<String, ParsedArgument<?, ?>>) contextArguments().get(context);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not read the parsed command arguments", error);
        }
        ParsedArgument<?, ?> original = arguments.get(name);
        if (original == null) throw new IllegalStateException("No parsed argument " + name);
        Object poolValue = argument.getType().parse(new StringReader(pool));
        arguments.put(name, new ParsedArgument(original.getRange().getStart(), original.getRange().getEnd(), poolValue));
        try {
            SelectorOverride override = bridge.bypassSelectorCheck(context, name);
            try {
                return resolve(resolver, context, name);
            } finally {
                override.restore();
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not resolve every target of a random selector", error);
        } finally {
            arguments.put(name, original);
        }
    }

    private static Field contextArguments() throws ReflectiveOperationException {
        Field field = contextArgumentsField;
        if (field == null) {
            field = VanillaTree.accessible(CommandContext.class.getDeclaredField("arguments"),
                    "Brigadier command context arguments field is inaccessible");
            contextArgumentsField = field;
        }
        return field;
    }

    /** The {@code minecraft.command.<path>} requirement for one node, falling back to vanilla's when unset. */
    PermissionPredicate permissionPredicate(String path, Predicate<Object> fallback) {
        return new PermissionPredicate(path, fallback);
    }

    final class PermissionPredicate implements Predicate<Object> {
        final String path;
        final Predicate<Object> fallback;
        private final String permission;

        private PermissionPredicate(String path, Predicate<Object> fallback) {
            this.path = path;
            this.fallback = fallback;
            this.permission = "minecraft.command." + path;
        }

        @Override
        public boolean test(Object source) {
            CommandSender sender;
            try {
                sender = bridge.sender(source);
            } catch (ReflectiveOperationException | RuntimeException error) {
                JavaPlugin logTarget = plugin;
                if (logTarget != null && senderLookupWarning.compareAndSet(false, true)) {
                    logTarget.getLogger().warning("Could not look up the command sender, using vanilla requirements: " + error.getMessage());
                }
                return fallback.test(source);
            }
            switch (PermissionManager.queryPermission(sender, permission)) {
                case TRUE: return true;
                case FALSE: return false;
                default: return fallback.test(source);
            }
        }
    }
}
