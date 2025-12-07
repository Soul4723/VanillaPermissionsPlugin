package io.github.soul4723.extrapermissions.listener;

import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AdminBroadcastListener implements Listener {
    
    private final Map<UUID, Long> recentCommands = new ConcurrentHashMap<>();
    private static final long RATE_LIMIT = 1000; // 1 second between broadcasts per player
    private static final long CLEANUP_INTERVAL = 300000; // 5 minutes
    private static final long MAX_AGE = 600000; // 10 minutes
    private long lastCleanup = System.currentTimeMillis();
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (event.isCancelled()) return;
        
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        cleanupOldEntries();
        
        Long lastCommandTime = recentCommands.get(playerId);
        if (lastCommandTime != null && (currentTime - lastCommandTime) < RATE_LIMIT) return;
        
        recentCommands.put(playerId, currentTime);
        
        String command = event.getMessage().toLowerCase();
        if (isSensitiveCommand(command)) return;
        
        for (Player online : player.getServer().getOnlinePlayers()) {
            if (online != player && PermissionManager.hasPermission(online, "minecraft.adminbroadcast.receive")) {
                online.sendMessage("§7" + player.getName() + " ran: " + event.getMessage());
            }
        }
    }
    
    private boolean isSensitiveCommand(String command) {
        return command.startsWith("/login") || 
               command.startsWith("/register") || 
               command.startsWith("/password") ||
               command.startsWith("/email") ||
               command.startsWith("/2fa") ||
               command.contains("password") ||
               command.contains("secret");
    }
    
    private void cleanupOldEntries() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCleanup < CLEANUP_INTERVAL) return;
        
        lastCleanup = currentTime;
        recentCommands.entrySet().removeIf(entry -> currentTime - entry.getValue() > MAX_AGE);
    }
}