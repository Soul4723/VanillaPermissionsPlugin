package io.github.soul4723.vanillapermissions.util;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.OptionalInt;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Selector meta from Vault's chat API when LuckPerms is not installed. Only load this class after checking that
 * Vault is enabled, because its API classes are missing otherwise.
 */
public final class VaultHook implements BukkitPermissionProvider.IntegerMeta {

    /** Vault's own permission service, which only wraps Bukkit's API and so names no permissions plugin. */
    private static final String SUPERPERMS = "SuperPerms";
    /** Vault's integer getter has no unset state, so ask with a default that no limit or weight uses. */
    private static final int UNSET = Integer.MIN_VALUE;

    private final Server server;
    private final Chat chat;
    private final Logger logger;

    private VaultHook(Server server, Chat chat, Logger logger) {
        this.server = server;
        this.chat = chat;
        this.logger = logger;
    }

    /** The chat meta hook, or {@code null} if no plugin registered a Vault chat provider. */
    public static VaultHook connect(Server server, Logger logger) {
        RegisteredServiceProvider<Chat> registration = server.getServicesManager().getRegistration(Chat.class);
        if (registration == null || registration.getProvider() == null) return null;
        return new VaultHook(server, registration.getProvider(), logger);
    }

    /** The permissions plugin behind Vault's permission service, or {@code null} if Vault only wraps Bukkit. */
    public static String permissionsPlugin(Server server) {
        RegisteredServiceProvider<Permission> registration = server.getServicesManager().getRegistration(Permission.class);
        if (registration == null || registration.getProvider() == null) return null;
        String name = registration.getProvider().getName();
        return name == null || SUPERPERMS.equals(name) ? null : name;
    }

    /**
     * Vault's {@link Permission#playerHas}, which reads the permissions plugin's stored data, for login-time checks;
     * {@code null} if Vault only wraps Bukkit, since that answers the same as {@link Player#hasPermission}.
     */
    public static BukkitPermissionProvider.LoginPermissions loginPermissions(Server server) {
        if (permissionsPlugin(server) == null) return null;
        Permission permission = server.getServicesManager().getRegistration(Permission.class).getProvider();
        return (player, node) -> permission.playerHas(player.getWorld().getName(), player, node);
    }

    /** Only online players are looked up, so the main thread never waits for a plugin to load offline data. */
    @Override
    public OptionalInt get(UUID subjectId, CommandSender onlineSubject, String key) {
        Player player = onlineSubject instanceof Player online && online.getUniqueId().equals(subjectId)
            ? online : server.getPlayer(subjectId);
        if (player == null) return OptionalInt.empty();
        try {
            // Most providers store meta as text, which their integer getter may not resolve, so read text first.
            String text = chat.getPlayerInfoString(player, key, null);
            if (text != null && !text.isBlank()) {
                try {
                    return OptionalInt.of(Integer.parseInt(text.trim()));
                } catch (NumberFormatException e) {
                    return OptionalInt.empty();
                }
            }
            int value = chat.getPlayerInfoInteger(player, key, UNSET);
            return value == UNSET ? OptionalInt.empty() : OptionalInt.of(value);
        } catch (RuntimeException e) {
            if (logger != null) logger.warning("Could not query Vault integer meta: " + e.getMessage());
            return OptionalInt.empty();
        }
    }

    @Override
    public String describe() {
        return "§aVault chat meta §7(" + chat.getName() + ")";
    }

    public String getChatName() {
        return chat.getName();
    }
}
