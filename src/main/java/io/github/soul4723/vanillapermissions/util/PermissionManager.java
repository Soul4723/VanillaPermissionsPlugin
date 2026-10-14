package io.github.soul4723.vanillapermissions.util;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.model.user.User;
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

        CachedPermissionData data = null;
        if (sender instanceof Player player && luckPermsHook != null && luckPermsHook.isEnabled()) {
            try {
                LuckPerms luckPerms = luckPermsHook.getLuckPerms();
                User user = luckPerms.getUserManager().getUser(player.getUniqueId());
                if (user == null) return false;
                data = user.getCachedData().getPermissionData(
                        luckPerms.getContextManager().getQueryOptions(player));
            } catch (Exception e) {
                if (logger != null) {
                    logger.warning("LuckPerms permission check failed, falling back to Bukkit: " + e.getMessage());
                }
            }
        }

        for (int end = permission.length(); end > 0;) {
            String current = permission.substring(0, end);
            if (data != null) {
                try {
                    if (data.checkPermission(current).asBoolean()) return true;
                    if (data.queryPermission(current).result() != net.luckperms.api.util.Tristate.UNDEFINED) return false;
                } catch (Exception e) {
                    if (logger != null) {
                        logger.warning("LuckPerms permission check failed, falling back to Bukkit: " + e.getMessage());
                    }
                    if (hasBukkitPermission(sender, current)) return true;
                }
            } else if (hasBukkitPermission(sender, current)) {
                return true;
            }

            int dot = permission.lastIndexOf('.', end - 1);
            if (dot < 0) return false;
            end = dot;
        }

        return false;
    }

    private static boolean shouldOpBypass(CommandSender sender) {
        if (!sender.isOp()) return false;

        if (plugin instanceof io.github.soul4723.vanillapermissions.VanillaPermissionsPlugin) {
            io.github.soul4723.vanillapermissions.VanillaPermissionsPlugin ep =
                (io.github.soul4723.vanillapermissions.VanillaPermissionsPlugin) plugin;

            return ep.isOpsBypassAll();
        }

        return true;
    }

    private static boolean hasBukkitPermission(CommandSender sender, String permission) {
        try {
            return sender.hasPermission(permission);
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Permission check failed for sender: " + e.getMessage());
            }
            return false;
        }
    }
}
