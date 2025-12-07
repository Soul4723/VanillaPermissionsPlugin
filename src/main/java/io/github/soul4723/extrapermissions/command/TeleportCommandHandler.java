package io.github.soul4723.extrapermissions.command;

import dev.jorel.commandapi.executors.CommandArguments;
import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class TeleportCommandHandler {

    public static void handleTeleportCommand(CommandSender sender, CommandArguments args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be run by players!");
            return;
        }
        Player player = (Player) sender;
        
        Object rawTarget = args.getUnchecked("target");
        if (rawTarget instanceof Entity) {
            Entity target = (Entity) rawTarget;
            if (!PermissionManager.hasPermission(player, "minecraft.command.teleport.targets")) {
                player.sendMessage("§cYou don't have permission to teleport to targets!");
                return;
            }
            player.teleport(target);
            player.sendMessage("§aTeleported to " + target.getName());
            return;
        }
        
        try {
            Object xObj = args.getUnchecked("x");
            Object yObj = args.getUnchecked("y");
            Object zObj = args.getUnchecked("z");
            if (xObj instanceof Number && yObj instanceof Number && zObj instanceof Number) {
                if (!PermissionManager.hasPermission(player, "minecraft.command.teleport.targets.location")) {
                    player.sendMessage("§cYou don't have permission to teleport to coordinates!");
                    return;
                }
                
                double x = ((Number) xObj).doubleValue();
                double y = ((Number) yObj).doubleValue();
                double z = ((Number) zObj).doubleValue();
                
                if (Math.abs(x) > 30000000 || Math.abs(z) > 30000000) {
                    player.sendMessage("§cCoordinates out of world bounds! Must be between -30,000,000 and 30,000,000");
                    return;
                }
                if (y < -64 || y > 320) {
                    player.sendMessage("§cY coordinate out of bounds! Must be between -64 and 320");
                    return;
                }
                
                Location location = new Location(player.getWorld(), x, y, z);
                try {
                    Object yawObj = args.getUnchecked("yaw");
                    Object pitchObj = args.getUnchecked("pitch");
                    if (yawObj instanceof Number && pitchObj instanceof Number) {
                        float yaw = ((Number) yawObj).floatValue();
                        float pitch = ((Number) pitchObj).floatValue();
                        location.setYaw(yaw);
                        location.setPitch(pitch);
                    }
                } catch (Exception ignored) {}
                
                player.teleport(location);
                player.sendMessage("§aTeleported to " + formatLocation(location));
                return;
            }
        } catch (Exception ignored) {}
        
        player.sendMessage("§cUsage: /tp <player/entity> or /tpcoords <x> <y> <z> [yaw] [pitch]");
    }

    private static String formatLocation(Location loc) {
        return String.format("%.2f, %.2f, %.2f", loc.getX(), loc.getY(), loc.getZ());
    }
}
