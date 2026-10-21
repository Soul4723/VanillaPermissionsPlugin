package io.github.soul4723.vanillapermissions.brigadier;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.soul4723.vanillapermissions.selector.SelectorDenial;
import io.github.soul4723.vanillapermissions.selector.SelectorRules;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Connects resolved vanilla command arguments to the selector permission rules. */
public final class SelectorTargets {

    private static final SimpleCommandExceptionType SELECTOR_DENIED = new SimpleCommandExceptionType(
            new LiteralMessage(SelectorDenial.SYNTAX_DENIED));
    private static final SimpleCommandExceptionType UNRESOLVED = new SimpleCommandExceptionType(
            new LiteralMessage(SelectorDenial.UNRESOLVED));
    private static final SelectorRules.Permissions PERMISSIONS = SelectorRules.PERMISSION_MANAGER;

    private SelectorTargets() {}

    /**
     * Applies selector permissions to one already-resolved native argument: the original Bukkit sender, the
     * dispatcher path, the argument node name, and the resolver's return value, classified by {@code classify}.
     */
    public static void validate(
            CommandSender sender,
            List<String> parsedNodePath,
            String argumentName,
            Object result,
            Function<Object, SelectorRules.Target> classify) throws CommandSyntaxException {
        if (!(sender instanceof Player)) return;
        SelectorPolicy.validate(sender, parsedNodePath, argumentName, targets(result, classify), PERMISSIONS);
    }

    public static void validateSelectorSyntax(CommandSender sender, boolean vanillaAllowed) throws CommandSyntaxException {
        if (!SelectorRules.allowSelectorSyntax(sender, vanillaAllowed, PERMISSIONS)) throw SELECTOR_DENIED.create();
    }

    static Collection<SelectorRules.Target> targets(Object result, Function<Object, SelectorRules.Target> classify)
            throws CommandSyntaxException {
        if (result instanceof Optional<?>) {
            Optional<?> optional = (Optional<?>) result;
            return optional.isPresent() ? targets(optional.get(), classify) : Collections.<SelectorRules.Target>emptyList();
        }
        if (result instanceof Collection<?>) {
            Collection<?> values = (Collection<?>) result;
            List<SelectorRules.Target> targets = new ArrayList<>(values.size());
            for (Object value : values) targets.add(classify.apply(value));
            return targets;
        }
        if (result == null) throw UNRESOLVED.create();
        return Collections.singletonList(classify.apply(result));
    }

    /**
     * What one resolved value is: a Bukkit or native entity (through CraftBukkit's {@code getBukkitEntity}), a game
     * profile (by id), a score holder name, or unsupported.
     */
    public static SelectorRules.Target target(Object value) {
        if (value == null) return SelectorRules.Target.unsupported();
        if (value instanceof Entity) return SelectorRules.Target.entity((Entity) value);
        if (value instanceof String) return SelectorRules.Target.scoreHolder();

        String type = value.getClass().getName();
        if (type.equals("com.mojang.authlib.GameProfile")) {
            Object id = invokeOrNull(value, "getId");
            if (id instanceof UUID) return SelectorRules.Target.player((UUID) id);
            return SelectorRules.Target.unsupported();
        }
        Object bukkitEntity = invokeOrNull(value, "getBukkitEntity");
        if (bukkitEntity instanceof Entity) return SelectorRules.Target.entity((Entity) bukkitEntity);
        if (invokeOrNull(value, "getScoreboardName") instanceof String) {
            return SelectorRules.Target.scoreHolder();
        }
        return SelectorRules.Target.unsupported();
    }

    private static Object invokeOrNull(Object target, String methodName) {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
