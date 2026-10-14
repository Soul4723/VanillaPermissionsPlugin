package io.github.soul4723.vanillapermissions.listener;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.UserManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LuckPermsPreLoginListener implements Listener {

    private final UserManager userManager;
    private final Logger logger;

    public LuckPermsPreLoginListener(LuckPerms luckPerms, Logger logger) {
        this.userManager = luckPerms.getUserManager();
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;

        try {
            preloadIfNeeded(userManager, event.getUniqueId(), event.getName());
        } catch (CompletionException | CancellationException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            logger.log(Level.WARNING,
                "LuckPerms could not load permissions before login; bypass permissions may not apply",
                cause);
        }
    }

    static void preloadIfNeeded(UserManager userManager, UUID uniqueId, String username) {
        if (!userManager.isLoaded(uniqueId)) {
            userManager.loadUser(uniqueId, username).join();
        }
    }
}
