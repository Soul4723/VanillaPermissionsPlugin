package io.github.soul4723.extrapermissions.listener;

import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SelectorPermissionListener implements Listener {
    
    private static final Pattern SELECTOR_PATTERN = Pattern.compile("@[aepsr](?:\\\\[[^\\\\]]*\\\\])*");
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage();
        checkSelectorScopes(event, player, command);
    }
    
    private void checkSelectorScopes(PlayerCommandPreprocessEvent event, Player player, String command) {
        Matcher matcher = SELECTOR_PATTERN.matcher(command);
        
        while (matcher.find()) {
            String selector = matcher.group();
            String baseSelector = selector.substring(0, 2);
            String scope = getSelectorScope(baseSelector);
            
            String permission = "minecraft.selector." + scope + "." + baseSelector.substring(1);
            
            if (!PermissionManager.hasPermissionOrParent(player, permission)) {
                event.setCancelled(true);
                player.sendMessage("§cYou don't have permission to use " + selector + " selector!");
                return;
            }
        }
    }
    
    private String getSelectorScope(String selector) {
        if (selector.length() < 2) return "unknown";
        return switch (selector.substring(0, 2)) {
            case "@s" -> "self";
            case "@a", "@p", "@r" -> "player";
            case "@e" -> "entity";
            default -> "unknown";
        };
    }
}