package io.github.soul4723.vanillapermissions.util;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.model.user.User;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.OptionalInt;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * LuckPerms lookups in the player's current context, with integer meta. Only load this class after checking that
 * LuckPerms is enabled. Command trees are not resent after permission changes: LuckPerms does that itself
 * ({@code update-client-command-list}, on by default).
 */
public final class LuckPermsPermissionProvider implements PermissionProvider {

    private final LuckPerms luckPerms;
    private final Logger logger;
    private volatile boolean enabled = true;

    public LuckPermsPermissionProvider(LuckPerms luckPerms, Logger logger) {
        this.luckPerms = luckPerms;
        this.logger = logger;
    }

    /** Connects to {@code luckPermsPlugin}'s API, or returns {@code null} after logging why it cannot be used. */
    public static LuckPermsPermissionProvider connect(Plugin plugin, Plugin luckPermsPlugin) {
        Logger log = plugin.getLogger();
        // A LuckPerms that failed to start no longer provides its API classes, so do not touch them.
        if (!luckPermsPlugin.isEnabled()) {
            log.severe("Could not use LuckPerms because it is not enabled; fix the LuckPerms errors earlier in the log");
            return null;
        }
        try {
            RegisteredServiceProvider<LuckPerms> registration =
                plugin.getServer().getServicesManager().getRegistration(LuckPerms.class);
            if (registration == null) {
                log.severe("Could not use LuckPerms because it has not registered its API; check the LuckPerms startup log");
                return null;
            }
            return new LuckPermsPermissionProvider(registration.getProvider(), log);
        } catch (RuntimeException e) {
            log.severe("Could not connect to the LuckPerms API: " + e.getMessage());
            return null;
        }
    }

    @Override
    public PermissionValue getPermissionValue(Player player, String node) {
        if (!enabled) return PermissionValue.UNDEFINED;
        try {
            CachedPermissionData data = permissionData(player);
            if (data == null) return PermissionValue.UNDEFINED;
            switch (data.checkPermission(node)) {
                case TRUE:
                    return PermissionValue.TRUE;
                case FALSE:
                    return PermissionValue.FALSE;
                default:
                    return PermissionValue.UNDEFINED;
            }
        } catch (Exception e) {
            if (logger != null) logger.warning("Could not query LuckPerms, using the vanilla fallback: " + e.getMessage());
            return PermissionValue.UNDEFINED;
        }
    }

    @Override
    public boolean hasPermission(Player player, String node) {
        if (!enabled) return player.hasPermission(node);
        try {
            CachedPermissionData data = permissionData(player);
            return data != null && data.checkPermission(node).asBoolean();
        } catch (Exception e) {
            if (logger != null) logger.warning("Could not check a node with LuckPerms, falling back to Bukkit: " + e.getMessage());
            return player.hasPermission(node);
        }
    }

    /** The player's permission data in their current context, or {@code null} if LuckPerms has not loaded them. */
    private CachedPermissionData permissionData(Player player) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        return user == null ? null : user.getCachedData().getPermissionData(luckPerms.getContextManager().getQueryOptions(player));
    }

    @Override
    public OptionalInt getIntegerMeta(UUID subjectId, CommandSender onlineSubject, String key) {
        if (!enabled) return OptionalInt.empty();
        try {
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

    @Override
    public String describePermissions() {
        if (!enabled) return "§cLuckPerms not connected";
        return "§aLuckPerms " + luckPerms.getPluginMetadata().getVersion()
            + " §7(contexts; LuckPerms refreshes command lists on changes)";
    }

    @Override
    public String describeMeta() {
        return enabled ? "§aLuckPerms meta" : "§coff §7(LuckPerms not connected)";
    }

    @Override
    public void close() {
        enabled = false;
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }
}
