package io.github.soul4723.vanillapermissions.util;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import io.github.soul4723.vanillapermissions.agent.NativeAgentBridge;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/** Connects resolved vanilla command arguments to the selector permission policy. */
public final class SelectorNativePermissions {

    private static final SimpleCommandExceptionType DENIED = new SimpleCommandExceptionType(
            new LiteralMessage("Entity selection is not allowed"));
    private static final SelectorPolicy.Permissions PERMISSIONS = new SelectorPolicy.Permissions() {
        @Override
        public boolean hasPermission(CommandSender sender, String node, boolean fallback) {
            return PermissionManager.hasPermissionOrFallback(sender, node, fallback);
        }

        @Override
        public OptionalInt getIntegerMeta(UUID subjectId, CommandSender onlineSubject, String node) {
            return PermissionManager.getIntegerMeta(subjectId, onlineSubject, node);
        }
    };

    private SelectorNativePermissions() {}

    public static void register() {
        NativeAgentBridge.registerSelectorCheck(SelectorNativePermissions::validate);
    }

    public static void clear() {
        NativeAgentBridge.clearSelectorCheck();
    }

    private static void validate(Object contextValue, String argumentName, Object result) throws Throwable {
        if (!(contextValue instanceof CommandContext<?> context)) {
            throw new IllegalStateException("Selector hook received an invalid command context");
        }

        Object source = context.getSource();
        Object rawSender = invoke(source, "getBukkitSender");
        if (!(rawSender instanceof Player sender) || !isOriginalPlayerSource(source)) return;

        List<String> parsedPath = commandPath(context, source);
        SelectorPolicy.validate(sender, parsedPath, argumentName, targets(result), PERMISSIONS);
    }

    private static List<String> commandPath(CommandContext<?> context, Object source)
            throws ReflectiveOperationException {
        if (context.getRootNode() instanceof RootCommandNode<?>) {
            return context.getNodes().stream().map(node -> node.getNode().getName()).toList();
        }

        List<?> nodes = context.getNodes();
        if (nodes.isEmpty()) throw new IllegalStateException("Selector hook received an empty command path");
        Object lastNode = invoke(nodes.get(nodes.size() - 1), "getNode");
        Object server = invoke(source, "getServer");
        Object commands = invoke(server, "getCommands");
        Object dispatcher = invoke(commands, "getDispatcher");
        return invokePath(dispatcher, lastNode);
    }

    @SuppressWarnings("unchecked")
    private static List<String> invokePath(Object dispatcher, Object node) throws ReflectiveOperationException {
        try {
            Method method = dispatcher.getClass().getMethod("getPath", CommandNode.class);
            return (List<String>) method.invoke(dispatcher, node);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof ReflectiveOperationException reflective) throw reflective;
            throw new IllegalStateException(cause);
        }
    }

    private static boolean isOriginalPlayerSource(Object source) throws ReflectiveOperationException {
        var sourceField = source.getClass().getField("source");
        Object originalSource = sourceField.get(source);
        for (Class<?> type = originalSource == null ? null : originalSource.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals("net.minecraft.server.level.ServerPlayer")) return true;
        }
        return false;
    }

    static Collection<SelectorPolicy.Target> targets(Object result) throws CommandSyntaxException {
        if (result instanceof Optional<?> optional) {
            return optional.isPresent() ? targets(optional.get()) : List.of();
        }
        if (result instanceof Collection<?> values) {
            List<SelectorPolicy.Target> targets = new ArrayList<>(values.size());
            for (Object value : values) targets.add(target(value));
            return targets;
        }
        if (result == null) throw DENIED.create();
        return List.of(target(result));
    }

    static SelectorPolicy.Target target(Object value) {
        if (value == null) return unsupportedTarget();
        if (value instanceof Entity entity) return entityTarget(entity);
        if (value instanceof String) {
            return new SelectorPolicy.Target(SelectorPolicy.Target.Kind.SCORE_HOLDER, null, null);
        }

        String type = value.getClass().getName();
        if (type.equals("com.mojang.authlib.GameProfile")) {
            Object id = invokeOrNull(value, "getId");
            if (id instanceof UUID uuid) return playerTarget(uuid);
            return unsupportedTarget();
        }
        Object bukkitEntity = invokeOrNull(value, "getBukkitEntity");
        if (bukkitEntity instanceof Entity entity) return entityTarget(entity);
        return unsupportedTarget();
    }

    private static SelectorPolicy.Target entityTarget(Entity entity) {
        if (entity instanceof Player player) {
            return playerTarget(player.getUniqueId(), player);
        }
        return new SelectorPolicy.Target(SelectorPolicy.Target.Kind.ENTITY, entity.getUniqueId(), null);
    }

    private static SelectorPolicy.Target playerTarget(UUID id) {
        return playerTarget(id, Bukkit.getPlayer(id));
    }

    private static SelectorPolicy.Target playerTarget(UUID id, Player onlinePlayer) {
        return new SelectorPolicy.Target(SelectorPolicy.Target.Kind.PLAYER, id, onlinePlayer);
    }

    private static SelectorPolicy.Target unsupportedTarget() {
        return new SelectorPolicy.Target(SelectorPolicy.Target.Kind.UNSUPPORTED, null, null);
    }

    private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof ReflectiveOperationException reflective) throw reflective;
            throw new IllegalStateException(cause);
        }
    }

    private static Object invokeOrNull(Object target, String methodName) {
        try {
            return invoke(target, methodName);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
