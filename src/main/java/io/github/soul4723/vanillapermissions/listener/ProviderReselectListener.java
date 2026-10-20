package io.github.soul4723.vanillapermissions.listener;

import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;

/**
 * Picks the permissions source again once every plugin has enabled, because {@code onEnable} cannot see a permissions
 * plugin that loads after this one. The early pick stays, since startup code needs a provider straight away.
 */
public final class ProviderReselectListener implements Listener {

    private final Plugin plugin;

    public ProviderReselectListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        reselectOn(event.getType());
    }

    /** Split from the handler so tests need no server to build an event. */
    public void reselectOn(ServerLoadEvent.LoadType type) {
        // RELOAD is ignored on purpose: Paper discourages /reload, and this adds no reload handling beyond startup.
        if (type != ServerLoadEvent.LoadType.STARTUP) return;
        PermissionManager.reselectProvider(plugin);
    }
}
