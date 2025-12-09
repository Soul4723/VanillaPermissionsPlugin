package io.github.soul4723.extrapermissions.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class WeatherCommandHandler {

    private static final String BASE_PERM = "minecraft.command.weather";

    public static void registerCommands() {
        try {
            CommandAPI.unregister("weather");
        } catch (Exception e) {
            Bukkit.getLogger().fine("Weather command not previously registered");
        }

        CommandAPICommand weather = new CommandAPICommand("weather")
                .withSubcommand(new CommandAPICommand("clear")
                        .withPermission(BASE_PERM + ".clear")
                        .executesPlayer((player, args) -> {
                            World world = player.getWorld();
                            world.setStorm(false);
                            world.setThundering(false);
                            player.sendMessage("§aWeather cleared.");
                        })
                )
                .withSubcommand(new CommandAPICommand("rain")
                        .withPermission(BASE_PERM + ".rain")
                        .executesPlayer((player, args) -> {
                            World world = player.getWorld();
                            world.setStorm(true);
                            world.setThundering(false);
                            player.sendMessage("§aRain started.");
                        })
                )
                .withSubcommand(new CommandAPICommand("thunder")
                        .withPermission(BASE_PERM + ".thunder")
                        .executesPlayer((player, args) -> {
                            World world = player.getWorld();
                            world.setStorm(true);
                            world.setThundering(true);
                            player.sendMessage("§aThunderstorm started.");
                        })
                );

        weather.register();
    }
}
