package net.minecraft.commands;

import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;
import org.bukkit.command.CommandSender;

/**
 * Mimics Spigot 1.21.11's CommandListenerWrapper: private PermissionSet g, {@code withPermission} as {@code a}, and
 * CraftBukkit's package-private DelegatePermissionSet, which checks the current command's node while one is set.
 */
public class CommandListenerWrapper {

    private final PermissionSet g;
    private final PermissionSet bukkitPermissions;
    private final CommandSender sender;
    public volatile CommandNode<?> currentCommand;

    public CommandListenerWrapper(CommandSender sender, PermissionSet permissions) {
        this.sender = sender;
        this.g = permissions;
        this.bukkitPermissions = new DelegatePermissionSet(permissions);
    }

    public CommandListenerWrapper a(PermissionSet permissions) {
        return new CommandListenerWrapper(sender, permissions);
    }

    public PermissionSet d() {
        return bukkitPermissions;
    }

    class DelegatePermissionSet implements PermissionSet {
        private final PermissionSet handle;

        DelegatePermissionSet(PermissionSet handle) {
            this.handle = handle;
        }

        @Override
        public boolean hasPermission(Permission permission) {
            boolean vanilla = handle.hasPermission(permission);
            CommandNode<?> current = currentCommand;
            if (current != null) return hasPermission(vanilla, "minecraft.command." + current.getName());
            if (permission.equals(Permissions.e)) return hasPermission(vanilla, "minecraft.command.selector");
            return vanilla;
        }

        public boolean hasPermission(boolean vanilla, String node) {
            return vanilla || sender.hasPermission(node);
        }
    }
}
