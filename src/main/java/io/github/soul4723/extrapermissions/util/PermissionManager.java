package io.github.soul4723.extrapermissions.util;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PermissionManager {
    
    private static LuckPermsHook luckPermsHook;
    private static final Map<String, CachedPermission> permissionCache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 30000;
    private static class CachedPermission {
        final boolean value;
        final long timestamp;
        
        CachedPermission(boolean value) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION;
        }
    }
    
    public static void initialize(LuckPermsHook luckPermsHook) {
        PermissionManager.luckPermsHook = luckPermsHook;
        
        if (luckPermsHook != null && luckPermsHook.isEnabled()) {
            try {
                LuckPerms luckPerms = luckPermsHook.getLuckPerms();
                if (luckPerms != null) {
                    luckPerms.getEventBus().subscribe(UserDataRecalculateEvent.class, event -> {
                        String userPrefix = event.getUser().getUniqueId().toString() + ":";
                        permissionCache.entrySet().removeIf(entry -> entry.getKey().startsWith(userPrefix));
                    });
                }
            } catch (Exception e) {
                Bukkit.getLogger().warning("Failed to register LuckPerms event listener: " + e.getMessage());
            }
        }
    }
    
    public static boolean hasPermission(CommandSender sender, String permission) {
        if (sender instanceof Player) {
            return hasPermission((Player) sender, permission);
        }
        
        try {
            return sender.hasPermission(permission);
        } catch (Exception e) {
            Bukkit.getLogger().warning("Permission check failed for non-player sender: " + e.getMessage());
            return false;
        }
    }
    
    public static boolean hasPermission(Player player, String permission) {
        if (player == null) return false;
        
        String cacheKey = player.getUniqueId().toString() + ":" + permission;
        CachedPermission cached = permissionCache.get(cacheKey);
        
        if (cached != null && !cached.isExpired()) {
            return cached.value;
        }
        
        boolean hasPermission;
        if (luckPermsHook != null && luckPermsHook.isEnabled()) {
            try {
                net.luckperms.api.model.user.User user = luckPermsHook.getLuckPerms().getUserManager().getUser(player.getUniqueId());
                hasPermission = user != null && user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
            } catch (Exception e) {
                Bukkit.getLogger().warning("LuckPerms permission check failed, falling back to Bukkit: " + e.getMessage());
                hasPermission = player.hasPermission(permission);
            }
        } else {
            hasPermission = player.hasPermission(permission);
        }
        
        permissionCache.put(cacheKey, new CachedPermission(hasPermission));
        return hasPermission;
    }
    
    public static void clearCache() {
        permissionCache.clear();
    }
    
    public static LuckPermsHook getLuckPermsHook() {
        return luckPermsHook;
    }
    
    public static String getCacheStats() {
        return "Cache size: " + permissionCache.size() + ", LuckPerms enabled: " + 
               (luckPermsHook != null && luckPermsHook.isEnabled());
    }
}