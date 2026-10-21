package io.github.soul4723.vanillapermissions.util;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.OptionalInt;
import java.util.UUID;

/**
 * Where node values and selector meta come from: {@link LuckPermsPermissionProvider} when LuckPerms is running,
 * otherwise {@link BukkitPermissionProvider}, which works with any permissions plugin through Bukkit's API.
 * The methods mirror fabric-permissions-api ({@code Permissions.getPermissionValue}, {@code Options.get}), so a
 * Fabric module would need a single provider over that API.
 */
public interface PermissionProvider extends AutoCloseable {

    /** The node's value for the player, keeping the unset state (fabric-permissions-api's {@code getPermissionValue}). */
    PermissionValue getPermissionValue(Player player, String node);

    /** Whether the player has the node, with unset resolved by the provider's defaults. */
    boolean hasPermission(Player player, String node);

    /**
     * Whether the player has the node during {@code PlayerLoginEvent}, when some permissions plugins have not attached
     * their data yet. Upstream reads these through fabric-permissions-api's offline check for the same reason.
     */
    default boolean hasPermissionAtLogin(Player player, String node) {
        return hasPermission(player, node);
    }

    /** Integer meta, e.g. {@code minecraft.selector.limit.<path>}; empty if unset or unavailable ({@code Options.get}). */
    OptionalInt getIntegerMeta(UUID subjectId, CommandSender onlineSubject, String key);

    /** One line for {@code /vanillapermissions debug} saying where node values come from. */
    String describePermissions();

    /** One line for {@code /vanillapermissions debug} saying where selector limits and weights come from. */
    String describeMeta();

    @Override
    default void close() {}
}
