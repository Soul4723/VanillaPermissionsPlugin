package io.github.soul4723.vanillapermissions.brigadier;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.lang.reflect.Method;

/**
 * Resolves one parsed argument through vanilla, e.g. {@code EntityArgument.getEntities(context, name)}. Failures of a
 * reflective call may arrive as an {@link java.lang.reflect.InvocationTargetException}; callers unwrap vanilla's
 * {@link CommandSyntaxException} from it so players see vanilla's message.
 */
@FunctionalInterface
public interface ArgumentResolver {

    Object resolve(CommandContext<?> context, String name) throws CommandSyntaxException, ReflectiveOperationException;

    /** A static {@code (CommandContext, String)} method, called as is. */
    static ArgumentResolver of(Method staticMethod) {
        return (context, name) -> staticMethod.invoke(null, context, name);
    }
}
