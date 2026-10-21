package net.minecraft.commands;

import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;
import org.bukkit.command.CommandSender;

/**
 * Mimics Spigot 26.x's CommandSourceStack (Mojang names): private PermissionSet {@code permissions}, and CraftBukkit's
 * {@code bukkitPermissions}, {@code currentCommand} and package-private DelegatePermissionSet. Paper 1.20.5's int-level
 * class of the same name is in {@code src/test/paper-1.20.5}.
 */
public class CommandSourceStack {

    private final PermissionSet permissions;
    private final PermissionSet bukkitPermissions;
    private final CommandSender sender;
    private final Level level;
    public volatile CommandNode<?> currentCommand;

    /** Stands for the source's ServerLevel and its CraftServer's {@code ignoreVanillaPermissions} setting. */
    public static final class Level {
        final boolean ignoreVanillaPermissions;

        public Level(boolean ignoreVanillaPermissions) {
            this.ignoreVanillaPermissions = ignoreVanillaPermissions;
        }
    }

    /** {@code level} may be {@code null}, as for a source without a world. */
    public CommandSourceStack(CommandSender sender, PermissionSet permissions, Level level) {
        this.sender = sender;
        this.permissions = permissions;
        this.level = level;
        this.bukkitPermissions = new DelegatePermissionSet(permissions);
    }

    public CommandSourceStack withPermission(PermissionSet permissions) {
        return new CommandSourceStack(sender, permissions, level);
    }

    /** CraftBukkit returns the Bukkit-aware set here, not {@link #permissions}. */
    public PermissionSet permissions() {
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
            if (permission.equals(Permissions.COMMANDS_ENTITY_SELECTORS)) return hasPermission(vanilla, "minecraft.command.selector");
            return vanilla;
        }

        /** As CraftBukkit: the vanilla answer counts unless the world's server ignores vanilla permissions. */
        public boolean hasPermission(boolean vanilla, String node) {
            return (level == null || !level.ignoreVanillaPermissions) && vanilla || sender.hasPermission(node);
        }
    }
}
