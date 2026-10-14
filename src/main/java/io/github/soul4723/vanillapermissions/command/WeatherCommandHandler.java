package io.github.soul4723.vanillapermissions.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.IntegerArgument;
import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

public class WeatherCommandHandler {

    private static final String BASE_PERM = "minecraft.command.weather";

    public static void registerCommands(JavaPlugin plugin) {
        try {
            CommandAPI.unregister("weather");
        } catch (Exception e) {
            plugin.getLogger().fine("Weather command not previously registered");
        }

        new CommandAPICommand("weather")
                .withSubcommands(weather("clear"), weather("rain"), weather("thunder"))
                .register();
    }

    private static CommandAPICommand weather(String type) {
        return new CommandAPICommand(type)
                .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, BASE_PERM + "." + type))
                .withOptionalArguments(new IntegerArgument("duration", 0, 1_000_000))
                .executesNative((proxy, args) -> {
                    CommandSender sender = proxy.getCaller();
                    if (!requirePermission(sender, type)) {
                        return;
                    }
                    Integer seconds = (Integer) args.get("duration");
                    setWeather(proxy.getWorld(), type, seconds);
                    sender.sendMessage(switch (type) {
                        case "clear" -> "§aWeather cleared.";
                        case "rain" -> "§aRain started.";
                        default -> "§aThunderstorm started.";
                    });
                });
    }

    private static boolean requirePermission(CommandSender sender, String type) {
        if (PermissionManager.hasPermissionOrParent(sender, BASE_PERM + "." + type)) {
            return true;
        }
        sender.sendMessage("§cYou don't have permission to use /weather " + type + ".");
        return false;
    }

    static void setWeather(World world, String type, Integer durationSeconds) {
        int ticks = durationSeconds == null
                ? ThreadLocalRandom.current().nextInt(6_000, 18_001)
                : durationSeconds * 20;
        switch (type) {
            case "clear" -> {
                world.setStorm(false);
                world.setThundering(false);
            }
            case "rain" -> {
                world.setStorm(true);
                world.setThundering(false);
            }
            case "thunder" -> {
                world.setStorm(true);
                world.setThundering(true);
            }
            default -> throw new IllegalArgumentException("Unknown weather: " + type);
        }
        world.setWeatherDuration(ticks);
        world.setThunderDuration(ticks);
    }
}
