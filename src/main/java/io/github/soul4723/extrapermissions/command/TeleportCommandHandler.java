package io.github.soul4723.extrapermissions.command;

import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TeleportCommandHandler {
    
    public static void handleTeleportCommand(CommandSender sender, Object[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be run by players!");
            return;
        }
        
        Player player = (Player) sender;
        
        // Handle player teleportation
        if (args.length >= 1 && args[0] instanceof Player) {
            Player target = (Player) args[0];
            if (target == null) {
                player.sendMessage("§cPlayer not found!");
                return;
            }
            
            // Check permission for teleporting to players
            if (!PermissionManager.hasPermission(player, "minecraft.command.teleport.targets")) {
                player.sendMessage("§cYou don't have permission to teleport to players!");
                return;
            }
            
            player.teleport(target);
            player.sendMessage("§aTeleported to " + target.getName());
            return;
        }
        
        // Handle coordinate teleportation
        if (args.length >= 3) {
            // Check permission for teleporting to coordinates
            if (!PermissionManager.hasPermission(player, "minecraft.command.teleport.targets.location")) {
                player.sendMessage("§cYou don't have permission to teleport to coordinates!");
                return;
            }
            
            try {
                double x = ((Number) args[0]).doubleValue();
                double y = ((Number) args[1]).doubleValue();
                double z = ((Number) args[2]).doubleValue();
                
                Location location = new Location(player.getWorld(), x, y, z);
                
                // Handle optional yaw and pitch
                if (args.length >= 5) {
                    float yaw = ((Number) args[3]).floatValue();
                    float pitch = ((Number) args[4]).floatValue();
                    location.setYaw(yaw);
                    location.setPitch(pitch);
                }
                
                player.teleport(location);
                player.sendMessage("§aTeleported to " + formatLocation(location));
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid coordinates! Use numbers for x, y, z values.");
            } catch (Exception e) {
                player.sendMessage("§cError during teleportation: " + e.getMessage());
            }
        } else {
            player.sendMessage("§cUsage: /tp <player> or /tpcoords <x> <y> <z> [yaw] [pitch]");
        }
    }
    
    private static String formatLocation(Location loc) {
        return String.format("%.2f, %.2f, %.2f", loc.getX(), loc.getY(), loc.getZ());
    }
}