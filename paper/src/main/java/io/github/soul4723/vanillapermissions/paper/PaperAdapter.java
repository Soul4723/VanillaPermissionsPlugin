package io.github.soul4723.vanillapermissions.paper;

import com.mojang.brigadier.CommandDispatcher;
import io.github.soul4723.vanillapermissions.brigadier.BrigadierFeatures;
import io.github.soul4723.vanillapermissions.brigadier.BrigadierPermissions;
import io.github.soul4723.vanillapermissions.paper.listener.ForceGamemodeBypassListener;
import io.github.soul4723.vanillapermissions.paper.listener.MoveSpeedBypassListener;
import io.github.soul4723.vanillapermissions.platform.AdapterLog;
import io.github.soul4723.vanillapermissions.platform.PluginScheduler;
import io.github.soul4723.vanillapermissions.platform.VersionAdapter;
import io.github.soul4723.vanillapermissions.util.ServerSoftware;
import io.github.soul4723.vanillapermissions.util.SupportedVersions;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

/** Paper 1.20.6+: checks on the native Brigadier tree through the lifecycle COMMANDS event, plus listeners for Paper's API. */
public final class PaperAdapter implements VersionAdapter {

    private BrigadierPermissions permissions;

    @Override
    public String id() {
        return "paper";
    }

    @Override
    public PluginScheduler scheduler(JavaPlugin plugin) {
        return new PluginScheduler() {
            @Override
            public void runLater(Player player, Runnable task, long ticks, Runnable retired) {
                if (player.getScheduler().runDelayed(plugin, scheduled -> task.run(), retired, ticks) == null) {
                    retired.run();
                }
            }

            @Override
            public void runGlobal(Runnable task) {
                Bukkit.getGlobalRegionScheduler().execute(plugin, task);
            }
        };
    }

    @Override
    public void enable(JavaPlugin plugin) {
        BrigadierFeatures.registerListeners(plugin);
        PluginManager plugins = plugin.getServer().getPluginManager();
        plugins.registerEvents(new ForceGamemodeBypassListener(), plugin);
        plugins.registerEvents(new MoveSpeedBypassListener(), plugin);
        BrigadierPermissions installer = new BrigadierPermissions(plugin, new PaperBridge(), scheduler(plugin));
        permissions = installer;
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            if (!plugin.isEnabled()) return;
            try {
                installer.install(getNativeDispatcher(event.registrar().getDispatcher()));
            } catch (ReflectiveOperationException | RuntimeException error) {
                installer.failAndDisable(AdapterLog.INSTALL_FAILED + " to vanilla commands on Minecraft "
                    + Bukkit.getMinecraftVersion() + " (tested: " + SupportedVersions.describe(ServerSoftware.detect()) + "). "
                    + "Vanilla commands were restored and VanillaPermissionsPlugin is disabling itself so no command is left half-checked. "
                    + "Run it on a tested Paper version or remove it.", error);
            }
        });
    }

    @Override
    public void disable() {
        if (permissions != null) permissions.uninstall();
    }

    @Override
    public int checkedNodeCount() {
        return permissions == null ? 0 : permissions.installedNodeCount();
    }

    /** The vanilla dispatcher behind the API mirror that Paper's COMMANDS event hands out. */
    static CommandDispatcher<?> getNativeDispatcher(CommandDispatcher<?> apiDispatcher) throws ReflectiveOperationException {
        Object root = apiDispatcher.getRoot();
        Method getNativeDispatcher = root.getClass().getMethod("getDispatcher");
        if (!getNativeDispatcher.trySetAccessible()) {
            throw new IllegalAccessException("Paper API mirror does not expose its native dispatcher");
        }
        Object value = getNativeDispatcher.invoke(root);
        if (!(value instanceof CommandDispatcher<?> nativeDispatcher)) {
            throw new IllegalStateException("Paper API mirror returned an unexpected dispatcher: " + value);
        }
        return nativeDispatcher;
    }
}
