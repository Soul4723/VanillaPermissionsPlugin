package net.minecraft.server.permissions;

/** Mimics PermissionSet, which has this name on Spigot 1.21.11 and 26.x. */
public interface PermissionSet {

    boolean hasPermission(Permission permission);
}
