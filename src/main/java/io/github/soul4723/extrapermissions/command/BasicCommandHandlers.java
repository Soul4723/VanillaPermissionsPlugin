package io.github.soul4723.extrapermissions.command;

import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.executors.CommandArguments;
import io.github.soul4723.extrapermissions.ExtraPermissions;
import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BasicCommandHandlers {
    
    public static void handleReload(CommandSender sender, CommandArguments args, ExtraPermissions plugin) {
        if (plugin != null) {
            plugin.reloadPluginConfig();
            sender.sendMessage("§aExtraPermissions configuration reloaded!");
            sender.sendMessage("§7Note: Some changes may require a full plugin restart to take effect.");
        } else {
            sender.sendMessage("§cError: Could not access ExtraPermissions plugin instance!");
        }
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
            "minecraft.selector",
            "minecraft.bypass.chat-speed",
            "minecraft.operator_block.command_block.place"
        };
        
        for (String permission : keyPermissions) {
            boolean hasPermission = PermissionManager.hasPermission(target, permission);
            String status = hasPermission ? "§a✓" : "§c✗";
            sender.sendMessage("§7  " + status + " §f" + permission);
        }
    }
    
    public static void handleDebug(CommandSender sender, CommandArguments args, ExtraPermissions plugin) {
        sender.sendMessage("§6=== ExtraPermissions Debug Info ===");
        sender.sendMessage("§7Plugin Version: §f" + plugin.getDescription().getVersion());
        sender.sendMessage("§7LuckPerms Integration: §f" + (PermissionManager.getLuckPermsHook().isEnabled() ? "§aEnabled" : "§cDisabled"));
        sender.sendMessage("§7" + PermissionManager.getCacheStats());
        
        sender.sendMessage("§7Feature Status:");
        sender.sendMessage("§7  command_permissions: " + (plugin.isFeatureEnabled("command_permissions") ? "§aEnabled" : "§cDisabled"));
        sender.sendMessage("§7  selector_permissions: " + (plugin.isFeatureEnabled("selector_permissions") ? "§aEnabled" : "§cDisabled"));
        sender.sendMessage("§7  bypass_permissions: " + (plugin.isFeatureEnabled("bypass_permissions") ? "§aEnabled" : "§cDisabled"));
        sender.sendMessage("§7  admin_permissions: " + (plugin.isFeatureEnabled("admin_permissions") ? "§aEnabled" : "§cDisabled"));
        sender.sendMessage("§7  debug_features: " + (plugin.isFeatureEnabled("debug_features") ? "§aEnabled" : "§cDisabled"));
    }
}