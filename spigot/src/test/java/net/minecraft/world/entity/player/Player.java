package net.minecraft.world.entity.player;

import net.minecraft.server.permissions.PermissionSet;

/** Mimics Spigot 26.x's Player (Mojang names): {@code permissions()}. */
public class Player {

    private final PermissionSet permissions;

    public Player(PermissionSet permissions) {
        this.permissions = permissions;
    }

    public PermissionSet permissions() {
        return permissions;
    }
}
