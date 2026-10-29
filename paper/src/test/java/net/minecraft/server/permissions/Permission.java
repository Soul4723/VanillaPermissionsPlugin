package net.minecraft.server.permissions;

/** Test double for the Minecraft 1.21.11+ class of the same name. */
public interface Permission {
    record HasCommandLevel(PermissionLevel level) implements Permission {}

    record Atom(String id) implements Permission {}
}
