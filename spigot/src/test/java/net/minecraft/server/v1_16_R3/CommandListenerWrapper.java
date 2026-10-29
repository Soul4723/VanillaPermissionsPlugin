package net.minecraft.server.v1_16_R3;

import org.bukkit.command.CommandSender;

/** Mimics Spigot 1.16.5's CommandListenerWrapper: private int f (level), private boolean j (silent). */
public class CommandListenerWrapper {

    private final int f;
    private final boolean j;
    private final CommandSender sender;

    public CommandListenerWrapper(CommandSender sender, int level) {
        this(sender, level, false);
    }

    private CommandListenerWrapper(CommandSender sender, int level, boolean silent) {
        this.sender = sender;
        this.f = level;
        this.j = silent;
    }

    public CommandListenerWrapper a(int level) {
        return new CommandListenerWrapper(sender, level, j);
    }

    public CommandListenerWrapper a() {
        return new CommandListenerWrapper(sender, f, true);
    }

    public CommandSender getBukkitSender() {
        return sender;
    }

    public boolean hasPermission(int level, String permission) {
        return f >= level || sender.hasPermission(permission);
    }
}
