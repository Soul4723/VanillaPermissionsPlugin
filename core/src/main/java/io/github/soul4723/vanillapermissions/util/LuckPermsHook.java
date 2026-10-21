package io.github.soul4723.vanillapermissions.util;

import net.luckperms.api.LuckPerms;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * The LuckPerms API, once LuckPerms is enabled. It does not resend command trees after permission changes: LuckPerms
 * already does that for every player whose data changed ({@code update-client-command-list}, on by default;
 * {@code AbstractCommandListUpdater} with {@code BukkitCommandListUpdater}, buffered 500 ms and limited to users who
 * inherit a changed group), and a second resend only cost the server another tree build per player.
 */
public class LuckPermsHook implements AutoCloseable {
    private LuckPerms luckPerms;
    private volatile boolean enabled = false;

    public void initialize(Plugin plugin) {
        Plugin luckPermsPlugin = plugin.getServer().getPluginManager().getPlugin("LuckPerms");
        if (luckPermsPlugin == null) {
            plugin.getLogger().severe("Could not find LuckPerms");
            return;
        }
        // A LuckPerms that failed to start no longer provides its API classes, so do not touch them.
        if (!luckPermsPlugin.isEnabled()) {
            plugin.getLogger().severe("Could not use LuckPerms because it is not enabled; fix the LuckPerms errors earlier in the log");
            return;
        }
        try {
            RegisteredServiceProvider<LuckPerms> provider =
                plugin.getServer().getServicesManager().getRegistration(LuckPerms.class);
            if (provider == null) {
                plugin.getLogger().severe("Could not use LuckPerms because it has not registered its API; check the LuckPerms startup log");
                return;
            }
            this.luckPerms = provider.getProvider();
            this.enabled = true;
        } catch (RuntimeException e) {
            close();
            plugin.getLogger().severe("Could not connect to the LuckPerms API: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        enabled = false;
    }

    public boolean isEnabled() {
        return enabled && luckPerms != null;
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }
}
