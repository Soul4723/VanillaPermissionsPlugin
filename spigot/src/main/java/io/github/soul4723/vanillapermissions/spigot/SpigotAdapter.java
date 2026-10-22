package io.github.soul4723.vanillapermissions.spigot;

import io.github.soul4723.vanillapermissions.brigadier.BrigadierFeatures;
import io.github.soul4723.vanillapermissions.brigadier.BrigadierPermissions;
import io.github.soul4723.vanillapermissions.platform.AdapterLog;
import io.github.soul4723.vanillapermissions.platform.ServerVersion;
import io.github.soul4723.vanillapermissions.platform.VersionAdapter;
import io.github.soul4723.vanillapermissions.util.ServerSoftware;
import io.github.soul4723.vanillapermissions.util.SupportedVersions;
import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Spigot 1.13+ and Paper 1.13 - 1.20.5: permission checks on CraftBukkit's vanilla Brigadier dispatcher, installed
 * directly in {@code onEnable}. During a normal start that runs before CraftBukkit wraps the vanilla commands for Bukkit
 * and syncs them into the live dispatcher, so the {@code minecraft:} copies and the tree sent to players inherit the
 * checks. When the plugin is enabled after startup, the commands are synced again after the install.
 *
 * <p>Once CraftBukkit has wrapped the vanilla commands ({@code setVanillaCommands}, after the POSTWORLD plugins enable),
 * {@link AliasPermissions} gives the alias wrappers ({@code /tp}, {@code /tell}...) their own Bukkit permission node.
 */
public final class SpigotAdapter implements VersionAdapter {

    private BrigadierPermissions permissions;
    private CraftServerAccess craft;
    private JavaPlugin plugin;
    private final AliasPermissions aliases = new AliasPermissions();
    private BukkitTask aliasTask;
    private List<String> unavailableBypasses = Collections.emptyList();

    @Override
    public String id() {
        return "spigot";
    }

    @Override
    public void enable(JavaPlugin plugin) {
        this.plugin = plugin;
        BrigadierFeatures.registerListeners(plugin);
        unavailableBypasses = PaperEventBypasses.register(plugin);

        Server server = plugin.getServer();
        try {
            craft = CraftServerAccess.of(server);
            SpigotBridge bridge = new SpigotBridge(server.getClass().getClassLoader(), craft.minecraftServer());
            permissions = new BrigadierPermissions(plugin, bridge, scheduler(plugin));
            MappingGeneration generation = MappingGeneration.detect(server.getClass(), server.getClass().getClassLoader());
            bridge.resolve(generation);
            boolean late = craft.vanillaCommandsRegistered();
            permissions.install(craft.vanillaDispatcher());
            logSkipped(plugin.getLogger(), bridge.skippedPluginCommands());
            if (late) {
                craft.syncCommands();
                plugin.getLogger().info("Re-synced the server's commands because VanillaPermissionsPlugin was enabled after startup");
                applyAliasPermissions();
            } else {
                // The wrappers do not exist yet; the first tick runs after startup (or /reload) has created them, the
                // same point CommandAPI picks for its permission fix (CommandAPISpigot#onEnable, scheduleSyncDelayedTask).
                aliasTask = server.getScheduler().runTask(plugin, this::applyAliasPermissions);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            ServerSoftware software = ServerSoftware.detect();
            String message = AdapterLog.installFailed(software.displayName(), ServerVersion.minecraft(),
                    SupportedVersions.describe(software));
            if (permissions != null) {
                permissions.failAndDisable(message, error);
            } else {
                plugin.getLogger().log(Level.SEVERE, message, error);
                scheduler(plugin).runGlobal(() -> server.getPluginManager().disablePlugin(plugin));
            }
        }
    }

    /**
     * Commands other plugins put in the vanilla dispatcher are left unchecked: their number at INFO and the names at
     * FINE, because a plugin such as LuckPerms adds a dozen. A vanilla command among them was changed by another
     * plugin and has no checks at all, so those names are a WARNING.
     */
    static void logSkipped(Logger logger, List<String> skipped) {
        List<String> vanilla = new ArrayList<>();
        List<String> added = new ArrayList<>();
        for (String name : skipped) {
            String bare = name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
            (PluginNodes.VANILLA_COMMANDS.contains(bare) ? vanilla : added).add(name);
        }
        if (!vanilla.isEmpty()) {
            logger.warning("Another plugin changed these vanilla commands, so they have no permission checks from "
                    + "VanillaPermissionsPlugin: " + String.join(", ", vanilla));
        }
        if (!added.isEmpty()) {
            logger.info("Left " + added.size() + " commands that other plugins added to the vanilla command dispatcher unchecked");
            logger.fine("Commands other plugins added: " + String.join(", ", added));
        }
    }

    /** Fail-safe: on any problem, one warning and CraftBukkit's alias permissions stay as they are. */
    private void applyAliasPermissions() {
        aliasTask = null;
        try {
            List<String> changed = aliases.apply(craft.knownCommands(), craft.vanillaCommandWrapperType(),
                    craft.vanillaDispatcher().getRoot(), plugin.getServer().getClass().getClassLoader(),
                    SpigotAdapter.class.getClassLoader());
            if (!changed.isEmpty()) {
                plugin.getLogger().info("Gave " + changed.size() + " vanilla alias commands their own Bukkit permission node "
                        + "(minecraft.command.<alias>, e.g. minecraft.command.tp)");
                plugin.getLogger().fine("Alias commands with their own node: " + String.join(", ", changed));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            aliases.restore();
            plugin.getLogger().warning("Could not give the vanilla alias commands (/tp, /tell, /w, /xp, /tm) their own "
                    + "Bukkit permission node, so CraftBukkit still requires the target command's node for them "
                    + "(minecraft.command.teleport for /tp): " + error);
        }
    }

    @Override
    public void disable() {
        if (aliasTask != null) {
            aliasTask.cancel();
            aliasTask = null;
        }
        aliases.restore();
        if (permissions != null) {
            permissions.uninstall();
            try {
                if (!isStopping(craft.minecraftServer())) {
                    craft.syncCommands();
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                plugin.getLogger().log(Level.WARNING, "Could not re-sync commands after uninstalling permission checks", error);
            }
        }
    }

    /** CraftBukkit sets hasStopped before disabling plugins; older builds only expose isRunning. */
    static boolean isStopping(Object minecraftServer) {
        try {
            try {
                return Boolean.TRUE.equals(minecraftServer.getClass().getMethod("hasStopped").invoke(minecraftServer));
            } catch (NoSuchMethodException absent) {
                return Boolean.FALSE.equals(minecraftServer.getClass().getMethod("isRunning").invoke(minecraftServer));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            // Re-syncing unnecessarily is harmless; leaving the plugin's command copies installed is not.
            return false;
        }
    }

    @Override
    public int checkedNodeCount() {
        return permissions == null ? 0 : permissions.installedNodeCount();
    }

    @Override
    public String inactiveReason(String node) {
        return unavailableBypasses.contains(node) ? "this server's API has no event for it (it needs Paper)" : null;
    }
}
