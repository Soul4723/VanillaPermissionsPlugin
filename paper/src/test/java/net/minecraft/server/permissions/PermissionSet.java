package net.minecraft.server.permissions;

/** Test double for the Minecraft 1.21.11+ class of the same name. */
public interface PermissionSet {
    PermissionSet ALL_PERMISSIONS = permission -> true;

    boolean hasPermission(Permission permission);
}
