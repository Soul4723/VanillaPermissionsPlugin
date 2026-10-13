package io.github.soul4723.extrapermissions.util;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedPermissionData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public class PermissionManager {
    
    private static LuckPermsHook luckPermsHook;
    private static Logger logger;
    private static JavaPlugin plugin;
    public static void initialize(LuckPermsHook luckPermsHook, JavaPlugin plugin) {
        PermissionManager.luckPermsHook = luckPermsHook;
        PermissionManager.logger = plugin.getLogger();
        PermissionManager.plugin = plugin;
    }
    
    public static boolean hasPermission(CommandSender sender, String permission) {
        if (sender instanceof Player) {
            return hasPermission((Player) sender, permission);
        }
        
        try {
            return sender.hasPermission(permission);
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Permission check failed for non-player sender: " + e.getMessage());
            }
            return false;
        }
    }
    
    public static boolean hasPermission(Player player, String permission) {
        if (player == null) return false;
        
        boolean hasPermission;
        if (luckPermsHook != null && luckPermsHook.isEnabled()) {
            try {
                LuckPerms luckPerms = luckPermsHook.getLuckPerms();
                net.luckperms.api.model.user.User user = luckPerms.getUserManager().getUser(player.getUniqueId());
                hasPermission = user != null && user.getCachedData().getPermissionData(
                        luckPerms.getContextManager().getQueryOptions(player)).checkPermission(permission).asBoolean();
            } catch (Exception e) {
                if (logger != null) {
                    logger.warning("LuckPerms permission check failed, falling back to Bukkit: " + e.getMessage());
                }
                hasPermission = player.hasPermission(permission);
            }
        } else {
            hasPermission = player.hasPermission(permission);
        }
        
        return hasPermission;
    }
    
    public static LuckPermsHook getLuckPermsHook() {
        return luckPermsHook;
    }
    
    public static boolean hasPermissionOrParent(CommandSender sender, String permission) {
        if (shouldOpBypass(sender)) return true;
        
        if (isExplicitlyDenied(sender, permission)) return false;
        if (hasPermission(sender, permission)) return true;
        
        String[] parts = permission.split("\\.");
        for (int i = parts.length - 1; i > 0; i--) {
            String[] parentParts = new String[i];
            System.arraycopy(parts, 0, parentParts, 0, i);
            String parentPerm = String.join(".", parentParts);
            if (isExplicitlyDenied(sender, parentPerm)) return false;
            if (hasPermission(sender, parentPerm)) return true;
        }
        
        return false;
    }
    
    private static boolean shouldOpBypass(CommandSender sender) {
        if (!sender.isOp()) return false;
        
        if (plugin instanceof io.github.soul4723.extrapermissions.ExtraPermissions) {
            io.github.soul4723.extrapermissions.ExtraPermissions ep = 
                (io.github.soul4723.extrapermissions.ExtraPermissions) plugin;
            
            return ep.isOpsBypassAll();
        }
        
        return true;
    }
    
    private static boolean isExplicitlyDenied(CommandSender sender, String permission) {
        if (!(sender instanceof Player)) return false;
        
        Player player = (Player) sender;
        if (luckPermsHook != null && luckPermsHook.isEnabled()) {
            try {
                LuckPerms luckPerms = luckPermsHook.getLuckPerms();
                net.luckperms.api.model.user.User user = luckPerms.getUserManager().getUser(player.getUniqueId());
                if (user != null) {
                    CachedPermissionData data = user.getCachedData().getPermissionData(
                            luckPerms.getContextManager().getQueryOptions(player));
                    return !data.checkPermission(permission).asBoolean() &&
                           data.queryPermission(permission).result() != net.luckperms.api.util.Tristate.UNDEFINED;
                }
            } catch (Exception e) {
                return false;
            }
        }
        
        return false;
    }
}
