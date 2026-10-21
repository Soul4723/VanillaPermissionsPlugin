package io.github.soul4723.vanillapermissions.legacy.feature;

import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * The non-command features on 1.8.8 - 1.12.2: the force-gamemode bypass, operator blocks and the
 * move-speed bypass. The whitelist and player-limit bypasses need nothing here: core's {@code WhitelistBypassListener}
 * works unchanged on these versions. Debug stick, jigsaw and the {@code /execute} additions do not exist before 1.13.
 */
public final class LegacyFeatures {

    private final List<Listener> listeners = new ArrayList<>();
    private MoveSpeedBypass moveSpeed;

    private LegacyFeatures() {}

    /** Registers the feature listeners. A feature that cannot be installed logs one warning and is skipped. */
    public static LegacyFeatures enable(JavaPlugin plugin) {
        LegacyFeatures features = new LegacyFeatures();
        features.register(plugin, new OperatorBlockListener());
        try {
            features.register(plugin, ForceGamemodeBypassListener.create(plugin.getServer(), plugin.getLogger()));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            skipped(plugin, "force-gamemode bypass (" + ForceGamemodeBypassListener.BYPASS_PERMISSION + ")", e);
        }
        try {
            features.moveSpeed = MoveSpeedBypass.install(plugin);
            features.register(plugin, features.moveSpeed);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            skipped(plugin, "move-speed bypass (" + MoveSpeedBypass.PERMISSION + ")", e);
        }
        return features;
    }

    /** Unregisters the listeners and removes any network hooks; safe to call more than once. */
    public void disable() {
        for (Listener listener : listeners) HandlerList.unregisterAll(listener);
        listeners.clear();
        if (moveSpeed != null) {
            moveSpeed.uninstall();
            moveSpeed = null;
        }
    }

    private void register(JavaPlugin plugin, Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listeners.add(listener);
    }

    private static void skipped(JavaPlugin plugin, String feature, Throwable cause) {
        plugin.getLogger().warning("The " + feature + " is off: this server (" + plugin.getServer().getVersion() + ") does not have the internals it needs: " + cause);
        plugin.getLogger().log(Level.FINE, "Feature install failure", cause);
    }
}
