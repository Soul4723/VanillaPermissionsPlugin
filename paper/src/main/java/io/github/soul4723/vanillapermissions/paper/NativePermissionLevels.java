package io.github.soul4723.vanillapermissions.paper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Vanilla operator levels on a {@code CommandSourceStack} or entity. Minecraft 1.21.11 replaced the int level
 * ({@code hasPermission(int)}, {@code withPermission(int)}, {@code getPermissionLevel()}) with
 * {@code net.minecraft.server.permissions.PermissionSet}; {@link #detect} picks the shape the server actually has
 * rather than trusting its version string. Entities other than players have level 0 in both shapes.
 */
abstract class NativePermissionLevels {

    static final String SELECTOR_PERMISSION = "minecraft.command.selector";
    static final int GAMEMASTER = 2;

    /** Vanilla's selector check, including Paper's {@code minecraft.command.selector} node. */
    abstract boolean allowsSelectors(Object source) throws ReflectiveOperationException;

    /** Operator level (0–4) of a command source or entity; entities without one are level 0. */
    abstract int level(Object sourceOrEntity) throws ReflectiveOperationException;

    /** A copy of {@code source} whose operator level is at most {@code level}; never raises it. */
    abstract Object withLevelAtMost(Object source, int level) throws ReflectiveOperationException;

    static NativePermissionLevels detect(Class<?> sourceClass) throws ReflectiveOperationException {
        if (hasMethod(sourceClass, "hasPermission", int.class, String.class)) {
            return new IntLevels(sourceClass);
        }
        ClassLoader loader = sourceClass.getClassLoader();
        Class<?> permissionSet;
        try {
            permissionSet = Class.forName("net.minecraft.server.permissions.PermissionSet", false, loader);
        } catch (ClassNotFoundException missing) {
            throw new IllegalStateException(sourceClass.getName()
                + " has neither hasPermission(int, String) nor a PermissionSet, so its operator levels cannot be read");
        }
        return new PermissionSetLevels(sourceClass, permissionSet, loader);
    }

    private static boolean hasMethod(Class<?> type, String name, Class<?>... parameters) {
        try {
            type.getMethod(name, parameters);
            return true;
        } catch (NoSuchMethodException missing) {
            return false;
        }
    }

    static Object invoke(Method method, Object receiver, Object... arguments) throws ReflectiveOperationException {
        try {
            return method.invoke(receiver, arguments);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            throw error;
        }
    }

    /**
     * Minecraft up to 1.21.10: an int {@code permissionLevel} on the source and {@code getPermissionLevel()} on
     * entities. That method moved from {@code Entity} down to {@code Player} in 1.21.2, so it is looked up on each
     * target's own class hierarchy.
     */
    private static final class IntLevels extends NativePermissionLevels {
        private final Class<?> sourceClass;
        private final Method hasPermission;
        private final Field permissionLevel;
        private final Method withPermission;
        private final ClassValue<Method> entityLevel = new ClassValue<>() {
            @Override
            protected Method computeValue(Class<?> type) {
                for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                    try {
                        Method method = current.getDeclaredMethod("getPermissionLevel");
                        if (method.getReturnType() == int.class && method.trySetAccessible()) return method;
                    } catch (NoSuchMethodException ignored) {
                        // keep walking up
                    }
                }
                return null;
            }
        };

        private IntLevels(Class<?> sourceClass) throws ReflectiveOperationException {
            this.sourceClass = sourceClass;
            hasPermission = sourceClass.getMethod("hasPermission", int.class, String.class);
            withPermission = sourceClass.getMethod("withPermission", int.class);
            permissionLevel = sourceClass.getDeclaredField("permissionLevel");
            if (permissionLevel.getType() != int.class || !permissionLevel.trySetAccessible()) {
                throw new IllegalAccessException(sourceClass.getName() + ".permissionLevel is not a readable int");
            }
        }

        @Override
        boolean allowsSelectors(Object source) throws ReflectiveOperationException {
            return (boolean) invoke(hasPermission, source, GAMEMASTER, SELECTOR_PERMISSION);
        }

        @Override
        int level(Object sourceOrEntity) throws ReflectiveOperationException {
            if (sourceClass.isInstance(sourceOrEntity)) return permissionLevel.getInt(sourceOrEntity);
            Method method = entityLevel.get(sourceOrEntity.getClass());
            return method == null ? 0 : (int) invoke(method, sourceOrEntity);
        }

        @Override
        Object withLevelAtMost(Object source, int level) throws ReflectiveOperationException {
            int current = level(source);
            return level >= current ? source : invoke(withPermission, source, level);
        }
    }

    /**
     * Minecraft 1.21.11 and later: {@code permissions()} returns a {@code PermissionSet}, levels are
     * {@code Permission.HasCommandLevel(PermissionLevel)} checks, and {@code LevelBasedPermissionSet.forLevel}
     * builds a set for one level. Players expose {@code permissions()}; other entities have no operator level.
     */
    private static final class PermissionSetLevels extends NativePermissionLevels {
        private final Class<?> permissionSet;
        private final Method hasPermission;
        private final Method withPermission;
        private final Method setHasPermission;
        private final Method forLevel;
        private final Object selectorPermission;
        /** {@code PermissionLevel} constants and their {@code HasCommandLevel} checks, indexed by level id. */
        private final Object[] levels;
        private final Object[] levelChecks;

        private PermissionSetLevels(Class<?> sourceClass, Class<?> permissionSet, ClassLoader loader)
                throws ReflectiveOperationException {
            this.permissionSet = permissionSet;
            Class<?> permission = Class.forName("net.minecraft.server.permissions.Permission", false, loader);
            Class<?> permissionLevel = Class.forName("net.minecraft.server.permissions.PermissionLevel", false, loader);
            Class<?> levelBased = Class.forName("net.minecraft.server.permissions.LevelBasedPermissionSet", false, loader);
            Class<?> hasCommandLevel = Class.forName("net.minecraft.server.permissions.Permission$HasCommandLevel", false, loader);
            Class<?> permissions = Class.forName("net.minecraft.server.permissions.Permissions", false, loader);

            hasPermission = sourceClass.getMethod("hasPermission", permission, String.class);
            withPermission = sourceClass.getMethod("withPermission", permissionSet);
            Method sourcePermissions = sourceClass.getMethod("permissions");
            if (!permissionSet.isAssignableFrom(sourcePermissions.getReturnType())) {
                throw new IllegalStateException(sourceClass.getName() + ".permissions() does not return a PermissionSet");
            }
            setHasPermission = permissionSet.getMethod("hasPermission", permission);
            forLevel = levelBased.getMethod("forLevel", permissionLevel);
            selectorPermission = permissions.getField("COMMANDS_ENTITY_SELECTORS").get(null);

            Method id = permissionLevel.getMethod("id");
            Object[] constants = permissionLevel.getEnumConstants();
            Constructor<?> check = hasCommandLevel.getConstructor(permissionLevel);
            levels = new Object[constants.length];
            levelChecks = new Object[constants.length];
            for (Object constant : constants) {
                int index = (int) id.invoke(constant);
                if (index < 0 || index >= constants.length || levels[index] != null) {
                    throw new IllegalStateException("Unexpected PermissionLevel ids: " + constant + " has id " + index);
                }
                levels[index] = constant;
                levelChecks[index] = check.newInstance(constant);
            }
        }

        @Override
        boolean allowsSelectors(Object source) throws ReflectiveOperationException {
            return (boolean) invoke(hasPermission, source, selectorPermission, SELECTOR_PERMISSION);
        }

        @Override
        int level(Object sourceOrEntity) throws ReflectiveOperationException {
            Method permissions;
            try {
                permissions = sourceOrEntity.getClass().getMethod("permissions");
            } catch (NoSuchMethodException none) {
                return 0;
            }
            Object set = invoke(permissions, sourceOrEntity);
            if (!permissionSet.isInstance(set)) return 0;
            for (int index = levels.length - 1; index > 0; index--) {
                if ((boolean) invoke(setHasPermission, set, levelChecks[index])) return index;
            }
            return 0;
        }

        @Override
        Object withLevelAtMost(Object source, int level) throws ReflectiveOperationException {
            if (level >= level(source)) return source;
            int clamped = Math.max(0, Math.min(level, levels.length - 1));
            return invoke(withPermission, source, invoke(forLevel, null, levels[clamped]));
        }
    }
}
