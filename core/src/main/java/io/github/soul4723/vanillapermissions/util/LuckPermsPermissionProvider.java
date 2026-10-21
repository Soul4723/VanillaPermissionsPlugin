package io.github.soul4723.vanillapermissions.util;

import io.github.soul4723.vanillapermissions.platform.PluginScheduler;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.OptionalInt;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * LuckPerms lookups in the player's current context, with integer meta and command list refresh from
 * {@link LuckPermsHook}. Only load this class after checking that LuckPerms is enabled.
 */
public final class LuckPermsPermissionProvider implements PermissionProvider {

    private final LuckPermsHook hook;
    private final Logger logger;

    public LuckPermsPermissionProvider(LuckPermsHook hook, Logger logger) {
        this.hook = hook;
        this.logger = logger;
    }

    /** Connects to the installed LuckPerms, or returns {@code null} after logging why it cannot be used. */
    public static LuckPermsPermissionProvider connect(Plugin plugin, PluginScheduler scheduler) {
        LuckPermsHook hook = new LuckPermsHook();
        hook.initialize(plugin, scheduler);
        return hook.isEnabled() ? new LuckPermsPermissionProvider(hook, plugin.getLogger()) : null;
    }

    @Override
    public PermissionValue getPermissionValue(Player player, String node) {
        User user = getUser(player);
        if (user == null) return PermissionValue.UNDEFINED;

        try {
            LuckPerms luckPerms = hook.getLuckPerms();
            switch (user.getCachedData().getPermissionData(
                luckPerms.getContextManager().getQueryOptions(player)
            ).queryPermission(node).result()) {
                case TRUE:
                    return PermissionValue.TRUE;
                case FALSE:
                    return PermissionValue.FALSE;
                case UNDEFINED:
                default:
                    return PermissionValue.UNDEFINED;
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Could not query LuckPerms, using the vanilla fallback: " + e.getMessage());
            }
            return PermissionValue.UNDEFINED;
        }
    }

    @Override
    public boolean hasPermission(Player player, String node) {
        if (!hook.isEnabled()) return player.hasPermission(node);
        try {
            LuckPerms luckPerms = hook.getLuckPerms();
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            return user != null && user.getCachedData().getPermissionData(
                    luckPerms.getContextManager().getQueryOptions(player)).checkPermission(node).asBoolean();
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Could not check a node with LuckPerms, falling back to Bukkit: " + e.getMessage());
            }
            return player.hasPermission(node);
        }
    }

    @Override
    public OptionalInt getIntegerMeta(UUID subjectId, CommandSender onlineSubject, String key) {
        if (!hook.isEnabled()) return OptionalInt.empty();
        try {
            LuckPerms luckPerms = hook.getLuckPerms();
            User user = luckPerms.getUserManager().getUser(subjectId);
            if (user == null) return OptionalInt.empty();

            CachedMetaData metaData;
            if (onlineSubject instanceof Player && ((Player) onlineSubject).getUniqueId().equals(subjectId)) {
                metaData = user.getCachedData().getMetaData(luckPerms.getContextManager().getQueryOptions((Player) onlineSubject));
            } else {
                metaData = user.getCachedData().getMetaData(user.getQueryOptions());
            }
            return metaData.getMetaValue(key, Integer::parseInt).map(OptionalInt::of).orElseGet(OptionalInt::empty);
        } catch (Exception e) {
            if (logger != null) logger.warning("Could not query LuckPerms integer meta: " + e.getMessage());
            return OptionalInt.empty();
        }
    }

    private User getUser(Player player) {
        if (!hook.isEnabled()) return null;
        try {
            return hook.getLuckPerms().getUserManager().getUser(player.getUniqueId());
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Could not look up the LuckPerms user: " + e.getMessage());
            }
            return null;
        }
    }

    @Override
    public String describePermissions() {
        if (!hook.isEnabled()) return "§cLuckPerms not connected";
        return "§aLuckPerms " + hook.getLuckPerms().getPluginMetadata().getVersion()
            + " §7(contexts, command list refresh on changes)";
    }

    @Override
    public String describeMeta() {
        return hook.isEnabled() ? "§aLuckPerms meta" : "§coff §7(LuckPerms not connected)";
    }

    @Override
    public void close() {
        hook.close();
    }

    public LuckPermsHook getHook() {
        return hook;
    }
}
