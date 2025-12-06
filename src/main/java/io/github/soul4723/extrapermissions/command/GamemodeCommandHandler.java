package io.github.soul4723.extrapermissions.command;

import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GamemodeCommandHandler {
    
    public static void handleGamemodeCommand(CommandSender sender, Object[] args) {
        if (args.length == 0 || args[0] == null) {
            sender.sendMessage("§cUsage: /gamemode <survival|creative|adventure|spectator> [player]");
            return;
        }
        
        String gamemode = args[0].toString().toLowerCase();
        
        // Validate gamemode
        try {
            org.bukkit.GameMode.valueOf(gamemode.toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cInvalid gamemode: " + gamemode + ". Valid modes: survival, creative, adventure, spectator");
            return;
        }
        
        // Check if target player is provided
        if (args.length == 1) {
            // Self gamemode change
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cConsole must specify a target player!");
                return;
            }
            
            Player player = (Player) sender;
            org.bukkit.GameMode gm = org.bukkit.GameMode.valueOf(gamemode.toUpperCase());
            player.setGameMode(gm);
            player.sendMessage("§aGamemode set to " + gamemode);
            
        } else if (args.length >= 2) {
            // Target player gamemode change
            if (!(args[1] instanceof Player)) {
                sender.sendMessage("§cInvalid target! Must be a player.");
                return;
            }
            
            Player target = (Player) args[1];
            if (target == null) {
                sender.sendMessage("§cPlayer not found!");
                return;
            }
            
            // Check if sender has permission to change other players' gamemode
            if (!PermissionManager.hasPermission(sender, "minecraft.command.gamemode.other")) {
                sender.sendMessage("§cYou don't have permission to set other players' gamemode!");
                return;
            }
            
            // Check specific gamemode permission for other players
            String specificPermission = "minecraft.command.gamemode." + gamemode + ".other";
            if (!PermissionManager.hasPermission(sender, specificPermission)) {
                sender.sendMessage("§cYou don't have permission to set " + gamemode + " for other players!");
                return;
            }
            
            org.bukkit.GameMode gm = org.bukkit.GameMode.valueOf(gamemode.toUpperCase());
            target.setGameMode(gm);
            sender.sendMessage("§aSet " + target.getName() + "'s gamemode to " + gamemode);
            target.sendMessage("§aYour gamemode was set to " + gamemode + " by " + sender.getName());
        }
    }
}