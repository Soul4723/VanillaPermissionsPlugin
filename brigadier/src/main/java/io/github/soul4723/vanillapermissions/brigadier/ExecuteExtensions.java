package io.github.soul4723.vanillapermissions.brigadier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Adds DrexHD's {@code /execute if|unless permission <entity> <node>} condition and the {@code feedback} and
 * {@code oplevel} source modifiers to the vanilla tree. The tree is built here; the source operations come from the
 * {@link BrigadierBridge}.
 */
final class ExecuteExtensions {

    private final List<Mutation> installed = new ArrayList<>();
    private final BrigadierPermissions permissions;
    private final BrigadierBridge bridge;
    private JavaPlugin plugin;

    ExecuteExtensions(BrigadierPermissions permissions, BrigadierBridge bridge) {
        this.permissions = permissions;
        this.bridge = bridge;
    }

    void clear() {
        for (int i = installed.size() - 1; i >= 0; i--) {
            Mutation mutation = installed.get(i);
            try {
                VanillaTree.removeNode(mutation.parent, mutation.node);
            } catch (ReflectiveOperationException | RuntimeException error) {
                if (plugin != null) plugin.getLogger().warning("Could not remove native /execute extension: " + error.getMessage());
            }
        }
        installed.clear();
        plugin = null;
    }

    void install(JavaPlugin owner, CommandDispatcher<?> dispatcher) {
        try {
            plugin = owner;
            List<CommandNode<?>> roots = VanillaTree.vanillaCommands(dispatcher, "execute");
            if (roots.isEmpty()) throw new IllegalStateException("Vanilla /execute command was not registered");
            CommandNode<?> execute = originalExecute(roots.get(0));
            CommandNode<?> conditional = execute.getChild("if");
            CommandNode<?> unless = execute.getChild("unless");
            if (conditional == null || unless == null) {
                throw new IllegalStateException("Vanilla /execute if/unless conditions were not registered");
            }

            @SuppressWarnings("unchecked")
            CommandNode<Object> executeRoot = (CommandNode<Object>) execute;
            addPermissionCondition(executeRoot, conditional, true);
            addPermissionCondition(executeRoot, unless, false);
            addFeedbackModifiers(executeRoot);
            addPermissionModifiers(executeRoot);
            // A copied minecraft:execute alias shares the subcommands but not nodes added to the original afterwards.
            for (CommandNode<?> root : roots) {
                for (String name : Arrays.asList("feedback", "oplevel")) {
                    if (root.getChild(name) == null) addChild(root, executeRoot.getChild(name));
                }
            }
            owner.getLogger().info("Installed vanilla /execute permission, feedback, and oplevel extensions");
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw new IllegalStateException("Could not install native /execute extensions", error);
        }
    }

    /** The node vanilla's {@code /execute} subcommands redirect back to, which the extensions must target too. */
    private static CommandNode<?> originalExecute(CommandNode<?> execute) {
        CommandNode<?> as = execute.getChild("as");
        CommandNode<?> targets = as == null ? null : as.getChild("targets");
        return targets != null && targets.getRedirect() != null ? targets.getRedirect() : execute;
    }

    private void addPermissionCondition(CommandNode<Object> executeRoot, CommandNode<?> conditional, boolean positive)
            throws ReflectiveOperationException {
        CommandNode<?> existing = conditional.getChild("permission");
        if (existing != null) {
            if (existing.getChild("entity") == null
                    || existing.getChild("entity").getChild("permission") == null) {
                throw new IllegalStateException("Existing /execute permission condition has an unexpected shape");
            }
            return;
        }

        @SuppressWarnings("unchecked")
        ArgumentType<Object> entityType = (ArgumentType<Object>) bridge.entityArgument();
        RequiredArgumentBuilder<Object, Object> entity = RequiredArgumentBuilder.argument("entity", entityType);
        RequiredArgumentBuilder<Object, String> permission = RequiredArgumentBuilder.<Object, String>argument("permission", StringArgumentType.word())
                .fork(executeRoot, context -> permissionCondition(context, positive));
        entity.then(permission);
        CommandNode<?> node = LiteralArgumentBuilder.<Object>literal("permission").then(entity).build();
        addChild(conditional, node);
    }

    private Collection<Object> permissionCondition(CommandContext<Object> context, boolean positive)
            throws CommandSyntaxException {
        try {
            Object entity = resolveArgument(context, "entity", bridge.entityResolver());
            String permission = StringArgumentType.getString(context, "permission");
            boolean granted = hasPermission(entity, permission);
            return granted == positive ? Collections.singletonList(context.getSource()) : Collections.emptyList();
        } catch (CommandSyntaxException error) {
            throw error;
        } catch (ReflectiveOperationException | RuntimeException error) {
            warn("Could not evaluate /execute permission condition", error);
            return Collections.emptyList();
        }
    }

    /** Through CraftBukkit's {@code getBukkitEntity()}, which every Brigadier-era server has. */
    private static boolean hasPermission(Object entity, String permission) throws ReflectiveOperationException {
        Object bukkitEntity = findMethod(entity.getClass(), "getBukkitEntity").invoke(entity);
        return bukkitEntity instanceof CommandSender && PermissionManager.hasPermission((CommandSender) bukkitEntity, permission);
    }

    private void addFeedbackModifiers(CommandNode<Object> executeRoot) throws ReflectiveOperationException {
        CommandNode<?> existing = executeRoot.getChild("feedback");
        boolean redirects = bridge.redirectsFeedback();
        if (existing != null) {
            if (existing.getChild("silent") == null
                    || redirects && (existing.getChild("console") == null
                            || existing.getChild("entity") == null
                            || existing.getChild("entity").getChild("targets") == null)) {
                throw new IllegalStateException("Existing /execute feedback extension has an unexpected shape");
            }
            return;
        }

        LiteralArgumentBuilder<Object> feedback = LiteralArgumentBuilder.<Object>literal("feedback")
                .then(LiteralArgumentBuilder.<Object>literal("silent")
                        .redirect(executeRoot, context -> bridge.withSuppressedOutput(context.getSource())));
        if (redirects) {
            feedback.then(LiteralArgumentBuilder.<Object>literal("console")
                    .redirect(executeRoot, context -> bridge.feedbackToServer(context.getSource())));

            @SuppressWarnings("unchecked")
            ArgumentType<Object> entitiesType = (ArgumentType<Object>) bridge.entitiesArgument();
            feedback.then(LiteralArgumentBuilder.<Object>literal("entity")
                    .then(RequiredArgumentBuilder.<Object, Object>argument("targets", entitiesType)
                            .fork(executeRoot, context -> bridge.feedbackToEntities(context.getSource(), entities(context)))));
        }
        addChild(executeRoot, feedback.build());
    }

    private void addPermissionModifiers(CommandNode<Object> executeRoot) throws ReflectiveOperationException {
        CommandNode<?> existing = executeRoot.getChild("oplevel");
        if (existing != null) {
            if (existing.getChild("level") == null || existing.getChild("entity") == null
                    || existing.getChild("entity").getChild("targets") == null) {
                throw new IllegalStateException("Existing /execute oplevel extension has an unexpected shape");
            }
            return;
        }

        LiteralArgumentBuilder<Object> oplevel = LiteralArgumentBuilder.<Object>literal("oplevel")
                .then(RequiredArgumentBuilder.<Object, Integer>argument("level", IntegerArgumentType.integer(0, 4))
                        .redirect(executeRoot, context -> withPermissionAtMost(
                                context.getSource(), IntegerArgumentType.getInteger(context, "level"))));

        @SuppressWarnings("unchecked")
        ArgumentType<Object> entitiesType = (ArgumentType<Object>) bridge.entitiesArgument();
        oplevel.then(LiteralArgumentBuilder.<Object>literal("entity")
                .then(RequiredArgumentBuilder.<Object, Object>argument("targets", entitiesType)
                        .fork(executeRoot, context -> {
                            List<Object> sources = new ArrayList<>();
                            for (Object target : entities(context)) {
                                sources.add(withPermissionAtMost(context.getSource(), permissionLevel(target)));
                            }
                            return sources;
                        })));
        addChild(executeRoot, oplevel.build());
    }

    private void addChild(CommandNode<?> parent, CommandNode<?> child) {
        VanillaTree.addChild(parent, child);
        if (parent.getChild(child.getName()) == child) installed.add(new Mutation(parent, child));
    }

    private Object withPermissionAtMost(Object source, int requestedLevel) {
        try {
            return bridge.withOperatorLevelAtMost(source, requestedLevel);
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw new IllegalStateException("Could not lower native permission level", error);
        }
    }

    private int permissionLevel(Object source) {
        try {
            return bridge.operatorLevel(source);
        } catch (ReflectiveOperationException | RuntimeException error) {
            warn("Could not read native permission level", error);
            return 0;
        }
    }

    private Collection<?> entities(CommandContext<?> context) throws CommandSyntaxException {
        try {
            Object result = resolveArgument(context, "targets", bridge.optionalEntitiesResolver());
            return result instanceof Collection<?> ? (Collection<?>) result : Collections.emptyList();
        } catch (CommandSyntaxException error) {
            throw error;
        } catch (ReflectiveOperationException | RuntimeException error) {
            warn("Could not resolve /execute entity targets", error);
            return Collections.emptyList();
        }
    }

    private Object resolveArgument(CommandContext<?> context, String name, ArgumentResolver resolver)
            throws ReflectiveOperationException, CommandSyntaxException {
        Object source = context.getSource();
        CommandSender sender = bridge.sender(source);
        boolean selector = sender instanceof Player && permissions.hasSelectorArguments(context, name);
        SelectorOverride override = null;
        if (selector) {
            boolean vanillaAllowed = bridge.selectorCheckBypassed(source) || bridge.allowsSelectors(source);
            SelectorTargets.validateSelectorSyntax(sender, vanillaAllowed);
            override = bridge.bypassSelectorCheck(context, name);
        }
        try {
            Object result = BrigadierPermissions.resolve(resolver, context, name);
            SelectorTargets.validate(sender, VanillaTree.parsedPath(context), name, result, result, bridge::selectorTarget);
            return result;
        } finally {
            if (override != null) override.restore();
        }
    }

    private static Method findMethod(Class<?> type, String name) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 0) return method;
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private void warn(String message, Throwable error) {
        if (plugin != null) {
            while ((error instanceof InvocationTargetException
                    || error instanceof IllegalStateException) && error.getCause() != null) error = error.getCause();
            plugin.getLogger().warning(message + ": " + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private static final class Mutation {
        final CommandNode<?> parent;
        final CommandNode<?> node;

        Mutation(CommandNode<?> parent, CommandNode<?> node) {
            this.parent = parent;
            this.node = node;
        }
    }
}
