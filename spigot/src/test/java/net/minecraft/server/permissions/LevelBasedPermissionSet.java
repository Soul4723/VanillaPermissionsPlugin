package net.minecraft.server.permissions;

/**
 * Mimics LevelBasedPermissionSet: level checks, and the selector permission from level 2, as vanilla grants them;
 * {@code forLevel} is {@code a} on Spigot 1.21.11.
 */
public interface LevelBasedPermissionSet extends PermissionSet {

    PermissionLevel level();

    @Override
    default boolean hasPermission(Permission permission) {
        PermissionLevel required = permission instanceof Permission.b b ? b.level
                : permission instanceof Permission.HasCommandLevel check ? check.level
                : permission == Permissions.e ? PermissionLevel.GAMEMASTERS : null;
        return required != null && level().compareTo(required) >= 0;
    }

    static LevelBasedPermissionSet a(PermissionLevel level) {
        return () -> level;
    }

    static LevelBasedPermissionSet forLevel(PermissionLevel level) {
        return a(level);
    }
}
