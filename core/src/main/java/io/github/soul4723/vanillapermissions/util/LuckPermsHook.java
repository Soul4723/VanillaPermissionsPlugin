package io.github.soul4723.vanillapermissions.util;

import io.github.soul4723.vanillapermissions.platform.PluginScheduler;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.context.ContextUpdateEvent;
import net.luckperms.api.event.group.GroupDataRecalculateEvent;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LuckPermsHook implements AutoCloseable {
    /** Lets LuckPerms finish propagating a change (group edits reach users after storage saves) and batches bursts. */
    static final long REFRESH_DELAY_TICKS = 10;
    /** {@code Player#updateCommands}, added in 1.13; looked up at run time because core compiles against the 1.8.8 API. */
    private static final MethodHandle UPDATE_COMMANDS = updateCommandsHandle();

    private final Set<UUID> pendingRefreshes = ConcurrentHashMap.newKeySet();
    private final List<EventSubscription<?>> subscriptions = new ArrayList<>();
    private LuckPerms luckPerms;
    private PluginScheduler scheduler;
    private volatile boolean enabled = false;

    public void initialize(Plugin plugin, PluginScheduler scheduler) {
        this.scheduler = scheduler;
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
            if (!isEnabled() || !plugin.isEnabled()) return;
            try {
                scheduler.runGlobal(() -> {
                    if (!isEnabled() || !plugin.isEnabled()) return;
                    for (Player player : new ArrayList<>(plugin.getServer().getOnlinePlayers())) {
                        refreshCommands(plugin, player.getUniqueId());
                    }
                });
            } catch (RuntimeException ignored) {
                // Disabled between the check and scheduling.
            }
        }));
        subscriptions.add(eventBus.subscribe(plugin, ContextUpdateEvent.class,
            event -> event.getSubject(Player.class).ifPresent(player -> refreshCommands(plugin, player.getUniqueId()))));
    }

    private void refreshCommands(Plugin plugin, UUID playerId) {
        if (!isEnabled() || !plugin.isEnabled() || !pendingRefreshes.add(playerId)) return;
        try {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) {
                pendingRefreshes.remove(playerId);
                return;
            }
            scheduler.runLater(player, () -> {
                pendingRefreshes.remove(playerId);
                if (isEnabled() && plugin.isEnabled()) updateCommands(player);
            }, REFRESH_DELAY_TICKS, () -> pendingRefreshes.remove(playerId));
        } catch (RuntimeException e) {
            // Scheduling can race plugin disable or entity retirement.
            pendingRefreshes.remove(playerId);
        }
    }

    private static MethodHandle updateCommandsHandle() {
        try {
            return MethodHandles.publicLookup().findVirtual(Player.class, "updateCommands", MethodType.methodType(void.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            return null;
        }
    }

    /** Resends the command tree; does nothing on servers without {@code Player#updateCommands}, whose clients do not cache it. */
    private static void updateCommands(Player player) {
        if (UPDATE_COMMANDS == null) return;
        try {
            UPDATE_COMMANDS.invokeExact(player);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized void close() {
        enabled = false;
        for (EventSubscription<?> subscription : subscriptions) subscription.close();
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
