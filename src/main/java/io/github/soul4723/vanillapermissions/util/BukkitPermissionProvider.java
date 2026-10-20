package io.github.soul4723.vanillapermissions.util;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.OptionalInt;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Bukkit's permission API, which every permissions plugin backs. A node is unset when
 * {@link Player#isPermissionSet} is false: no plugin assigned it and no registered default applies to the player.
 * Selector meta needs a separate source (Vault chat meta); without one, limits and weights are off.
 */
public final class BukkitPermissionProvider implements PermissionProvider {

    /** Integer meta lookup that does not depend on the permissions plugin, e.g. {@link VaultHook}. */
    public interface IntegerMeta {
        OptionalInt get(UUID subjectId, CommandSender onlineSubject, String key);

        String describe();
    }

    /** A stored-data lookup for {@code PlayerLoginEvent}, e.g. Vault's permission service. Grant-only. */
    public interface LoginPermissions {
        boolean has(Player player, String node);
    }

    private final String permissionsPlugin;
    private final IntegerMeta meta;
    private final LoginPermissions loginPermissions;
    private final Logger logger;

    /**
     * @param permissionsPlugin name of the detected permissions plugin, or {@code null} for Bukkit defaults only
     * @param meta integer meta source, or {@code null} if there is none
     * @param loginPermissions lookup used before the plugin attaches permissions, or {@code null}
     */
    public BukkitPermissionProvider(String permissionsPlugin, IntegerMeta meta, LoginPermissions loginPermissions, Logger logger) {
        this.permissionsPlugin = permissionsPlugin;
        this.meta = meta;
        this.loginPermissions = loginPermissions;
        this.logger = logger;
    }

    public BukkitPermissionProvider(String permissionsPlugin, IntegerMeta meta, Logger logger) {
        this(permissionsPlugin, meta, null, logger);
    }

    @Override
    public PermissionValue getPermissionValue(Player player, String node) {
        try {
            if (!player.isPermissionSet(node)) return PermissionValue.UNDEFINED;
            return player.hasPermission(node) ? PermissionValue.TRUE : PermissionValue.FALSE;
        } catch (RuntimeException e) {
            if (logger != null) logger.warning("Could not query a Bukkit permission, using the vanilla fallback: " + e.getMessage());
            return PermissionValue.UNDEFINED;
        }
    }

    @Override
    public boolean hasPermission(Player player, String node) {
        return player.hasPermission(node);
    }

    /** Some plugins (GroupManager) attach permissions only at join, so also ask their stored data. */
    @Override
    public boolean hasPermissionAtLogin(Player player, String node) {
        if (player.hasPermission(node)) return true;
        if (loginPermissions == null) return false;
        try {
            return loginPermissions.has(player, node);
        } catch (RuntimeException e) {
            if (logger != null) logger.warning("Could not check a login permission through Vault: " + e.getMessage());
            return false;
        }
    }

    @Override
    public OptionalInt getIntegerMeta(UUID subjectId, CommandSender onlineSubject, String key) {
        return meta == null ? OptionalInt.empty() : meta.get(subjectId, onlineSubject, key);
    }

    @Override
    public String describePermissions() {
        return permissionsPlugin == null
            ? "§eBukkit defaults and operator status only §7(no permissions plugin found)"
            : "§aBukkit API §7(" + permissionsPlugin + "; command list refresh after changes depends on it)";
    }

    @Override
    public String describeMeta() {
        return meta == null ? "§eoff §7(needs LuckPerms, or Vault with a chat meta provider)" : meta.describe();
    }

    public String getPermissionsPlugin() {
        return permissionsPlugin;
    }

    public boolean hasMeta() {
        return meta != null;
    }
}
