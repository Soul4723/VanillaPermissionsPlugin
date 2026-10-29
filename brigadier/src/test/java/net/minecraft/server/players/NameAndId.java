package net.minecraft.server.players;

import java.util.UUID;

/** Test double for Spigot's NameAndId (1.21.9+), whose record accessors are obfuscated. */
public record NameAndId(UUID a, String b) {}
