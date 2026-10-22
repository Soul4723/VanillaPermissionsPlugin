package io.github.soul4723.vanillapermissions.platform;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * One server-version group's way of adding permission checks to vanilla commands and the listeners that need that
 * group's API. {@link Adapters#select} picks the adapter for the running server; the rest of the plugin is shared.
 */
public interface VersionAdapter {

    /** Short name for logs, e.g. {@code paper}. */
    String id();

    /** Scheduling for this server's player and global threads. */
    default PluginScheduler scheduler(JavaPlugin plugin) {
        return PluginScheduler.bukkit(plugin);
    }

    /** Installs the command checks and registers this group's listeners. */
    void enable(JavaPlugin plugin) throws Exception;

    /** Restores vanilla commands; safe to call if {@link #enable} failed or never ran. */
    void disable();

    /** Number of vanilla command nodes currently carrying a permission check, for {@code /vanillapermissions debug}. */
    int checkedNodeCount();

    /**
     * Why setting {@code node} (lower case) does nothing on this server, for {@code /vanillapermissions check}, e.g. a
     * sub-command node the 1.8.8 - 1.12.2 checks do not know; {@code null} if it can take effect.
     */
    default String inactiveReason(String node) {
        return null;
    }
}
