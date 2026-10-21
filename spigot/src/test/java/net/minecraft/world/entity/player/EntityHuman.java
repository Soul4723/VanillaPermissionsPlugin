package net.minecraft.world.entity.player;

import net.minecraft.server.permissions.PermissionSet;

/** Mimics Spigot 1.21.11's EntityHuman: {@code permissions()} is {@code I()}. */
public class EntityHuman {

    private final PermissionSet permissions;

    public EntityHuman(PermissionSet permissions) {
        this.permissions = permissions;
    }

    public PermissionSet I() {
        return permissions;
    }
}
