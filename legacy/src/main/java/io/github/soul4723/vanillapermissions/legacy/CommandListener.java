package io.github.soul4723.vanillapermissions.legacy;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Checks what a player typed before CraftBukkit dispatches it. Console, command blocks and commands other plugins
 * dispatch never fire this event, so they stay unchecked, as the modern adapters leave console and command blocks.
 */
final class CommandListener implements Listener {

    private final CommandGuard guard;
    private final Logger logger;
    private final AtomicBoolean failureLogged = new AtomicBoolean();

    CommandListener(CommandGuard guard, Logger logger) {
        this.guard = guard;
        this.logger = logger;
    }

    /**
     * HIGHEST, so the line other plugins may have rewritten is the one checked. This listener is the only check on
     * these versions, so a check that fails refuses the command: Bukkit would log the exception and run it unchecked.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (!message.startsWith("/")) return;
        String denial;
        try {
            denial = guard.check(event.getPlayer(), message.substring(1));
        } catch (RuntimeException | LinkageError error) {
            if (failureLogged.compareAndSet(false, true)) {
                logger.log(Level.WARNING, "Could not check " + message + ", so it was refused. Further failures are not logged.", error);
            } else {
                logger.log(Level.FINE, "Could not check " + message + ", so it was refused", error);
            }
            denial = CommandGuard.NO_PERMISSION;
        }
        if (denial != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + denial);
        }
    }
}
