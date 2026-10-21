package io.github.soul4723.vanillapermissions.paper;

import io.github.soul4723.vanillapermissions.brigadier.PermissionLevelTable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static io.github.soul4723.vanillapermissions.brigadier.BrigadierBridge.GAMEMASTER;
import static io.github.soul4723.vanillapermissions.brigadier.BrigadierBridge.SELECTOR_PERMISSION;
import static io.github.soul4723.vanillapermissions.brigadier.Reflection.invoke;

/**
 * Vanilla operator levels on a {@code CommandSourceStack} or entity. Minecraft 1.21.11 replaced the int level
 * ({@code hasPermission(int)}, {@code withPermission(int)}, {@code getPermissionLevel()}) with
 * {@code net.minecraft.server.permissions.PermissionSet}; {@link #detect} picks the shape the server actually has
 * rather than trusting its version string. Entities other than players have level 0 in both shapes. The PermissionSet
 * level table is the {@link PermissionLevelTable} the {@code spigot} adapter also uses.
 */
abstract class NativePermissionLevels {

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
        private final Object selectorPermission;
        private final PermissionLevelTable table;

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
            selectorPermission = permissions.getField("COMMANDS_ENTITY_SELECTORS").get(null);
            table = new PermissionLevelTable(permissionLevel, permissionLevel.getMethod("id"),
                hasCommandLevel.getConstructor(permissionLevel), levelBased.getMethod("forLevel", permissionLevel),
                permissionSet.getMethod("hasPermission", permission));
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
            return permissionSet.isInstance(set) ? table.level(set) : 0;
        }

        @Override
        Object withLevelAtMost(Object source, int level) throws ReflectiveOperationException {
            if (level >= level(source)) return source;
            return invoke(withPermission, source, table.setFor(level));
        }
    }
}
