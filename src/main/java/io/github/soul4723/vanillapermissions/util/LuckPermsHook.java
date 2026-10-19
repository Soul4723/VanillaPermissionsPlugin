package io.github.soul4723.vanillapermissions.util;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LuckPermsHook implements AutoCloseable {
    private final Set<UUID> pendingRefreshes = ConcurrentHashMap.newKeySet();
    private LuckPerms luckPerms;
    private volatile boolean enabled = false;
    private EventSubscription<UserDataRecalculateEvent> recalculationSubscription;

    public void initialize(Plugin plugin) {
        Plugin luckPermsPlugin = plugin.getServer().getPluginManager().getPlugin("LuckPerms");
        if (luckPermsPlugin == null) {
            plugin.getLogger().severe("LuckPerms is not installed; VanillaPermissionsPlugin requires LuckPerms 5.5 or newer");
            return;
        }
        try {
            RegisteredServiceProvider<LuckPerms> provider =
                plugin.getServer().getServicesManager().getRegistration(LuckPerms.class);
            if (provider == null) {
                plugin.getLogger().severe("LuckPerms is installed but has not registered its API; check the LuckPerms startup log");
                return;
            }
            this.luckPerms = provider.getProvider();
            // Clients cache the command tree, so resend it whenever LuckPerms changes a player's permissions.
            recalculationSubscription = luckPerms.getEventBus().subscribe(
                plugin, UserDataRecalculateEvent.class, event -> refreshCommands(plugin, event.getUser().getUniqueId()));
            this.enabled = true;
        } catch (RuntimeException e) {
            close();
            plugin.getLogger().severe("Could not connect to the LuckPerms API: " + e.getMessage());
        }
    }

    private void refreshCommands(Plugin plugin, UUID playerId) {
        if (!isEnabled() || !plugin.isEnabled() || !pendingRefreshes.add(playerId)) return;
        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                pendingRefreshes.remove(playerId);
                if (!isEnabled() || !plugin.isEnabled()) return;
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null) player.updateCommands();
            });
        } catch (IllegalPluginAccessException e) {
            // The plugin was disabled between the check above and scheduling.
            pendingRefreshes.remove(playerId);
        }
    }

    @Override
    public synchronized void close() {
        enabled = false;
        if (recalculationSubscription != null) {
            recalculationSubscription.close();
            recalculationSubscription = null;
        }
        pendingRefreshes.clear();
    }

    public boolean isEnabled() {
        return enabled && luckPerms != null;
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }
}
