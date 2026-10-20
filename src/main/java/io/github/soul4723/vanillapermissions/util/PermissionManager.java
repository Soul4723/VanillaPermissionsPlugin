package io.github.soul4723.vanillapermissions.util;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Node lookups through the selected {@link PermissionProvider}. {@link #queryPermission} keeps the unset state so
 * callers can fall back to vanilla; {@link #hasPermission(Player, String)} treats unset as the provider's default.
 * Console and other non-player senders are never affected by command or selector nodes.
 */
public class PermissionManager {

    /** Permissions plugins recognised without Vault, as in EssentialsX's superperms handler. */
    static final List<String> KNOWN_PERMISSIONS_PLUGINS = List.of("LuckPerms", "PermissionsEx", "GroupManager",
        "UltraPermissions", "PowerRanks", "bPermissions", "zPermissions", "PermissionsBukkit", "SimplyPerms",
        "Privileges", "DroxPerms", "xPerms");

    private static volatile PermissionProvider provider = new BukkitPermissionProvider(null, null, null);
    private static Logger logger;

    public static void initialize(PermissionProvider provider, Plugin plugin) {
        PermissionManager.provider = provider;
        PermissionManager.logger = plugin.getLogger();
    }

    /**
     * LuckPerms when it is running, otherwise Bukkit's API with Vault chat meta if available. Logs one line for the
     * permission source and one for selector meta, so admins can see what works without LuckPerms. This runs in
     * {@code onEnable}, before plugins that enable after this one; {@link #reselectProvider} runs once they all have.
     */
    public static PermissionProvider selectProvider(Plugin plugin) {
        PluginManager plugins = plugin.getServer().getPluginManager();
        Logger log = plugin.getLogger();

        Plugin luckPerms = plugins.getPlugin("LuckPerms");
        if (luckPerms != null) {
            PermissionProvider connected = LuckPermsPermissionProvider.connect(plugin);
            if (connected != null) {
                log.info("Using LuckPerms " + luckPerms.getPluginMeta().getVersion()
                    + " for permission checks, contexts, selector limits and weights, and command list refresh");
                return connected;
            }
            log.severe("Falling back to Bukkit permissions without LuckPerms; fix LuckPerms to restore its nodes");
        }

        BukkitSelection selection = selectBukkitProvider(plugin, true);
        if (selection.provider().getPermissionsPlugin() != null) {
            log.info("Using Bukkit permissions from " + selection.provider().getPermissionsPlugin()
                + "; whether command lists refresh after a permission change depends on that plugin");
        }
        log.info(selection.metaLine());
        return selection.provider();
    }

    /**
     * Selects again once every plugin has enabled, because a permissions plugin that enables after this one is missed
     * by {@link #selectProvider}. Swaps the provider only if the source changed, logging one line for the result, and
     * then warns, once and only here, if there is still no permissions plugin.
     */
    public static void reselectProvider(Plugin plugin) {
        PermissionProvider current = provider;
        Plugin luckPerms = plugin.getServer().getPluginManager().getPlugin("LuckPerms");
        // LuckPerms is a softdepend, so it enabled before this plugin and nothing that loads later replaces it.
        boolean keepLuckPerms = current instanceof LuckPermsPermissionProvider && luckPerms != null && luckPerms.isEnabled();
        if (!keepLuckPerms) {
            BukkitSelection selection = selectBukkitProvider(plugin, false);
            if (!(current instanceof BukkitPermissionProvider bukkit) || !bukkit.sameSourceAs(selection.provider())) {
                provider = selection.provider();
                current.close();
                plugin.getLogger().info("Selected after all plugins enabled: " + selection.summary());
            }
        }
        if (provider instanceof BukkitPermissionProvider bukkit && bukkit.getPermissionsPlugin() == null) {
            plugin.getLogger().warning("No permissions plugin found, so every node follows Bukkit defaults and operator status; "
                + "install a permissions plugin such as LuckPerms for granular control");
        }
    }

    /** A Bukkit provider with the text to log for it. */
    private record BukkitSelection(BukkitPermissionProvider provider, String summary, String metaLine) {}

    /** {@code log} is false when selecting again after startup, so a repeated Vault failure is not reported twice. */
    private static BukkitSelection selectBukkitProvider(Plugin plugin, boolean log) {
        Server server = plugin.getServer();
        PluginManager plugins = server.getPluginManager();
        Logger logger = plugin.getLogger();

        VaultHook vault = null;
        BukkitPermissionProvider.LoginPermissions loginPermissions = null;
        String permissionsPlugin = null;
        Plugin vaultPlugin = plugins.getPlugin("Vault");
        if (vaultPlugin != null && vaultPlugin.isEnabled()) {
            try {
                vault = VaultHook.connect(server, logger);
                permissionsPlugin = VaultHook.permissionsPlugin(server);
                loginPermissions = VaultHook.loginPermissions(server);
            } catch (RuntimeException | LinkageError e) {
                if (log) logger.warning("Could not use the Vault API: " + e);
            }
        }
        if (permissionsPlugin == null) permissionsPlugin = detectPermissionsPlugin(plugins);

        String metaLine = vault != null
            ? "Selector limits and weights come from Vault chat meta (" + vault.getChatName() + ")"
            : "Selector limits and weights are off: they need LuckPerms, or Vault with a chat meta provider";
        String summary = (permissionsPlugin == null ? "Bukkit defaults only" : "Bukkit permissions from " + permissionsPlugin)
            + "; " + metaLine.substring(0, 1).toLowerCase(Locale.ROOT) + metaLine.substring(1);
        return new BukkitSelection(new BukkitPermissionProvider(permissionsPlugin, vault, loginPermissions, logger),
            summary, metaLine);
    }

    static String detectPermissionsPlugin(PluginManager plugins) {
        for (String name : KNOWN_PERMISSIONS_PLUGINS) {
            Plugin plugin = plugins.getPlugin(name);
            if (plugin != null && plugin.isEnabled()) return name;
        }
        return null;
    }

    public static PermissionValue queryPermission(CommandSender sender, String permission) {
        if (!(sender instanceof Player player)) return PermissionValue.UNDEFINED;
        return provider.getPermissionValue(player, permission);
    }

    public static boolean hasPermissionOrFallback(CommandSender sender, String permission, boolean fallback) {
        return queryPermission(sender, permission).orElse(fallback);
    }

    public static OptionalInt getIntegerMeta(UUID subjectId, CommandSender onlineSubject, String key) {
        return provider.getIntegerMeta(subjectId, onlineSubject, key);
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
        return provider.hasPermission(player, permission);
    }

    /** For checks during {@code PlayerLoginEvent}; see {@link PermissionProvider#hasPermissionAtLogin}. */
    public static boolean hasPermissionAtLogin(Player player, String permission) {
        return player != null && provider.hasPermissionAtLogin(player, permission);
    }

    public static PermissionProvider getProvider() {
        return provider;
    }

}
