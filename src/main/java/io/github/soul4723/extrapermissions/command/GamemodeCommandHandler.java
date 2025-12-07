package io.github.soul4723.extrapermissions.command;

import io.github.soul4723.extrapermissions.util.PermissionManager;
import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GamemodeCommandHandler {
    
    public static void handleGamemodeCommand(CommandSender sender, CommandArguments args) {
        Object gmObj = args.getUnchecked("gamemode");
        if (gmObj == null) {
            sender.sendMessage("§cUsage: /gamemode <survival|creative|adventure|spectator> [player]");
            return;
        }
        
        String gamemode = gmObj.toString().toLowerCase();
        try {
            org.bukkit.GameMode.valueOf(gamemode.toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cInvalid gamemode: " + gamemode + ". Valid modes: survival, creative, adventure, spectator");
            return;
        }
        
        Player target = null;
        Object rawTarget = args.getUnchecked("target");
        if (rawTarget instanceof Player) {
            target = (Player) rawTarget;
        }
        
        if (target == null) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cConsole must specify a target player!");
                return;
            }
            Player player = (Player) sender;
            
            String gamemodePermission = "minecraft.command.gamemode." + gamemode;
            if (!PermissionManager.hasPermission(player, gamemodePermission)) {
                player.sendMessage("§cYou don't have permission to use gamemode " + gamemode + "!");
                return;
            }
            
            org.bukkit.GameMode gm = org.bukkit.GameMode.valueOf(gamemode.toUpperCase());
            player.setGameMode(gm);
            player.sendMessage("§aGamemode set to " + gamemode);
        } else {
            if (!PermissionManager.hasPermission(sender, "minecraft.command.gamemode.other")) {
                sender.sendMessage("§cYou don't have permission to set other players' gamemode!");
                return;
            }
            
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