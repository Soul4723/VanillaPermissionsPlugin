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

    private SelectorTargets() {}

    /**
     * Applies selector permissions to one already-resolved native argument, each value classified by {@code classify}.
     * {@code candidates} is every target the server could pick when it resolves the selector again as the command
     * runs: {@code result} itself, or for a random selector what the same selector without its limit and random order
     * resolved to.
     */
    public static void validate(
            CommandSender sender,
            List<String> parsedNodePath,
            String argumentName,
            Object result,
            Object candidates,
            Function<Object, SelectorRules.Target> classify) throws CommandSyntaxException {
        if (!(sender instanceof Player)) return;
        Collection<SelectorRules.Target> selected = targets(result, classify);
        Collection<SelectorRules.Target> pool = candidates == result ? selected : targets(candidates, classify);
        Optional<SelectorDenial> denial = SelectorRules.check(sender, parsedNodePath, argumentName, selected, pool,
                SelectorRules.PERMISSION_MANAGER);
        if (denial.isPresent()) throw new SimpleCommandExceptionType(new LiteralMessage(denial.get().message())).create();
    }

    public static void validateSelectorSyntax(CommandSender sender, boolean vanillaAllowed) throws CommandSyntaxException {
        if (!SelectorRules.allowSelectorSyntax(sender, vanillaAllowed, SelectorRules.PERMISSION_MANAGER)) throw SELECTOR_DENIED.create();
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
        // Native entities are score holders too, so one CraftBukkit cannot wrap must not pass for a scoreboard name.
        if (hasMethod(value, "getBukkitEntity")) {
            Object bukkitEntity = invokeOrNull(value, "getBukkitEntity");
            return bukkitEntity instanceof Entity ? SelectorRules.Target.entity((Entity) bukkitEntity) : SelectorRules.Target.unsupported();
        }
        if (invokeOrNull(value, "getScoreboardName") instanceof String) {
            return SelectorRules.Target.scoreHolder();
        }
        return SelectorRules.Target.unsupported();
    }

    private static boolean hasMethod(Object target, String methodName) {
        try {
            target.getClass().getMethod(methodName);
            return true;
        } catch (NoSuchMethodException absent) {
            return false;
        }
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
