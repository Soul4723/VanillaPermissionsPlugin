package io.github.soul4723.vanillapermissions.legacy;

import io.github.soul4723.vanillapermissions.legacy.feature.LegacyFeatures;
import io.github.soul4723.vanillapermissions.legacy.grammar.Grammars;
import io.github.soul4723.vanillapermissions.legacy.grammar.LegacyVersion;
import io.github.soul4723.vanillapermissions.platform.AdapterLog;
import io.github.soul4723.vanillapermissions.platform.ServerVersion;
import io.github.soul4723.vanillapermissions.platform.VersionAdapter;
import io.github.soul4723.vanillapermissions.selector.SelectorRules;
import io.github.soul4723.vanillapermissions.util.PermissionManager;
import io.github.soul4723.vanillapermissions.util.ServerSoftware;
import io.github.soul4723.vanillapermissions.util.SupportedVersions;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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
    /** The sub-command paths the grammars check, without {@code minecraft.command.}; empty until installed. */
    private volatile Set<String> checkedPaths = Collections.emptySet();

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
            ServerSoftware software = ServerSoftware.detect();
            plugin.getLogger().log(Level.SEVERE, AdapterLog.installFailed(software.displayName(), ServerVersion.minecraft(),
                SupportedVersions.describe(software)), error);
            disable();
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getServer().getPluginManager().disablePlugin(plugin));
        }
    }

    private void install(JavaPlugin plugin, LegacyVersion version) throws ReflectiveOperationException {
        LegacyServer server = new LegacyServer(plugin.getServer(), plugin.getLogger());
        List<String> vanilla = server.vanillaCommandNames();
        if (vanilla.isEmpty()) throw new IllegalStateException("CraftBukkit registered no VanillaCommandWrapper commands");
        CommandGuard guard = new CommandGuard(server, SelectorRules.PERMISSION_MANAGER);

        CommandListener commands = new CommandListener(guard, plugin.getLogger());
        plugin.getServer().getPluginManager().registerEvents(commands, plugin);
        listeners.add(commands);
        TabCompleteFilter completions = TabCompleteFilter.register(plugin, guard);
        if (completions != null) listeners.add(completions);

        checkedPaths = Collections.unmodifiableSet(Grammars.nodePaths(vanilla, version));
        checkedNodes = checkedPaths.size();
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
        checkedPaths = Collections.emptySet();
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

    @Override
    public String inactiveReason(String node) {
        if (node.startsWith("minecraft.debug_stick.use.")) return "there is no debug stick before 1.13";
        if (node.startsWith("minecraft.operator_block.jigsaw.")) return "there is no jigsaw block before 1.14";
        String prefix = "minecraft.command.";
        if (!node.startsWith(prefix)) return null;
        String path = node.substring(prefix.length());
        // Roots are CraftBukkit's own nodes; sub-commands only count where a grammar checks them.
        if (path.indexOf('.') < 0 || path.endsWith("*") || checkedPaths.isEmpty() || checkedPaths.contains(path)) return null;
        return "legacy mode checks sub-commands of " + Grammars.count() + " commands only";
    }
}
