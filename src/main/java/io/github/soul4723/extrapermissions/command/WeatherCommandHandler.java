package io.github.soul4723.extrapermissions.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class WeatherCommandHandler {

    private static final String BASE_PERM = "minecraft.command.weather";

    public static void registerCommands(JavaPlugin plugin) {
        try {
            CommandAPI.unregister("weather");
        } catch (Exception e) {
            plugin.getLogger().fine("Weather command not previously registered");
        }

        CommandAPICommand weather = new CommandAPICommand("weather")
                .withSubcommand(new CommandAPICommand("clear")
                        .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, BASE_PERM + ".clear"))
                        .executesNative((proxy, args) -> {
                            CommandSender sender = proxy.getCaller();
                            World world = proxy.getWorld();
                            world.setStorm(false);
                            world.setThundering(false);
                            sender.sendMessage("§aWeather cleared.");
                        })
                )
                .withSubcommand(new CommandAPICommand("rain")
                        .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, BASE_PERM + ".rain"))
                        .executesNative((proxy, args) -> {
                            CommandSender sender = proxy.getCaller();
                            World world = proxy.getWorld();
                            world.setStorm(true);
                            world.setThundering(false);
                            sender.sendMessage("§aRain started.");
                        })
                )
                .withSubcommand(new CommandAPICommand("thunder")
                        .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, BASE_PERM + ".thunder"))
                        .executesNative((proxy, args) -> {
                            CommandSender sender = proxy.getCaller();
                            World world = proxy.getWorld();
                            world.setStorm(true);
                            world.setThundering(true);
                            sender.sendMessage("§aThunderstorm started.");
                        })
                );

        weather.register();
    }
}
