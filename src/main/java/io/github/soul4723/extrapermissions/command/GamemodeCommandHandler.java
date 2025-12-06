package io.github.soul4723.extrapermissions.command;

import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GamemodeCommandHandler {
    
    public static void handleGamemodeCommand(CommandSender sender, Object[] args) {
        if (!PermissionManager.hasPermission(sender, "minecraft.command.gamemode")) {
            sender.sendMessage("§cYou don't have permission to use /gamemode!");
            return;
        }
        
        String gamemode = args[0].toString().toLowerCase();
        String permission = "minecraft.command.gamemode." + gamemode;
        
        // Check if target player is provided (args[1] exists)
        if (args.length == 1) {
            // Self gamemode change
            if (!PermissionManager.hasPermission(sender, permission)) {
                sender.sendMessage("§cYou don't have permission to set gamemode to " + gamemode + "!");
                return;
            }
            
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cThis command can only be run by players!");
                return;
            }
            
            Player player = (Player) sender;
            try {
                org.bukkit.GameMode gm = org.bukkit.GameMode.valueOf(gamemode.toUpperCase());
                player.setGameMode(gm);
                player.sendMessage("§aGamemode set to " + gamemode);
            } catch (IllegalArgumentException e) {
                sender.sendMessage("§cInvalid gamemode: " + gamemode);
            }
        } else if (args.length == 2) {
            // Target player gamemode change
            if (!PermissionManager.hasPermission(sender, "minecraft.command.gamemode.other")) {
                sender.sendMessage("§cYou don't have permission to set other players' gamemode!");
                return;
            }
            
            Player target = (Player) args[1];
            if (target == null) {
                sender.sendMessage("§cPlayer not found!");
                return;
            }
            
            if (!PermissionManager.hasPermission(sender, permission + ".other")) {
                sender.sendMessage("§cYou don't have permission to set " + gamemode + " for other players!");
                return;
            }
            
            try {
                org.bukkit.GameMode gm = org.bukkit.GameMode.valueOf(gamemode.toUpperCase());
                target.setGameMode(gm);
                sender.sendMessage("§aSet " + target.getName() + "'s gamemode to " + gamemode);
                target.sendMessage("§aYour gamemode was set to " + gamemode + " by " + sender.getName());
            } catch (IllegalArgumentException e) {
                sender.sendMessage("§cInvalid gamemode: " + gamemode);
            }
        }
    }
}