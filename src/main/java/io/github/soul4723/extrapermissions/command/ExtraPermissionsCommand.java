package io.github.soul4723.extrapermissions.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.DoubleArgument;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.FloatArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import io.github.soul4723.extrapermissions.ExtraPermissions;

public class ExtraPermissionsCommand {
    
    public static void registerCommands(ExtraPermissions plugin) {
        // Always register admin commands
        registerAdminCommands(plugin);
        
        // Register vanilla command replacements only if feature is enabled
        if (plugin.isFeatureEnabled("command_permissions")) {
            registerVanillaCommands();
        }
    }
    
    private static void registerAdminCommands(ExtraPermissions plugin) {
        new CommandAPICommand("extrapermissions")
            .withPermission("extrapermissions.use")
            .withAliases("eperm", "ep")
            .withSubcommand(new CommandAPICommand("reload")
                .withPermission("extrapermissions.reload")
                .executes((sender, args) -> BasicCommandHandlers.handleReload(sender, args, plugin)))
            .withSubcommand(new CommandAPICommand("check")
                .withPermission("extrapermissions.admin")
                .withArguments(new EntitySelectorArgument.OnePlayer("player"))
                .executes(BasicCommandHandlers::handleCheck))
            .withSubcommand(new CommandAPICommand("debug")
                .withPermission("extrapermissions.admin")
                .executes((sender, args) -> BasicCommandHandlers.handleDebug(sender, args, plugin)))
            .register();
    }
    
    private static void registerVanillaCommands() {
        // Register gamemode command replacement
        new CommandAPICommand("gamemode")
            .withPermission("minecraft.command.gamemode")
            .withAliases("gm")
            .withArguments(new StringArgument("gamemode")
                .replaceSuggestions(ArgumentSuggestions.strings("survival", "creative", "adventure", "spectator")))
            .withOptionalArguments(new EntitySelectorArgument.OnePlayer("target"))
            .executes(GamemodeCommandHandler::handleGamemodeCommand)
            .register();
        
        // Register teleport command replacement
        new CommandAPICommand("teleport")
            .withPermission("minecraft.command.teleport")
            .withAliases("tp")
            .withArguments(new EntitySelectorArgument.OneEntity("target"))
            .executes(TeleportCommandHandler::handleTeleportCommand)
            .register();
        
        // Register teleport coordinates command
        new CommandAPICommand("tpcoords")
            .withPermission("minecraft.command.teleport.targets.location")
            .withArguments(
                new DoubleArgument("x"),
                new DoubleArgument("y"), 
                new DoubleArgument("z"),
                new FloatArgument("yaw"),
                new FloatArgument("pitch")
            )
            .executes(TeleportCommandHandler::handleTeleportCommand)
            .register();
    }
}