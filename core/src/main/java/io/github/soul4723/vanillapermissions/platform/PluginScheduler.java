package io.github.soul4723.vanillapermissions.platform;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Scheduling needed by core, supplied by the selected server adapter. */
public interface PluginScheduler {

    /** Runs on the player's owning thread, unless the player has left by then. */
    void runLater(Player player, Runnable task, long ticks);

    /** Runs later on the server's global thread. */
    void runGlobal(Runnable task);

    static PluginScheduler bukkit(Plugin plugin) {
        return new PluginScheduler() {
            @Override
            public void runLater(Player player, Runnable task, long ticks) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) task.run();
                }, ticks);
            }

            @Override
            public void runGlobal(Runnable task) {
                plugin.getServer().getScheduler().runTask(plugin, task);
            }
        };
    }
}
