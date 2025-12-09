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
    private static net.luckperms.api.event.EventSubscription<UserDataRecalculateEvent> eventSubscription;
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
                    eventSubscription = luckPerms.getEventBus().subscribe(UserDataRecalculateEvent.class, event -> {
                        String userPrefix = event.getUser().getUniqueId() + ":";
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
        
        String cacheKey = player.getUniqueId() + ":" + permission;
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
    
    public static void close() {
        if (eventSubscription != null) {
            eventSubscription.close();
            eventSubscription = null;
        }
    }
    
    public static LuckPermsHook getLuckPermsHook() {
        return luckPermsHook;
    }
    
    public static String getCacheStats() {
        return "Cache size: " + permissionCache.size() + ", LuckPerms enabled: " + 
               (luckPermsHook != null && luckPermsHook.isEnabled() ? "true" : "false");
    }
    
    
    public static boolean hasPermissionOrParent(CommandSender sender, String permission) {
        if (sender.isOp()) return true;
        
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
    
    private static boolean isExplicitlyDenied(CommandSender sender, String permission) {
        if (!(sender instanceof Player)) return false;
        
        Player player = (Player) sender;
        if (luckPermsHook != null && luckPermsHook.isEnabled()) {
            try {
                net.luckperms.api.model.user.User user = luckPermsHook.getLuckPerms().getUserManager().getUser(player.getUniqueId());
                if (user != null) {
                    return !user.getCachedData().getPermissionData().checkPermission(permission).asBoolean() &&
                           user.getCachedData().getPermissionData().queryPermission(permission).result() != net.luckperms.api.util.Tristate.UNDEFINED;
                }
            } catch (Exception e) {
                return false;
            }
        }
        
        return false;
    }
}
