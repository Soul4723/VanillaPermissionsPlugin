package io.github.soul4723.vanillapermissions.spigot;

import io.github.soul4723.vanillapermissions.brigadier.PermissionLevelTable;
import io.github.soul4723.vanillapermissions.spigot.SpigotNames.LevelShape;
import io.github.soul4723.vanillapermissions.spigot.SpigotNames.NativeClass;
import io.github.soul4723.vanillapermissions.spigot.SpigotNames.NativeMember;

import static io.github.soul4723.vanillapermissions.brigadier.BrigadierBridge.GAMEMASTER;
import static io.github.soul4723.vanillapermissions.brigadier.BrigadierBridge.SELECTOR_PERMISSION;
import static io.github.soul4723.vanillapermissions.brigadier.Reflection.invoke;

/**
 * Vanilla operator levels on a command source or entity, in the {@link LevelShape} {@link NativeAccess} found. Levels
 * are read from the source's own level or PermissionSet, never from CraftBukkit's Bukkit-aware checks, so granting a
 * Bukkit node cannot raise what {@code /execute oplevel} copies. Entities other than players have level 0 from 1.21.2.
 *
 * <p>The PermissionSet half shares its {@link PermissionLevelTable} with the {@code paper} adapter's
 * {@code NativePermissionLevels}, but passes it members from {@link SpigotNames} instead of Mojang names, reads the
 * source's raw {@code permissions} field instead of {@code permissions()}, and uses CraftBukkit's selector check
 * instead of Paper's {@code hasPermission(Permission, String)}, which Spigot lacks.
 */
abstract class OperatorLevels {

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
        return access.shape() == LevelShape.INT ? new IntLevels(access) : new PermissionSetLevels(access);
    }

    /** Up to 1.21.10: an int level on the source and CraftBukkit's {@code hasPermission(int, String)}. */
    private static final class IntLevels extends OperatorLevels {
        private final NativeAccess access;

        IntLevels(NativeAccess access) {
            this.access = access;
        }

        @Override
        boolean allowsSelectors(Object source) throws ReflectiveOperationException {
            return (Boolean) invoke(access.method(NativeMember.SOURCE_HAS_PERMISSION), source, GAMEMASTER, SELECTOR_PERMISSION);
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
        private final Object selectorPermission;
        private final PermissionLevelTable table;

        PermissionSetLevels(NativeAccess access) throws ReflectiveOperationException {
            this.access = access;
            selectorPermission = access.field(NativeMember.SELECTORS_PERMISSION).get(null);
            Class<?> permissionLevel = access.type(NativeClass.PERMISSION_LEVEL);
            table = new PermissionLevelTable(permissionLevel, access.method(NativeMember.PERMISSION_LEVEL_ID),
                    access.type(NativeClass.HAS_COMMAND_LEVEL).getConstructor(permissionLevel), access.method(NativeMember.FOR_LEVEL),
                    access.method(NativeMember.SET_HAS_PERMISSION));
            // On 1.21.11 the selector permission is the obfuscated Permissions.e, so check that it behaves like the
            // entity selector permission (granted from level 2), as NativeAccess checks the selector flag by behaviour.
            // The paper adapter only finds it by its Mojang name, so it does not check.
            if (!table.grants(table.setFor(GAMEMASTER), selectorPermission)
                    || table.grants(table.setFor(GAMEMASTER - 1), selectorPermission)) {
                throw new IllegalStateException(NativeMember.SELECTORS_PERMISSION
                        + " is not granted from level 2 like the entity selector permission");
            }
        }

        @Override
        boolean allowsSelectors(Object source) throws ReflectiveOperationException {
            Object delegate = access.field(NativeMember.SOURCE_BUKKIT_PERMISSIONS).get(source);
            Class<?> delegateType = access.type(NativeClass.DELEGATE_PERMISSION_SET);
            if (!delegateType.isInstance(delegate)) {
                throw new IllegalStateException("The source's Bukkit permissions are not a " + delegateType.getName());
            }
            boolean vanilla = table.grants(access.field(NativeMember.SOURCE_PERMISSIONS).get(source), selectorPermission);
            return (Boolean) invoke(access.method(NativeMember.DELEGATE_HAS_PERMISSION), delegate, vanilla, SELECTOR_PERMISSION);
        }

        @Override
        int level(Object sourceOrEntity) throws ReflectiveOperationException {
            if (access.type(NativeClass.SOURCE).isInstance(sourceOrEntity)) {
                return table.level(access.field(NativeMember.SOURCE_PERMISSIONS).get(sourceOrEntity));
            }
            if (access.type(NativeClass.PLAYER).isInstance(sourceOrEntity)) {
                return table.level(invoke(access.method(NativeMember.PLAYER_PERMISSIONS), sourceOrEntity));
            }
            return 0;
        }

        @Override
        Object withLevel(Object source, int level) throws ReflectiveOperationException {
            return invoke(access.method(NativeMember.SOURCE_WITH_PERMISSION_SET), source, table.setFor(level));
        }
    }
}
