package io.github.soul4723.vanillapermissions.brigadier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static io.github.soul4723.vanillapermissions.brigadier.Reflection.invoke;

/**
 * Operator levels on Minecraft 1.21.11 and later, where a {@code PermissionSet} replaced the int level: each level is
 * a {@code Permission.HasCommandLevel(PermissionLevel)} check, and {@code LevelBasedPermissionSet.forLevel} builds the
 * set for one level. This is the part the {@code paper} and {@code spigot} adapters share; each resolves the server's
 * classes and members its own way (Mojang or Spigot names) and passes them in, and each reads the sets from the
 * source and players itself.
 */
public final class PermissionLevelTable {

    private final Method setHasPermission;
    private final Method forLevel;
    /** {@code PermissionLevel} constants and their {@code HasCommandLevel} checks, indexed by level id. */
    private final Object[] constants;
    private final Object[] checks;

    /**
     * @param permissionLevel  the {@code PermissionLevel} enum
     * @param id               {@code PermissionLevel.id()}
     * @param hasCommandLevel  the {@code Permission.HasCommandLevel(PermissionLevel)} constructor
     * @param forLevel         {@code static LevelBasedPermissionSet.forLevel(PermissionLevel)}
     * @param setHasPermission {@code PermissionSet.hasPermission(Permission)}
     * @throws IllegalStateException if the level ids are not 0 to the highest level, each used once
     */
    public PermissionLevelTable(Class<?> permissionLevel, Method id, Constructor<?> hasCommandLevel, Method forLevel,
                                Method setHasPermission) throws ReflectiveOperationException {
        this.setHasPermission = setHasPermission;
        this.forLevel = forLevel;
        Object[] values = permissionLevel.getEnumConstants();
        if (values == null || values.length <= BrigadierBridge.GAMEMASTER) {
            throw new IllegalStateException(permissionLevel.getName() + " is not an enum of the operator levels");
        }
        constants = new Object[values.length];
        checks = new Object[values.length];
        for (Object constant : values) {
            int index = (Integer) invoke(id, constant);
            if (index < 0 || index >= values.length || constants[index] != null) {
                throw new IllegalStateException("Unexpected PermissionLevel ids: " + constant + " has id " + index);
            }
            constants[index] = constant;
            checks[index] = hasCommandLevel.newInstance(constant);
        }
    }

    /** The highest operator level {@code set} grants; 0 if it grants none or is {@code null}. */
    public int level(Object set) throws ReflectiveOperationException {
        if (set == null) return 0;
        for (int index = checks.length - 1; index > 0; index--) {
            if (grants(set, checks[index])) return index;
        }
        return 0;
    }

    /** {@code LevelBasedPermissionSet.forLevel} for {@code level}, clamped to 0 - the highest level. */
    public Object setFor(int level) throws ReflectiveOperationException {
        int clamped = Math.max(0, Math.min(level, constants.length - 1));
        return invoke(forLevel, null, constants[clamped]);
    }

    /** {@code set.hasPermission(permission)}. */
    public boolean grants(Object set, Object permission) throws ReflectiveOperationException {
        return (Boolean) invoke(setHasPermission, set, permission);
    }
}
