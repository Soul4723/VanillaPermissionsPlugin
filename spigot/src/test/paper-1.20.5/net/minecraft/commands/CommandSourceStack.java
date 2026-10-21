package net.minecraft.commands;

import org.bukkit.command.CommandSender;

/**
 * Mimics Paper 1.20.5's CommandSourceStack (Mojang names, int levels): private int {@code permissionLevel},
 * {@code withPermission(int)} and CraftBukkit's {@code hasPermission(int, String)}. Spigot 26.x's class of the same
 * name is in {@code src/test/java}, so this one is compiled to its own directory (see the {@code spigot} pom) and
 * loaded by its own class loader.
 */
public class CommandSourceStack {

    private final int permissionLevel;
    private final CommandSender sender;

    public CommandSourceStack(CommandSender sender, int permissionLevel) {
        this.sender = sender;
        this.permissionLevel = permissionLevel;
    }

    public CommandSourceStack withPermission(int level) {
        return new CommandSourceStack(sender, level);
    }

    public boolean hasPermission(int level, String permission) {
        return permissionLevel >= level || sender.hasPermission(permission);
    }
}
