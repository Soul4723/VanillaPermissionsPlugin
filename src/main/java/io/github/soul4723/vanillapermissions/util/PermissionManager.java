package io.github.soul4723.vanillapermissions.util;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.util.Tristate;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.OptionalInt;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * LuckPerms lookups in the player's current context. {@link #queryPermission} keeps the unset state so callers
 * can fall back to vanilla; {@link #hasPermission(Player, String)} treats unset as not granted.
 */
public class PermissionManager {

    private static LuckPermsHook luckPermsHook;
    private static Logger logger;

    public static void initialize(LuckPermsHook luckPermsHook, JavaPlugin plugin) {
        PermissionManager.luckPermsHook = luckPermsHook;
        PermissionManager.logger = plugin.getLogger();
    }

    public static Tristate queryPermission(CommandSender sender, String permission) {
        User user = getUser(sender);
        if (user == null) return Tristate.UNDEFINED;

        try {
            LuckPerms luckPerms = luckPermsHook.getLuckPerms();
            return user.getCachedData().getPermissionData(
                luckPerms.getContextManager().getQueryOptions((Player) sender)
            ).queryPermission(permission).result();
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Could not query LuckPerms, using the vanilla fallback: " + e.getMessage());
            }
            return Tristate.UNDEFINED;
        }
    }

    public static boolean hasPermissionOrFallback(CommandSender sender, String permission, boolean fallback) {
        return switch (queryPermission(sender, permission)) {
            case TRUE -> true;
            case FALSE -> false;
            case UNDEFINED -> fallback;
        };
    }

    public static OptionalInt getIntegerMeta(UUID subjectId, CommandSender onlineSubject, String key) {
        if (luckPermsHook == null || !luckPermsHook.isEnabled()) return OptionalInt.empty();
        try {
            LuckPerms luckPerms = luckPermsHook.getLuckPerms();
            User user = luckPerms.getUserManager().getUser(subjectId);
            if (user == null) return OptionalInt.empty();

            CachedMetaData metaData;
            if (onlineSubject instanceof Player player && player.getUniqueId().equals(subjectId)) {
                metaData = user.getCachedData().getMetaData(luckPerms.getContextManager().getQueryOptions(player));
            } else {
                metaData = user.getCachedData().getMetaData(user.getQueryOptions());
            }
            return metaData.getMetaValue(key, Integer::parseInt).map(OptionalInt::of).orElseGet(OptionalInt::empty);
        } catch (Exception e) {
            if (logger != null) logger.warning("Could not query LuckPerms integer meta: " + e.getMessage());
            return OptionalInt.empty();
        }
    }

    private static User getUser(CommandSender sender) {
        if (!(sender instanceof Player player) || luckPermsHook == null || !luckPermsHook.isEnabled()) {
            return null;
        }
        try {
            return luckPermsHook.getLuckPerms().getUserManager().getUser(player.getUniqueId());
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Could not look up the LuckPerms user: " + e.getMessage());
            }
            return null;
        }
    }

    public static boolean hasPermission(CommandSender sender, String permission) {
        if (sender instanceof Player) {
            return hasPermission((Player) sender, permission);
        }

        try {
            return sender.hasPermission(permission);
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Could not check a node for a non-player sender: " + e.getMessage());
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
                User user = luckPerms.getUserManager().getUser(player.getUniqueId());
                hasPermission = user != null && user.getCachedData().getPermissionData(
                        luckPerms.getContextManager().getQueryOptions(player)).checkPermission(permission).asBoolean();
            } catch (Exception e) {
                if (logger != null) {
                    logger.warning("Could not check a node with LuckPerms, falling back to Bukkit: " + e.getMessage());
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

}
