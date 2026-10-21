package net.minecraft.server.permissions;

/** Mimics PermissionLevel; {@code id()} is {@code a()} on Spigot 1.21.11. */
public enum PermissionLevel {
    ALL, MODERATORS, GAMEMASTERS, ADMINS, OWNERS;

    public int a() {
        return ordinal();
    }

    public int id() {
        return ordinal();
    }
}
