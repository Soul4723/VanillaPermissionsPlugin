package io.github.soul4723.vanillapermissions.spigot;

import io.github.soul4723.vanillapermissions.spigot.SpigotNames.Levels;
import io.github.soul4723.vanillapermissions.spigot.SpigotNames.NativeClass;
import io.github.soul4723.vanillapermissions.spigot.SpigotNames.NativeMember;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Vanilla operator levels on a command source or entity, in the {@link Levels} shape {@link NativeAccess} found. Levels
 * are read from the source's own level or PermissionSet, never from CraftBukkit's Bukkit-aware checks, so granting a
 * Bukkit node cannot raise what {@code /execute oplevel} copies. Entities other than players have level 0 from 1.21.2.
 *
 * <p>The PermissionSet half follows the {@code paper} adapter's {@code NativePermissionLevels}, with members from
 * {@link SpigotNames} instead of Mojang names, and CraftBukkit's selector check instead of Paper's
 * {@code hasPermission(Permission, String)}, which Spigot lacks.
 */
abstract class OperatorLevels {

    static final String SELECTOR_PERMISSION = "minecraft.command.selector";
    static final int GAMEMASTER = 2;

    /** Vanilla's selector check: level 2 or the Bukkit node {@code minecraft.command.selector}. */
    abstract boolean allowsSelectors(Object source) throws ReflectiveOperationException;

    /** Operator level (0 - 4) of a command source or entity; entities without one are level 0. */
    abstract int level(Object sourceOrEntity) throws ReflectiveOperationException;

    /** A copy of {@code source} with operator level {@code level}. */
    abstract Object withLevel(Object source, int level) throws ReflectiveOperationException;

    /** A copy of {@code source} whose operator level is at most {@code level}; never raises it. */
    Object withLevelAtMost(Object source, int level) throws ReflectiveOperationException {
        return level >= level(source) ? source : withLevel(source, level);
    }

    static OperatorLevels of(NativeAccess access) throws ReflectiveOperationException {
        return access.levels() == Levels.INT ? new IntLevels(access) : new PermissionSetLevels(access);
    }

    static Object invoke(Method method, Object receiver, Object... arguments) throws ReflectiveOperationException {
        try {
            return method.invoke(receiver, arguments);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException) throw (RuntimeException) error.getCause();
            throw error;
        }
    }

    /** Up to 1.21.10: an int level on the source and CraftBukkit's {@code hasPermission(int, String)}. */
    private static final class IntLevels extends OperatorLevels {
        private final NativeAccess access;
        private final Method hasPermission;

        IntLevels(NativeAccess access) throws ReflectiveOperationException {
            this.access = access;
            Class<?> source = access.type(NativeClass.SOURCE);
            hasPermission = source.getMethod("hasPermission", int.class, String.class);
            if (hasPermission.getReturnType() != boolean.class) {
                throw new IllegalStateException(source.getName() + ".hasPermission(int, String) does not return boolean");
            }
        }

        @Override
        boolean allowsSelectors(Object source) throws ReflectiveOperationException {
            return (Boolean) invoke(hasPermission, source, GAMEMASTER, SELECTOR_PERMISSION);
        }

        @Override
        int level(Object sourceOrEntity) throws ReflectiveOperationException {
            if (access.type(NativeClass.SOURCE).isInstance(sourceOrEntity)) {
                return access.field(NativeMember.SOURCE_PERMISSION_LEVEL).getInt(sourceOrEntity);
            }
            if (!access.type(NativeClass.PERMISSION_LEVEL_HOLDER).isInstance(sourceOrEntity)) return 0;
            return (Integer) invoke(access.method(NativeMember.ENTITY_PERMISSION_LEVEL), sourceOrEntity);
        }

        @Override
        Object withLevel(Object source, int level) throws ReflectiveOperationException {
            return invoke(access.method(NativeMember.SOURCE_WITH_PERMISSION), source, level);
        }
    }

    /**
     * 1.21.11 and later: levels are {@code Permission.HasCommandLevel} checks on a {@code PermissionSet}, and
     * {@code LevelBasedPermissionSet.forLevel} builds the set for one level. CraftBukkit's {@code permissions()} returns
     * its {@code DelegatePermissionSet}, which also grants on Bukkit nodes, but checks the node of the command being
     * tested ({@code currentCommand}) instead of the selector node while a requirement runs. The selector check
     * therefore asks the delegate's {@code hasPermission(boolean, String)} directly, the call its selector branch makes.
     */
    private static final class PermissionSetLevels extends OperatorLevels {
        private final NativeAccess access;
        private final Method setHasPermission;
        private final Field bukkitPermissions;
        private final Class<?> delegateType;
        private final Method delegateHasPermission;
        private final Object selectorPermission;
        /** {@code PermissionLevel} constants and their {@code HasCommandLevel} checks, indexed by level id. */
        private final Object[] levels;
        private final Object[] levelChecks;

        PermissionSetLevels(NativeAccess access) throws ReflectiveOperationException {
            this.access = access;
            setHasPermission = access.method(NativeMember.SET_HAS_PERMISSION);
            selectorPermission = access.field(NativeMember.SELECTORS_PERMISSION).get(null);
            Class<?> source = access.type(NativeClass.SOURCE);
            bukkitPermissions = NativeAccess.field(source, "bukkitPermissions", access.type(NativeClass.PERMISSION_SET), false,
                    source.getName() + ".bukkitPermissions");
            delegateType = Class.forName(source.getName() + "$DelegatePermissionSet", false, source.getClassLoader());
            String where = delegateType.getName() + ".hasPermission(boolean, String)";
            delegateHasPermission = NativeAccess.accessible(delegateType.getMethod("hasPermission", boolean.class, String.class), where);
            if (delegateHasPermission.getReturnType() != boolean.class) {
                throw new IllegalStateException(where + " does not return boolean");
            }

            Class<?> permissionLevel = access.type(NativeClass.PERMISSION_LEVEL);
            Constructor<?> check = access.type(NativeClass.HAS_COMMAND_LEVEL).getConstructor(permissionLevel);
            Method id = access.method(NativeMember.PERMISSION_LEVEL_ID);
            Object[] constants = permissionLevel.getEnumConstants();
            if (constants == null || constants.length <= GAMEMASTER) {
                throw new IllegalStateException(permissionLevel.getName() + " is not an enum of the operator levels");
            }
            levels = new Object[constants.length];
            levelChecks = new Object[constants.length];
            for (Object constant : constants) {
                int index = (Integer) id.invoke(constant);
                if (index < 0 || index >= constants.length || levels[index] != null) {
                    throw new IllegalStateException("Unexpected PermissionLevel ids: " + constant + " has id " + index);
                }
                levels[index] = constant;
                levelChecks[index] = check.newInstance(constant);
            }
            if (!grants(levelSet(GAMEMASTER), selectorPermission) || grants(levelSet(GAMEMASTER - 1), selectorPermission)) {
                throw new IllegalStateException(NativeMember.SELECTORS_PERMISSION + " is not granted from level 2 like the entity selector permission");
            }
        }

        @Override
        boolean allowsSelectors(Object source) throws ReflectiveOperationException {
            Object delegate = bukkitPermissions.get(source);
            if (!delegateType.isInstance(delegate)) {
                throw new IllegalStateException("The source's Bukkit permissions are not a " + delegateType.getName());
            }
            boolean vanilla = grants(access.field(NativeMember.SOURCE_PERMISSIONS).get(source), selectorPermission);
            return (Boolean) invoke(delegateHasPermission, delegate, vanilla, SELECTOR_PERMISSION);
        }

        @Override
        int level(Object sourceOrEntity) throws ReflectiveOperationException {
            Object set;
            if (access.type(NativeClass.SOURCE).isInstance(sourceOrEntity)) {
                set = access.field(NativeMember.SOURCE_PERMISSIONS).get(sourceOrEntity);
            } else if (access.type(NativeClass.PLAYER).isInstance(sourceOrEntity)) {
                set = invoke(access.method(NativeMember.PLAYER_PERMISSIONS), sourceOrEntity);
            } else {
                return 0;
            }
            if (set == null) return 0;
            for (int index = levels.length - 1; index > 0; index--) {
                if (grants(set, levelChecks[index])) return index;
            }
            return 0;
        }

        @Override
        Object withLevel(Object source, int level) throws ReflectiveOperationException {
            int clamped = Math.max(0, Math.min(level, levels.length - 1));
            return invoke(access.method(NativeMember.SOURCE_WITH_PERMISSION_SET), source, levelSet(clamped));
        }

        private Object levelSet(int level) throws ReflectiveOperationException {
            return invoke(access.method(NativeMember.FOR_LEVEL), null, levels[level]);
        }

        private boolean grants(Object set, Object permission) throws ReflectiveOperationException {
            return (Boolean) invoke(setHasPermission, set, permission);
        }
    }
}
