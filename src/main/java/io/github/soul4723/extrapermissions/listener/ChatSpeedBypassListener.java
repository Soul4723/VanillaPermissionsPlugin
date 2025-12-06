package io.github.soul4723.extrapermissions.listener;

import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChatSpeedBypassListener implements Listener {
    
    private final Map<UUID, Long> lastChatTime = new HashMap<>();
    private static final long DEFAULT_CHAT_COOLDOWN = 1000; // 1 second default
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        // Check if player has chat speed bypass permission
        if (PermissionManager.hasPermission(player, "minecraft.bypass.chat-speed")) {
            // Player has bypass - allow chat without cooldown
            return;
        }
        
        // Check for specific bypass permissions
        if (PermissionManager.hasPermission(player, "minecraft.bypass.chat-speed.global")) {
            // Global bypass - no restrictions
            return;
        }
        
        if (PermissionManager.hasPermission(player, "minecraft.bypass.chat-speed.reduced")) {
            // Reduced cooldown - half the normal time
            long reducedCooldown = DEFAULT_CHAT_COOLDOWN / 2;
            UUID playerId = player.getUniqueId();
            long currentTime = System.currentTimeMillis();
            
            if (lastChatTime.containsKey(playerId)) {
                long timeSinceLastChat = currentTime - lastChatTime.get(playerId);
                if (timeSinceLastChat < reducedCooldown) {
                    event.setCancelled(true);
                    player.sendMessage("§cPlease wait before sending another message!");
                    return;
                }
            }
            lastChatTime.put(playerId, currentTime);
            return;
        }
        
        // Apply normal chat cooldown for players without bypass
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        if (lastChatTime.containsKey(playerId)) {
            long timeSinceLastChat = currentTime - lastChatTime.get(playerId);
            if (timeSinceLastChat < DEFAULT_CHAT_COOLDOWN) {
                event.setCancelled(true);
                player.sendMessage("§cPlease wait before sending another message!");
                return;
            }
        }
        
        lastChatTime.put(playerId, currentTime);
    }
}