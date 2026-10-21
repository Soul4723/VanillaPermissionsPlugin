package net.minecraft.server.permissions;

/** Test double for the Minecraft 1.21.11+ class of the same name. */
public enum PermissionLevel {
    ALL, MODERATORS, GAMEMASTERS, ADMINS, OWNERS;

    public int id() {
        return ordinal();
    }

    public boolean isEqualOrHigherThan(PermissionLevel other) {
        return compareTo(other) >= 0;
    }
}
