package io.github.soul4723.extrapermissions.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.DoubleArgument;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.FloatArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import io.github.soul4723.extrapermissions.command.BasicCommandHandlers;
import io.github.soul4723.extrapermissions.command.GamemodeCommandHandler;
import io.github.soul4723.extrapermissions.command.TeleportCommandHandler;

public class ExtraPermissionsCommand {
    
    public static void registerCommands() {
        new CommandAPICommand("extrapermissions")
            .withPermission("extrapermissions.use")
            .withAliases("eperm", "ep")
            .withSubcommand(new CommandAPICommand("reload")
                .withPermission("extrapermissions.reload")
                .executes(BasicCommandHandlers::handleReload))
            .withSubcommand(new CommandAPICommand("check")
                .withArguments(new EntitySelectorArgument.OnePlayer("player"))
                .executes(BasicCommandHandlers::handleCheck))
            .withSubcommand(new CommandAPICommand("gamemode")
                .withPermission("minecraft.command.gamemode")
                .withArguments(new StringArgument("gamemode")
                    .replaceSuggestions(ArgumentSuggestions.strings("survival", "creative", "adventure", "spectator")))
                .withOptionalArguments(new EntitySelectorArgument.OnePlayer("target"))
                .executes(GamemodeCommandHandler::handleGamemodeCommand))
            .withSubcommand(new CommandAPICommand("teleport")
                .withPermission("minecraft.command.teleport")
                .withArguments(
                    new EntitySelectorArgument.OneEntity("target")
                )
                .executes(TeleportCommandHandler::handleTeleportCommand))
            .withSubcommand(new CommandAPICommand("tpcoords")
                .withPermission("minecraft.command.teleport")
                .withArguments(
                    new DoubleArgument("x"),
                    new DoubleArgument("y"), 
                    new DoubleArgument("z"),
                    new FloatArgument("yaw"),
                    new FloatArgument("pitch")
                )
                .executes(TeleportCommandHandler::handleTeleportCommand))
            .register();
    }
}