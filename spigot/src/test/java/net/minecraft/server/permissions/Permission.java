package net.minecraft.server.permissions;

/** Mimics Permission with its level check under both names: {@code b} on Spigot 1.21.11, {@code HasCommandLevel} on 26.x. */
public interface Permission {

    final class b implements Permission {
        final PermissionLevel level;

        public b(PermissionLevel level) {
            this.level = level;
        }
    }

    final class HasCommandLevel implements Permission {
        final PermissionLevel level;

        public HasCommandLevel(PermissionLevel level) {
            this.level = level;
        }
    }
}
