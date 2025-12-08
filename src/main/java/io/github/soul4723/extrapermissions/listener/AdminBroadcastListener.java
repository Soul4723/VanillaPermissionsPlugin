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
    private static final long RATE_LIMIT = 1000;
    private static final long CLEANUP_INTERVAL = 300000;
    private static final long MAX_AGE = 600000;
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
            if (online != player && PermissionManager.hasPermissionOrParent(online, "minecraft.adminbroadcast.receive")) {
                online.sendMessage("§7" + player.getName() + " ran: " + event.getMessage());
            }
        }
    }
    
    private boolean isSensitiveCommand(String command) {
        String[] parts = command.split(" ", 2);
        String baseCommand = parts[0];
        
        if (baseCommand.equals("/login") || baseCommand.equals("/register") || 
            baseCommand.equals("/password") || baseCommand.equals("/email") || 
            baseCommand.equals("/2fa")) {
            return true;
        }
        
        if (parts.length > 1) {
            String args = parts[1].toLowerCase();
            return args.contains(" password ") || args.contains(" secret ") || 
                   args.startsWith("password ") || args.startsWith("secret ") ||
                   args.endsWith(" password") || args.endsWith(" secret");
        }
        
        return false;
    }
    
    private void cleanupOldEntries() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCleanup < CLEANUP_INTERVAL) return;
        
        lastCleanup = currentTime;
        recentCommands.entrySet().removeIf(entry -> currentTime - entry.getValue() > MAX_AGE);
    }
}