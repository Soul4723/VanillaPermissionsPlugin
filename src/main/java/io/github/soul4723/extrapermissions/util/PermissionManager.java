package io.github.soul4723.extrapermissions.util;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PermissionManager {
    
    private static LuckPermsHook luckPermsHook;
    
    public static void initialize(LuckPermsHook luckPermsHook) {
        PermissionManager.luckPermsHook = luckPermsHook;
    }
    
    public static boolean hasPermission(Player player, String permission) {
        if (luckPermsHook != null && luckPermsHook.isEnabled()) {
            try {
                LuckPerms luckPerms = luckPermsHook.getLuckPerms();
                if (luckPerms != null) {
                    User user = luckPerms.getUserManager().getUser(player.getUniqueId());
                    if (user != null) {
                        return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean().orElse(false);
                    }
                }
            } catch (Exception e) {
                Bukkit.getLogger().warning("LuckPerms permission check failed, falling back to Bukkit: " + e.getMessage());
            }
        }

        return player.hasPermission(permission);
    }
    
    public static boolean hasPermission(CommandSender sender, String permission) {
        if (sender instanceof Player) {
            return hasPermission((Player) sender, permission);
        }
        return sender.hasPermission(permission);
    }
    
    public static LuckPermsHook getLuckPermsHook() {
        return luckPermsHook;
    }
}