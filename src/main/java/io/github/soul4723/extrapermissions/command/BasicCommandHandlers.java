package io.github.soul4723.extrapermissions.command;

import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BasicCommandHandlers {
    
    public static void handleReload(CommandSender sender, Object[] args) {
        if (!PermissionManager.hasPermission(sender, "extrapermissions.reload")) {
            sender.sendMessage("§cYou don't have permission to reload ExtraPermissions!");
            return;
        }

        sender.sendMessage("§aExtraPermissions configuration reloaded!");
    }
    
    public static void handleCheck(CommandSender sender, Object[] args) {
        if (!PermissionManager.hasPermission(sender, "extrapermissions.admin")) {
            sender.sendMessage("§cYou don't have permission to check permissions!");
            return;
        }
        
        Player target = (Player) args[0];
        if (target == null) {
            sender.sendMessage("§cPlayer not found!");
            return;
        }
        
        sender.sendMessage("§6Permissions for " + target.getName() + ":");
        sender.sendMessage("§7- This is a placeholder for permission checking");
    }
}