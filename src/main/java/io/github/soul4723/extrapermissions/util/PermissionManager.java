package io.github.soul4723.extrapermissions.util;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for managing permissions with LuckPerms integration and caching.
 * Provides optimized permission checking with fallback to Bukkit permissions.
 */
public class PermissionManager {
    
    private static LuckPermsHook luckPermsHook;
    
    // Permission caching for performance
    private static final Map<String, CachedPermission> permissionCache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 30000; // 30 seconds
    
    // Cache entry class
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
    
    /**
     * Initialize the PermissionManager with LuckPerms hook
     * @param luckPermsHook The LuckPerms integration hook
     */
    public static void initialize(LuckPermsHook luckPermsHook) {
        PermissionManager.luckPermsHook = luckPermsHook;
        
        // Register event listener for cache invalidation if LuckPerms is available
        if (luckPermsHook != null && luckPermsHook.isEnabled()) {
            try {
                LuckPerms luckPerms = luckPermsHook.getLuckPerms();
                if (luckPerms != null) {
                    luckPerms.getEventBus().subscribe(UserDataRecalculateEvent.class, event -> {
                        // Clear cache for this user when permissions are recalculated
                        String userPrefix = event.getUser().getUniqueId().toString() + ":";
                        permissionCache.entrySet().removeIf(entry -> entry.getKey().startsWith(userPrefix));
                    });
                }
            } catch (Exception e) {
                Bukkit.getLogger().warning("Failed to register LuckPerms event listener: " + e.getMessage());
            }
        }
    }
    
    /**
     * Check if a command sender has permission
     * @param sender The command sender (player or console)
     * @param permission The permission node to check
     * @return true if sender has permission, false otherwise
     */
    public static boolean hasPermission(CommandSender sender, String permission) {
        if (sender instanceof Player) {
            return hasPermission((Player) sender, permission);
        }
        
        // For non-players (console, command blocks), use Bukkit permissions directly
        try {
            return sender.hasPermission(permission);
        } catch (Exception e) {
            Bukkit.getLogger().warning("Permission check failed for non-player sender: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if a player has permission
     * @param player The player to check
     * @param permission The permission node to check
     * @return true if player has permission, false otherwise
     */
    public static boolean hasPermission(Player player, String permission) {
        if (player == null) {
            return false;
        }
        
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
    
    public static void clearCacheForPlayer(UUID playerUuid) {
        String userPrefix = playerUuid.toString() + ":";
        permissionCache.entrySet().removeIf(entry -> entry.getKey().startsWith(userPrefix));
    }
    
    public static LuckPermsHook getLuckPermsHook() {
        return luckPermsHook;
    }
    
    // Debug method for troubleshooting
    public static String getCacheStats() {
        return "Cache size: " + permissionCache.size() + ", LuckPerms enabled: " + 
               (luckPermsHook != null && luckPermsHook.isEnabled());
    }
}