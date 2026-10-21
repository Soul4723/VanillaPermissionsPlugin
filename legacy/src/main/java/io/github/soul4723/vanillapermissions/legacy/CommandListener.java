package io.github.soul4723.vanillapermissions.legacy;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Checks what a player typed before CraftBukkit dispatches it. Console, command blocks and commands other plugins
 * dispatch never fire this event, so they stay unchecked, as the modern adapters leave console and command blocks.
 */
final class CommandListener implements Listener {

    private final CommandGuard guard;

    CommandListener(CommandGuard guard) {
        this.guard = guard;
    }

    /** HIGHEST, so the line other plugins may have rewritten is the one checked. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (!message.startsWith("/")) return;
        String denial = guard.check(event.getPlayer(), message.substring(1));
        if (denial != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + denial);
        }
    }
}
