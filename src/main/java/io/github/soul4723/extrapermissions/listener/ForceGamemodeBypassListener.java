package io.github.soul4723.extrapermissions.listener;

import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ForceGamemodeBypassListener implements Listener {

    private static final String BYPASS_PERMISSION = "minecraft.bypass.force-gamemode";
    private final Set<UUID> recentJoins = new HashSet<>();
    private final Set<UUID> recentRespawns = new HashSet<>();

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (PermissionManager.hasPermissionOrParent(player, BYPASS_PERMISSION)) {
            recentJoins.add(player.getUniqueId());
            Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugin("ExtraPermissions"),
                () -> recentJoins.remove(player.getUniqueId()),
                5L
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (PermissionManager.hasPermissionOrParent(player, BYPASS_PERMISSION)) {
            recentRespawns.add(player.getUniqueId());
            Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugin("ExtraPermissions"),
                () -> recentRespawns.remove(player.getUniqueId()),
                5L
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        
        if (!recentJoins.contains(playerUUID) && !recentRespawns.contains(playerUUID)) {
            return;
        }
        
        // If player has bypass permission and gamemode is being changed shortly after join/respawn,
        // assume this is forced by server and cancel it
        if (PermissionManager.hasPermissionOrParent(player, BYPASS_PERMISSION)) {
            GameMode serverDefaultGamemode = Bukkit.getServer().getDefaultGameMode();
            
            // Only cancel if changing to server's default gamemode (likely force-gamemode)
            if (event.getNewGameMode() == serverDefaultGamemode) {
                event.setCancelled(true);
            }
            
            // Remove from tracking regardless
            recentJoins.remove(playerUUID);
            recentRespawns.remove(playerUUID);
        }
    }
}
