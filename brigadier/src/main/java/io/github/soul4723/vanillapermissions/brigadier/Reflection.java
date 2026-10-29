package io.github.soul4723.vanillapermissions.brigadier;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Reflective calls on native objects, for the adapters that reach the server by reflection. */
public final class Reflection {

    private Reflection() {}

    /**
     * {@code method.invoke(receiver, arguments)}, rethrowing a {@link RuntimeException} thrown by the method itself so
     * callers see the server's own failure instead of an {@link InvocationTargetException}.
     */
    public static Object invoke(Method method, Object receiver, Object... arguments) throws ReflectiveOperationException {
        try {
            return method.invoke(receiver, arguments);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException) throw (RuntimeException) error.getCause();
            throw error;
        }
    }
}
