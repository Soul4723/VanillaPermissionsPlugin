package io.github.soul4723.extrapermissions.listener;

import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatSpeedBypassListener implements Listener {
    
    private final Map<UUID, Long> lastChatTime = new ConcurrentHashMap<>();
    private static final long DEFAULT_CHAT_COOLDOWN = 1000; // 1 second default
    private static final long CLEANUP_INTERVAL = 300000; // 5 minutes
    private static final long MAX_AGE = 600000; // 10 minutes
    private long lastCleanup = System.currentTimeMillis();
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        
        // Periodic cleanup to prevent memory leaks
        cleanupOldEntries();

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
    
    private void cleanupOldEntries() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCleanup < CLEANUP_INTERVAL) {
            return; // Don't cleanup too frequently
        }
        
        lastCleanup = currentTime;
        lastChatTime.entrySet().removeIf(entry -> currentTime - entry.getValue() > MAX_AGE);
    }
}