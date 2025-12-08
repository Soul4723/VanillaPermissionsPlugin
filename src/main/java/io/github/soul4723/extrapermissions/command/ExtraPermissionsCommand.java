package io.github.soul4723.extrapermissions.command;

import dev.jorel.commandapi.executors.CommandExecutor;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.DoubleArgument;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.FloatArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import io.github.soul4723.extrapermissions.ExtraPermissions;

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
                    .executes((CommandExecutor) (sender, args) -> BasicCommandHandlers.handleReload(sender, plugin)))
                .withSubcommand(new CommandAPICommand("check")
                    .withPermission("extrapermissions.admin")
                    .withArguments(new EntitySelectorArgument.OnePlayer("player"))
                    .executes((CommandExecutor) BasicCommandHandlers::handleCheck))
                .withSubcommand(new CommandAPICommand("debug")
                    .withPermission("extrapermissions.admin")
                    .executes((CommandExecutor) (sender, args) -> BasicCommandHandlers.handleDebug(sender, plugin)))
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
                .executes((CommandExecutor) GamemodeCommandHandler::handleGamemodeCommand)
                .register();
            
            new CommandAPICommand("tp")
                .withPermission("minecraft.command.teleport")
                .withArguments(new EntitySelectorArgument.OneEntity("target"))
                .executes((CommandExecutor) TeleportCommandHandler::handleTeleportCommand)
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
                .executes((CommandExecutor) TeleportCommandHandler::handleTeleportCommand)
                .register();
        } catch (Exception e) {
            throw new RuntimeException("Failed to register vanilla command replacements: " + e.getMessage(), e);
        }
    }
}