package io.github.soul4723.vanillapermissions.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import io.github.soul4723.vanillapermissions.VanillaPermissionsPlugin;
import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.entity.Player;

public final class VanillaPermissionsPluginCommand {

    private VanillaPermissionsPluginCommand() {}

    public static void registerCommands(VanillaPermissionsPlugin plugin) {
        registerAdminCommands(plugin);
    }

    private static void registerAdminCommands(VanillaPermissionsPlugin plugin) {
        try {
            new CommandAPICommand("vanillapermissions")
                .withRequirement(sender -> PermissionManager.hasPermissionOrFallback(
                    sender, "vanillapermissions.use", sender.hasPermission("vanillapermissions.use")))
                .withSubcommand(new CommandAPICommand("check")
                    .withRequirement(sender -> PermissionManager.hasPermissionOrFallback(
                        sender, "vanillapermissions.admin", sender.hasPermission("vanillapermissions.admin")))
                    .withArguments(new EntitySelectorArgument.OnePlayer("player"))
                    .executes((sender, args) -> {
                        Object raw = args.getUnchecked("player");
                        if (!(raw instanceof Player target)) {
                            sender.sendMessage("§cPlayer not found.");
                            return;
                        }

                        sender.sendMessage("§6Permissions for " + target.getName() + ":");
                        String[] permissions = {
                            "vanillapermissions.use",
                            "vanillapermissions.admin",
                            "minecraft.command.gamemode",
                            "minecraft.command.teleport",
                            "minecraft.selector"
                        };
                        for (String permission : permissions) {
                            String status = PermissionManager.hasPermission(target, permission) ? "§a✓" : "§c✗";
                            sender.sendMessage("§7  " + status + " §f" + permission);
                        }
                    }))
                .withSubcommand(new CommandAPICommand("debug")
                    .withRequirement(sender -> PermissionManager.hasPermissionOrFallback(
                        sender, "vanillapermissions.admin", sender.hasPermission("vanillapermissions.admin")))
                    .executes((sender, args) -> {
                        sender.sendMessage("§6=== VanillaPermissionsPlugin Debug Info ===");
                        sender.sendMessage("§7Plugin Version: §f" + plugin.getDescription().getVersion());

                        var hook = PermissionManager.getLuckPermsHook();
                        sender.sendMessage("§7LuckPerms Integration: "
                            + (hook != null && hook.isEnabled() ? "§aEnabled" : "§cDisabled"));
                    }))
                .register();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register VanillaPermissionsPlugin admin commands: " + e.getMessage());
        }
    }

}
