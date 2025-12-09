package io.github.soul4723.extrapermissions.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.DoubleArgument;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.FloatArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import io.github.soul4723.extrapermissions.ExtraPermissions;
import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class ExtraPermissionsCommand {
    
    public static void registerCommands(ExtraPermissions plugin) {
        registerAdminCommands(plugin);
        if (plugin.isFeatureEnabled("command_permissions")) {
            registerVanillaCommands();
        }
    }
    
    private static void registerAdminCommands(ExtraPermissions plugin) {
        try {
            new CommandAPICommand("extrapermissions")
                .withPermission("extrapermissions.use")
                .withAliases("eperm", "ep")
                .withSubcommand(new CommandAPICommand("reload")
                    .withPermission("extrapermissions.reload")
                    .executes((sender, args) -> {
                        plugin.reloadPluginConfig();
                        sender.sendMessage("§aExtraPermissions configuration reloaded!");
                        sender.sendMessage("§7Note: Some changes may require a full plugin restart to take effect.");
                    }))
                .withSubcommand(new CommandAPICommand("check")
                    .withPermission("extrapermissions.admin")
                    .withArguments(new EntitySelectorArgument.OnePlayer("player"))
                    .executes((sender, args) -> {
                        Object raw = args.getUnchecked("player");
                        if (!(raw instanceof Player)) {
                            sender.sendMessage("§cPlayer not found!");
                            return;
                        }
                        Player target = (Player) raw;
                        
                        sender.sendMessage("§6Permissions for " + target.getName() + ":");
                        String[] keyPermissions = {
                            "extrapermissions.use",
                            "extrapermissions.reload", 
                            "extrapermissions.admin",
                            "minecraft.command.gamemode",
                            "minecraft.command.teleport",
                            "minecraft.selector.self.s",
                            "minecraft.bypass.chat-speed",
                            "minecraft.operator_block.command_block.place"
                        };
                        
                        for (String permission : keyPermissions) {
                            boolean hasPermission = PermissionManager.hasPermissionOrParent(target, permission);
                            String status = hasPermission ? "§a✓" : "§c✗";
                            sender.sendMessage("§7  " + status + " §f" + permission);
                        }
                    }))
                .withSubcommand(new CommandAPICommand("debug")
                    .withPermission("extrapermissions.admin")
                    .executes((sender, args) -> {
                        sender.sendMessage("§6=== ExtraPermissions Debug Info ===");
                        sender.sendMessage("§7Plugin Version: §f" + plugin.getDescription().getVersion());
                        
                        var hook = PermissionManager.getLuckPermsHook();
                        sender.sendMessage("§7LuckPerms Integration: §f" + (hook != null && hook.isEnabled() ? "§aEnabled" : "§cDisabled"));
                        sender.sendMessage("§7" + PermissionManager.getCacheStats());
                        
                        sender.sendMessage("§7Feature Status:");
                        sender.sendMessage("§7  command_permissions: " + (plugin.isFeatureEnabled("command_permissions") ? "§aEnabled" : "§cDisabled"));
                        sender.sendMessage("§7  selector_permissions: " + (plugin.isFeatureEnabled("selector_permissions") ? "§aEnabled" : "§cDisabled"));
                        sender.sendMessage("§7  bypass_permissions: " + (plugin.isFeatureEnabled("bypass_permissions") ? "§aEnabled" : "§cDisabled"));
                        sender.sendMessage("§7  admin_permissions: " + (plugin.isFeatureEnabled("admin_permissions") ? "§aEnabled" : "§cDisabled"));
                    }))
                .register();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register ExtraPermissions admin commands: " + e.getMessage());
        }
    }
    
    private static void registerVanillaCommands() {
        try {
            new CommandAPICommand("gamemode")
                .withPermission("minecraft.command.gamemode")
                .withArguments(new StringArgument("gamemode")
                    .replaceSuggestions(ArgumentSuggestions.strings("survival", "creative", "adventure", "spectator")))
                .withOptionalArguments(new EntitySelectorArgument.OnePlayer("target"))
                .executes((sender, args) -> {
                    Object gmObj = args.getUnchecked("gamemode");
                    if (gmObj == null) {
                        sender.sendMessage("§cUsage: /gamemode <survival|creative|adventure|spectator> [player]");
                        return;
                    }
                    
                    String gamemode = ((String) gmObj).toLowerCase();
                    if (gamemode.isEmpty()) {
                        sender.sendMessage("§cInvalid gamemode");
                        return;
                    }
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
                        if (!PermissionManager.hasPermissionOrParent(player, gamemodePermission)) {
                            player.sendMessage("§cYou don't have permission to use gamemode " + gamemode + "!");
                            return;
                        }
                        
                        org.bukkit.GameMode gm = org.bukkit.GameMode.valueOf(gamemode.toUpperCase());
                        player.setGameMode(gm);
                        player.sendMessage("§aGamemode set to " + gamemode);
                    } else {
                        if (!PermissionManager.hasPermissionOrParent(sender, "minecraft.command.gamemode.other")) {
                            sender.sendMessage("§cYou don't have permission to set other players' gamemode!");
                            return;
                        }
                        
                        String specificPermission = "minecraft.command.gamemode." + gamemode + ".other";
                        if (!PermissionManager.hasPermissionOrParent(sender, specificPermission)) {
                            sender.sendMessage("§cYou don't have permission to set " + gamemode + " for other players!");
                            return;
                        }
                        
                        org.bukkit.GameMode gm = org.bukkit.GameMode.valueOf(gamemode.toUpperCase());
                        target.setGameMode(gm);
                        sender.sendMessage("§aSet " + target.getName() + "'s gamemode to " + gamemode);
                        target.sendMessage("§aYour gamemode was set to " + gamemode + " by " + sender.getName());
                    }
                })
                .register();
            
            new CommandAPICommand("tp")
                .withPermission("minecraft.command.teleport")
                .withArguments(new EntitySelectorArgument.OneEntity("target"))
                .executes((sender, args) -> {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage("§cThis command can only be run by players!");
                        return;
                    }
                    Player player = (Player) sender;
                    
                    Object rawTarget = args.getUnchecked("target");
                    if (rawTarget instanceof Entity) {
                        Entity target = (Entity) rawTarget;
                        if (!PermissionManager.hasPermissionOrParent(player, "minecraft.command.teleport.targets")) {
                            player.sendMessage("§cYou don't have permission to teleport to targets!");
                            return;
                        }
                        player.teleport(target);
                        player.sendMessage("§aTeleported to " + target.getName());
                        return;
                    }
                    
                    player.sendMessage("§cUsage: /tp <player/entity> or /tp <x> <y> <z> [yaw] [pitch]");
                })
                .register();
            
            new CommandAPICommand("tp")
                .withPermission("minecraft.command.teleport")
                .withArguments(
                    new DoubleArgument("x"),
                    new DoubleArgument("y"), 
                    new DoubleArgument("z")
                )
                .withOptionalArguments(
                    new FloatArgument("yaw"),
                    new FloatArgument("pitch")
                )
                .executes((sender, args) -> {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage("§cThis command can only be run by players!");
                        return;
                    }
                    Player player = (Player) sender;
                    
                    try {
                        Object xObj = args.getUnchecked("x");
                        Object yObj = args.getUnchecked("y");
                        Object zObj = args.getUnchecked("z");
                        if (xObj instanceof Number && yObj instanceof Number && zObj instanceof Number) {
                            if (!PermissionManager.hasPermissionOrParent(player, "minecraft.command.teleport.targets.location")) {
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
                    
                    player.sendMessage("§cUsage: /tp <player/entity> or /tp <x> <y> <z> [yaw] [pitch]");
                })
                .register();
        } catch (Exception e) {
            throw new RuntimeException("Failed to register vanilla command replacements: " + e.getMessage(), e);
        }
    }
    
    private static String formatLocation(Location loc) {
        return String.format("%.2f, %.2f, %.2f", loc.getX(), loc.getY(), loc.getZ());
    }
}