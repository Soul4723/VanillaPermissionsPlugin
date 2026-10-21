package io.github.soul4723.vanillapermissions.legacy;

import io.github.soul4723.vanillapermissions.legacy.feature.LegacyFeatures;
import io.github.soul4723.vanillapermissions.legacy.grammar.Grammars;
import io.github.soul4723.vanillapermissions.legacy.grammar.LegacyVersion;
import io.github.soul4723.vanillapermissions.platform.AdapterLog;
import io.github.soul4723.vanillapermissions.platform.ServerVersion;
import io.github.soul4723.vanillapermissions.platform.VersionAdapter;
import io.github.soul4723.vanillapermissions.selector.SelectorRules;
import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Spigot and Paper 1.8.8 - 1.12.2, before Brigadier. Nothing is patched: a
 * {@code PlayerCommandPreprocessEvent} listener checks lines whose label resolves to CraftBukkit's
 * {@code VanillaCommandWrapper}, using hand-written grammars that produce the modern node names.
 *
 * <p>CraftBukkit registers the vanilla wrappers after the plugins that load at {@code POSTWORLD} are enabled, so the
 * checks are installed on the first server tick. That tick also stands in for {@code ServerLoadEvent} (1.13.1+),
 * which the other adapters use to pick the permission provider again.
 */
public final class LegacyAdapter implements VersionAdapter {

    private final List<Listener> listeners = new ArrayList<>();
    private LegacyFeatures features;
    private volatile int checkedNodes;

    @Override
    public String id() {
        return "legacy";
    }

    @Override
    public void enable(JavaPlugin plugin) {
        LegacyVersion version = LegacyVersion.parse(ServerVersion.minecraft());
        features = LegacyFeatures.enable(plugin);
        plugin.getServer().getScheduler().runTask(plugin, () -> firstTick(plugin, version));
    }

    private void firstTick(JavaPlugin plugin, LegacyVersion version) {
        if (!plugin.isEnabled()) return;
        try {
            PermissionManager.reselectProvider(plugin);
        } catch (RuntimeException | LinkageError error) {
            plugin.getLogger().log(Level.WARNING, "Could not pick the permission provider again after start-up", error);
        }
        try {
            install(plugin, version);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            plugin.getLogger().log(Level.SEVERE, AdapterLog.INSTALL_FAILED + " to vanilla commands on Minecraft " + version
                + " (" + plugin.getServer().getVersion() + "). Vanilla commands were left unchanged and VanillaPermissionsPlugin "
                + "is disabling itself so no command is left half-checked. Run it on a tested version or remove it.", error);
            disable();
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getServer().getPluginManager().disablePlugin(plugin));
        }
    }

    private void install(JavaPlugin plugin, LegacyVersion version) throws ReflectiveOperationException {
        LegacyServer server = new LegacyServer(plugin.getServer(), plugin.getLogger());
        List<String> vanilla = server.vanillaCommandNames();
        if (vanilla.isEmpty()) throw new IllegalStateException("CraftBukkit registered no VanillaCommandWrapper commands");
        CommandGuard guard = new CommandGuard(server, SelectorRules.PERMISSION_MANAGER, version);

        CommandListener commands = new CommandListener(guard, plugin.getLogger());
        plugin.getServer().getPluginManager().registerEvents(commands, plugin);
        listeners.add(commands);
        TabCompleteFilter completions = TabCompleteFilter.register(plugin, guard);
        if (completions != null) listeners.add(completions);

        checkedNodes = Grammars.nodePaths(vanilla, version).size();
        plugin.getLogger().info(AdapterLog.added(checkedNodes));
        if (completions == null) {
            plugin.getLogger().info("Tab completion is not filtered on Minecraft " + version
                + " (no TabCompleteEvent before 1.9); denied sub-commands are still refused when run");
        }
    }

    @Override
    public void disable() {
        for (Listener listener : listeners) HandlerList.unregisterAll(listener);
        listeners.clear();
        checkedNodes = 0;
        if (features != null) {
            features.disable();
            features = null;
        }
    }

    /**
     * The distinct {@code minecraft.command.} nodes the installed grammars can check on this server: every path
     * (roots and prefixes included) of each grammar whose command CraftBukkit registered, plus the modern roots
     * checked for {@code tell} and {@code xp}. The other vanilla commands keep CraftBukkit's root check only and are
     * not counted.
     */
    @Override
    public int checkedNodeCount() {
        return checkedNodes;
    }
}
