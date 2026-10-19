package io.github.soul4723.vanillapermissions.util;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Adds DrexHD's execute permission condition and source modifiers to Paper's vanilla tree. */
public final class NativeExecuteCommandExtensions {

    private static final List<Mutation> INSTALLED = new ArrayList<>();
    private static JavaPlugin plugin;
    private static Method entityArgument;
    private static Method entitiesArgument;
    private static Method getEntity;
    private static Method getEntities;
    private static Method entityPermissionLevel;
    private static Field permissionLevel;
    private static Field silent;
    private static Field selectorBypass;
    private static Class<?> commandSourceStack;
    private static Class<?> commandSource;

    private NativeExecuteCommandExtensions() {}

    public static void clear() {
        for (int i = INSTALLED.size() - 1; i >= 0; i--) {
            Mutation mutation = INSTALLED.get(i);
            try {
                NativeGameModeTree.removeNode(mutation.parent(), mutation.node());
            } catch (ReflectiveOperationException | RuntimeException error) {
                if (plugin != null) plugin.getLogger().warning("Could not remove native /execute extension: " + error.getMessage());
            }
        }
        INSTALLED.clear();
        plugin = null;
    }

    public static void install(JavaPlugin owner, Object dispatcherValue) {
        if (!(dispatcherValue instanceof CommandDispatcher<?> dispatcher)) {
            throw new IllegalStateException("Paper returned an unexpected native command dispatcher");
        }

        try {
            initializeReflection(owner);
            CommandNode<?> execute = NativeCommandPermissions.vanillaCommand(dispatcher, "execute");
            if (execute == null) throw new IllegalStateException("Vanilla /execute command was not registered");
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
            owner.getLogger().info("Installed vanilla /execute permission, feedback, and oplevel extensions");
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw new IllegalStateException("Could not install native /execute extensions", error);
        }
    }

    private static void initializeReflection(JavaPlugin owner) throws ReflectiveOperationException {
        plugin = owner;
        ClassLoader loader = org.bukkit.Bukkit.getServer().getClass().getClassLoader();
        Class<?> entityArgumentClass = Class.forName("net.minecraft.commands.arguments.EntityArgument", false, loader);
        entityArgument = entityArgumentClass.getMethod("entity");
        entitiesArgument = entityArgumentClass.getMethod("entities");
        getEntity = entityArgumentClass.getMethod("getEntity", CommandContext.class, String.class);
        getEntities = entityArgumentClass.getMethod("getOptionalEntities", CommandContext.class, String.class);

        Class<?> sourceClass = Class.forName("net.minecraft.commands.CommandSourceStack", false, loader);
        commandSourceStack = sourceClass;
        Class<?> commandSourceClass = Class.forName("net.minecraft.commands.CommandSource", false, loader);
        commandSource = commandSourceClass;
        commandSourceClass.getMethod("getBukkitSender", sourceClass);
        Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity", false, loader);
        permissionLevel = sourceClass.getDeclaredField("permissionLevel");
        if (!permissionLevel.trySetAccessible()) throw new IllegalAccessException("CommandSourceStack.permissionLevel is inaccessible");
        entityPermissionLevel = entityClass.getDeclaredMethod("getPermissionLevel");
        if (!entityPermissionLevel.trySetAccessible()) throw new IllegalAccessException("Entity.getPermissionLevel is inaccessible");
        silent = sourceClass.getDeclaredField("silent");
        if (!silent.trySetAccessible()) throw new IllegalAccessException("CommandSourceStack.silent is inaccessible");
        selectorBypass = sourceClass.getField("bypassSelectorPermissions");
        sourceClass.getMethod("withSuppressedOutput");
        sourceClass.getMethod("withPermission", int.class);
        sourceClass.getMethod("getServer");
        sourceClass.getMethod("withSource", commandSourceClass);
        sourceClass.getMethod("getBukkitSender");
    }

    private static void addPermissionCondition(CommandNode<Object> executeRoot, CommandNode<?> conditional, boolean positive)
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
        ArgumentType<Object> entityType = (ArgumentType<Object>) entityArgument.invoke(null);
        RequiredArgumentBuilder<Object, Object> entity = RequiredArgumentBuilder.argument("entity", entityType);
        RequiredArgumentBuilder<Object, String> permission = RequiredArgumentBuilder.argument("permission", StringArgumentType.word())
                .fork(executeRoot, context -> permissionCondition(context, positive));
        entity.then(permission);
        CommandNode<?> node = LiteralArgumentBuilder.<Object>literal("permission").then(entity).build();
        addChild(conditional, node);
    }

    private static Collection<Object> permissionCondition(CommandContext<Object> context, boolean positive)
            throws CommandSyntaxException {
        try {
            Object entity = resolveArgument(context, "entity", getEntity);
            String permission = StringArgumentType.getString(context, "permission");
            boolean granted = hasPermission(entity, permission);
            return granted == positive ? List.of(context.getSource()) : List.of();
        } catch (CommandSyntaxException error) {
            throw error;
        } catch (ReflectiveOperationException | RuntimeException error) {
            warn("Could not evaluate /execute permission condition", error);
            return List.of();
        }
    }

    private static boolean hasPermission(Object entity, String permission) throws ReflectiveOperationException {
        Object source = findMethod(entity.getClass(), "createCommandSourceStack").invoke(entity);
        Object bukkitSender = findMethod(source.getClass(), "getBukkitSender").invoke(source);
        return bukkitSender instanceof CommandSender sender && PermissionManager.hasPermission(sender, permission);
    }

    private static void addFeedbackModifiers(CommandNode<Object> executeRoot) throws ReflectiveOperationException {
        CommandNode<?> existing = executeRoot.getChild("feedback");
        if (existing != null) {
            if (existing.getChild("silent") == null || existing.getChild("console") == null
                    || existing.getChild("entity") == null
                    || existing.getChild("entity").getChild("targets") == null) {
                throw new IllegalStateException("Existing /execute feedback extension has an unexpected shape");
            }
            return;
        }

        LiteralArgumentBuilder<Object> feedback = LiteralArgumentBuilder.<Object>literal("feedback")
                .then(LiteralArgumentBuilder.<Object>literal("silent")
                        .redirect(executeRoot, context -> invoke(context.getSource(), "withSuppressedOutput")))
                .then(LiteralArgumentBuilder.<Object>literal("console")
                        .redirect(executeRoot, context -> {
                            Object source = context.getSource();
                            setSilent(source, false);
                            return withOutput(source, invoke(source, "getServer"));
                        }));

        @SuppressWarnings("unchecked")
        ArgumentType<Object> entitiesType = (ArgumentType<Object>) entitiesArgument.invoke(null);
        feedback.then(LiteralArgumentBuilder.<Object>literal("entity")
                .then(RequiredArgumentBuilder.<Object, Object>argument("targets", entitiesType)
                        .fork(executeRoot, context -> {
                            setSilent(context.getSource(), false);
                            List<Object> sources = new ArrayList<>();
                            for (Object target : entities(context)) {
                                sources.add(withOutput(context.getSource(), target));
                            }
                            return sources;
                        })));
        addChild(executeRoot, feedback.build());
    }

    private static void addPermissionModifiers(CommandNode<Object> executeRoot) throws ReflectiveOperationException {
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
        ArgumentType<Object> entitiesType = (ArgumentType<Object>) entitiesArgument.invoke(null);
        oplevel.then(LiteralArgumentBuilder.<Object>literal("entity")
                .then(RequiredArgumentBuilder.<Object, Object>argument("targets", entitiesType)
                        .fork(executeRoot, context -> {
                            int callerLevel = permissionLevel(context.getSource());
                            List<Object> sources = new ArrayList<>();
                            for (Object target : entities(context)) {
                                sources.add(invoke(context.getSource(), "withPermission",
                                        Math.min(callerLevel, permissionLevel(target))));
                            }
                            return sources;
                        })));
        addChild(executeRoot, oplevel.build());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addChild(CommandNode<?> parent, CommandNode<?> child) {
        ((CommandNode) parent).addChild(child);
        if (parent.getChild(child.getName()) == child) INSTALLED.add(new Mutation(parent, child));
    }

    /**
     * Sends command output to {@code output} while LuckPerms and selector checks keep using the original sender.
     * A plain withSource would also make Paper report {@code output} as the Bukkit sender, letting a player run
     * commands and selectors under the console's or another player's permissions.
     */
    static Object withOutput(Object source, Object output) {
        Object sender = invoke(source, "getBukkitSender");
        Object redirected = Proxy.newProxyInstance(commandSource.getClassLoader(), new Class<?>[]{commandSource},
                (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxy == arguments[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            default -> "FeedbackSource[" + output + "]";
                        };
                    }
                    if (method.getName().equals("getBukkitSender")) return sender;
                    try {
                        return method.invoke(output, arguments);
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    }
                });
        return invoke(source, "withSource", redirected);
    }

    private static Object withPermissionAtMost(Object source, int requestedLevel) {
        return invoke(source, "withPermission", Math.min(permissionLevel(source), requestedLevel));
    }

    private static int permissionLevel(Object source) {
        try {
            return commandSourceStack.isInstance(source)
                ? permissionLevel.getInt(source)
                : (int) entityPermissionLevel.invoke(source);
        } catch (ReflectiveOperationException | RuntimeException error) {
            warn("Could not read native permission level", error);
            return 0;
        }
    }

    private static Collection<?> entities(CommandContext<?> context)
            throws CommandSyntaxException {
        try {
            Object result = resolveArgument(context, "targets", getEntities);
            return result instanceof Collection<?> values ? values : List.of();
        } catch (CommandSyntaxException error) {
            throw error;
        } catch (ReflectiveOperationException | RuntimeException error) {
            warn("Could not resolve /execute entity targets", error);
            return List.of();
        }
    }

    private static Object resolveArgument(CommandContext<?> context, String name, Method resolver)
            throws ReflectiveOperationException, CommandSyntaxException {
        Object source = context.getSource();
        Object senderValue = invoke(source, "getBukkitSender");
        CommandSender sender = senderValue instanceof CommandSender value
                ? value : null;
        boolean selector = sender instanceof Player
                && NativeCommandPermissions.isSelectorArgument(context, name);
        boolean previous = selector && selectorBypass.getBoolean(source);
        if (selector) {
            boolean vanillaAllowed = previous || (boolean) invoke(source, "hasPermission", 2, "minecraft.command.selector");
            SelectorNativePermissions.validateSelectorSyntax(sender, vanillaAllowed);
            if (!previous) selectorBypass.setBoolean(source, true);
        }
        try {
            Object result = invokeArgument(resolver, context, name);
            List<String> path = context.getNodes().stream().map(ParsedCommandNode::getNode)
                    .map(CommandNode::getName).toList();
            SelectorNativePermissions.validate(sender, path, name, result);
            return result;
        } finally {
            if (selector && !previous) selectorBypass.setBoolean(source, false);
        }
    }

    private static Object invokeArgument(Method resolver, CommandContext<?> context, String name)
            throws ReflectiveOperationException, CommandSyntaxException {
        try {
            return resolver.invoke(null, context, name);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof CommandSyntaxException syntax) throw syntax;
            if (cause instanceof ReflectiveOperationException reflective) throw reflective;
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Native selector resolver failed", cause);
        }
    }

    private static void setSilent(Object source, boolean value) {
        try {
            silent.setBoolean(source, value);
        } catch (IllegalAccessException | RuntimeException error) {
            throw new IllegalStateException("Could not change native command feedback state", error);
        }
    }

    private static Object invoke(Object receiver, String name, Object... arguments) {
        try {
            Method method = findMethod(receiver.getClass(), name, arguments);
            return method.invoke(receiver, arguments);
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw new IllegalStateException("Could not invoke native command source method " + name, error);
        }
    }

    private static Method findMethod(Class<?> type, String name, Object... arguments) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
            Class<?>[] parameters = method.getParameterTypes();
            boolean matches = true;
            for (int index = 0; index < arguments.length; index++) {
                Object argument = arguments[index];
                if (argument == null || !wrap(parameters[index]).isInstance(argument)) {
                    matches = false;
                    break;
                }
            }
            if (matches) return method;
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == boolean.class) return Boolean.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static void warn(String message, Throwable error) {
        if (plugin != null) {
            while ((error instanceof InvocationTargetException
                    || error instanceof IllegalStateException) && error.getCause() != null) error = error.getCause();
            plugin.getLogger().warning(message + ": " + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private record Mutation(CommandNode<?> parent, CommandNode<?> node) {}
}
