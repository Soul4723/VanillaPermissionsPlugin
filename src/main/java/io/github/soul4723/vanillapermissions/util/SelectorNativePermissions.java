package io.github.soul4723.vanillapermissions.util;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
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

    private static final SimpleCommandExceptionType SELECTOR_DENIED = new SimpleCommandExceptionType(
            new LiteralMessage("You do not have permission to use target selectors"));
    private static final SimpleCommandExceptionType UNRESOLVED = new SimpleCommandExceptionType(
            new LiteralMessage("The selected targets could not be checked against selector permissions"));
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

    /**
     * Applies selector permissions to one already-resolved native argument.
     * Native command adapters provide the original Bukkit sender, dispatcher path,
     * argument node name, and resolver return value.
     */
    public static void validate(
            CommandSender sender,
            List<String> parsedNodePath,
            String argumentName,
            Object result) throws CommandSyntaxException {
        if (!(sender instanceof Player)) return;
        SelectorPolicy.validate(sender, parsedNodePath, argumentName, targets(result), PERMISSIONS);
    }

    public static void validateSelectorSyntax(CommandSender sender, boolean vanillaAllowed) throws CommandSyntaxException {
        if (!SelectorPolicy.allowSelectorSyntax(sender, vanillaAllowed, PERMISSIONS)) throw SELECTOR_DENIED.create();
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
        if (result == null) throw UNRESOLVED.create();
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
        if (type.equals("net.minecraft.world.scores.ScoreHolder")
                || invokeOrNull(value, "getScoreboardName") instanceof String) {
            return new SelectorPolicy.Target(SelectorPolicy.Target.Kind.SCORE_HOLDER, null, null);
        }
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
