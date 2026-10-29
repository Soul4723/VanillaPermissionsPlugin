package net.minecraft.server.permissions;

/** Mimics Permissions: the entity selector permission is {@code e} on Spigot 1.21.11 and {@code COMMANDS_ENTITY_SELECTORS} on 26.x. */
public class Permissions {

    public static final Permission e = new Permission() {};
    public static final Permission COMMANDS_ENTITY_SELECTORS = e;
}
