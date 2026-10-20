package net.minecraft.server.permissions;

/** Test double for the Minecraft 1.21.11+ class of the same name. */
public interface LevelBasedPermissionSet extends PermissionSet {
    PermissionLevel level();

    @Override
    default boolean hasPermission(Permission permission) {
        if (permission instanceof Permission.HasCommandLevel(PermissionLevel required)) {
            return level().isEqualOrHigherThan(required);
        }
        return permission.equals(Permissions.COMMANDS_ENTITY_SELECTORS) && level().isEqualOrHigherThan(PermissionLevel.GAMEMASTERS);
    }

    static LevelBasedPermissionSet forLevel(PermissionLevel level) {
        return new LevelBasedPermissionSet() {
            @Override
            public PermissionLevel level() {
                return level;
            }
        };
    }
}
