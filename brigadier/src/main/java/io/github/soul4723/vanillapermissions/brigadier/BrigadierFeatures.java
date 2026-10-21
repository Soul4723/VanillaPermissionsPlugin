package io.github.soul4723.vanillapermissions.brigadier;

import io.github.soul4723.vanillapermissions.brigadier.listener.DebugStickListener;
import io.github.soul4723.vanillapermissions.brigadier.listener.OperatorBlockListener;
import io.github.soul4723.vanillapermissions.brigadier.listener.ProviderReselectListener;
import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/** Features every 1.13+ adapter shares that only need the Bukkit 1.13 API, not the command tree. */
public final class BrigadierFeatures {

    private BrigadierFeatures() {}

    /**
     * Registers the 1.13+ listeners: provider reselection on {@code ServerLoadEvent}, the debug stick and the
     * operator blocks. Adapters register their own server-specific listeners after these.
     */
    public static void registerListeners(JavaPlugin plugin) {
        PluginManager plugins = plugin.getServer().getPluginManager();
        registerProviderReselect(plugin, plugin.getClass().getClassLoader());
        plugins.registerEvents(new DebugStickListener(), plugin);
        plugins.registerEvents(new OperatorBlockListener(), plugin);
    }

    static void registerProviderReselect(Plugin plugin, ClassLoader loader) {
        try {
            Class.forName("org.bukkit.event.server.ServerLoadEvent", false, loader);
            plugin.getServer().getPluginManager().registerEvents(new ProviderReselectListener(plugin), plugin);
        } catch (ClassNotFoundException | LinkageError absent) {
            plugin.getServer().getScheduler().runTask(plugin, () -> PermissionManager.reselectProvider(plugin));
        }
    }
}
