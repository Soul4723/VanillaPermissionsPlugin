package io.github.soul4723.vanillapermissions.util;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.context.ContextUpdateEvent;
import net.luckperms.api.event.group.GroupDataRecalculateEvent;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LuckPermsHook implements AutoCloseable {
    /** Lets LuckPerms finish propagating a change (group edits reach users after storage saves) and batches bursts. */
    static final long REFRESH_DELAY_TICKS = 10;

    private final Set<UUID> pendingRefreshes = ConcurrentHashMap.newKeySet();
    private final List<EventSubscription<?>> subscriptions = new ArrayList<>();
    private LuckPerms luckPerms;
    private volatile boolean enabled = false;

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
            subscribeRefreshes(plugin, luckPerms.getEventBus());
            this.enabled = true;
        } catch (RuntimeException e) {
            close();
            plugin.getLogger().severe("Could not connect to the LuckPerms API: " + e.getMessage());
        }
    }

    /** Clients cache the command tree, so resend it whenever LuckPerms changes what a player may run. */
    private synchronized void subscribeRefreshes(Plugin plugin, EventBus eventBus) {
        subscriptions.add(eventBus.subscribe(plugin, UserDataRecalculateEvent.class,
            event -> refreshCommands(plugin, event.getUser().getUniqueId())));
        subscriptions.add(eventBus.subscribe(plugin, GroupDataRecalculateEvent.class, event -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) refreshCommands(plugin, player.getUniqueId());
        }));
        subscriptions.add(eventBus.subscribe(plugin, ContextUpdateEvent.class,
            event -> event.getSubject(Player.class).ifPresent(player -> refreshCommands(plugin, player.getUniqueId()))));
    }

    private void refreshCommands(Plugin plugin, UUID playerId) {
        if (!isEnabled() || !plugin.isEnabled() || !pendingRefreshes.add(playerId)) return;
        try {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                pendingRefreshes.remove(playerId);
                if (!isEnabled() || !plugin.isEnabled()) return;
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null) player.updateCommands();
            }, REFRESH_DELAY_TICKS);
        } catch (IllegalPluginAccessException e) {
            // The plugin was disabled between the check above and scheduling.
            pendingRefreshes.remove(playerId);
        }
    }

    @Override
    public synchronized void close() {
        enabled = false;
        subscriptions.forEach(EventSubscription::close);
        subscriptions.clear();
        pendingRefreshes.clear();
    }

    public boolean isEnabled() {
        return enabled && luckPerms != null;
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }
}
