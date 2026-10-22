package io.github.soul4723.vanillapermissions.brigadier;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.soul4723.vanillapermissions.selector.SelectorDenial;
import io.github.soul4723.vanillapermissions.selector.SelectorRules;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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

    /**
     * {@link #validate} for a selector that picks at random: {@code candidates} is what the same selector without its
     * limit and random order resolved to, every target the server could pick when the command runs.
     */
    public static void validate(
            CommandSender sender,
            List<String> parsedNodePath,
            String argumentName,
            Object result,
            Object candidates,
            Function<Object, SelectorRules.Target> classify) throws CommandSyntaxException {
        if (!(sender instanceof Player)) return;
        SelectorPolicy.validate(sender, parsedNodePath, argumentName, targets(result, classify),
                targets(candidates, classify), PERMISSIONS);
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
     * profile (by id; a {@code NameAndId} from 1.21.9), a score holder name, or unsupported.
     */
    public static SelectorRules.Target target(Object value) {
        if (value == null) return SelectorRules.Target.unsupported();
        if (value instanceof Entity) return SelectorRules.Target.entity((Entity) value);
        if (value instanceof String) return SelectorRules.Target.scoreHolder();

        UUID profile = profileId(value);
        if (profile != null) return SelectorRules.Target.player(profile);
        Object bukkitEntity = invokeOrNull(value, "getBukkitEntity");
        if (bukkitEntity instanceof Entity) return SelectorRules.Target.entity((Entity) bukkitEntity);
        if (invokeOrNull(value, "getScoreboardName") instanceof String) {
            return SelectorRules.Target.scoreHolder();
        }
        return SelectorRules.Target.unsupported();
    }

    /**
     * The id of a game profile or {@code NameAndId}, or {@code null} for any other value: its one no-argument method
     * returning a UUID, which is {@code getId()} before authlib 7, {@code id()} on the records since, and obfuscated on
     * Spigot.
     */
    static UUID profileId(Object value) {
        String type = value.getClass().getName();
        if (!type.equals("com.mojang.authlib.GameProfile") && !type.equals("net.minecraft.server.players.NameAndId")) return null;
        for (Method method : value.getClass().getMethods()) {
            if (method.getParameterCount() != 0 || method.getReturnType() != UUID.class
                    || Modifier.isStatic(method.getModifiers())) continue;
            try {
                return (UUID) method.invoke(value);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Object invokeOrNull(Object target, String methodName) {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
