package io.github.soul4723.extrapermissions.command;

import dev.jorel.commandapi.executors.CommandArguments;
import io.github.soul4723.extrapermissions.ExtraPermissions;
import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BasicCommandHandlers {
    
    public static void handleReload(CommandSender sender, ExtraPermissions plugin) {
        plugin.reloadPluginConfig();
        sender.sendMessage("§aExtraPermissions configuration reloaded!");
        sender.sendMessage("§7Note: Some changes may require a full plugin restart to take effect.");
    }
    
    public static void handleCheck(CommandSender sender, CommandArguments args) {
        Player target = null;
        Object raw = args.getUnchecked("player");
        if (raw instanceof Player) {
            target = (Player) raw;
        }
        if (target == null) {
            sender.sendMessage("§cPlayer not found!");
            return;
        }
        
        sender.sendMessage("§6Permissions for " + target.getName() + ":");
        String[] keyPermissions = {
            "extrapermissions.use",
            "extrapermissions.reload", 
            "extrapermissions.admin",
            "minecraft.command.gamemode",
            "minecraft.command.teleport",
            "minecraft.selector.self.s",
            "minecraft.bypass.chat-speed",
            "minecraft.operator_block.command_block.place"
        };
        
        for (String permission : keyPermissions) {
            boolean hasPermission = PermissionManager.hasPermissionOrParent(target, permission);
            String status = hasPermission ? "§a✓" : "§c✗";
            sender.sendMessage("§7  " + status + " §f" + permission);
        }
    }
    
    public static void handleDebug(CommandSender sender, ExtraPermissions plugin) {
        sender.sendMessage("§6=== ExtraPermissions Debug Info ===");
        sender.sendMessage("§7Plugin Version: §f" + plugin.getDescription().getVersion());
        
        var hook = PermissionManager.getLuckPermsHook();
        sender.sendMessage("§7LuckPerms Integration: §f" + (hook != null && hook.isEnabled() ? "§aEnabled" : "§cDisabled"));
        sender.sendMessage("§7" + PermissionManager.getCacheStats());
        
        sender.sendMessage("§7Feature Status:");
        sender.sendMessage("§7  command_permissions: " + (plugin.isFeatureEnabled("command_permissions") ? "§aEnabled" : "§cDisabled"));
        sender.sendMessage("§7  selector_permissions: " + (plugin.isFeatureEnabled("selector_permissions") ? "§aEnabled" : "§cDisabled"));
        sender.sendMessage("§7  bypass_permissions: " + (plugin.isFeatureEnabled("bypass_permissions") ? "§aEnabled" : "§cDisabled"));
        sender.sendMessage("§7  admin_permissions: " + (plugin.isFeatureEnabled("admin_permissions") ? "§aEnabled" : "§cDisabled"));
    }
}