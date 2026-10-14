package io.github.soul4723.vanillapermissions.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class TimeCommandHandler {

    private static final String BASE_PERM = "minecraft.command.time";

    public static void registerCommands(JavaPlugin plugin) {
        try {
            CommandAPI.unregister("time");
        } catch (Exception e) {
            plugin.getLogger().fine("Time command not previously registered");
        }

        new CommandAPICommand("time")
                .withSubcommand(new CommandAPICommand("set")
                        .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, BASE_PERM + ".set"))
                        .withArguments(new StringArgument("time").replaceSuggestions(
                                ArgumentSuggestions.strings("day", "noon", "night", "midnight")))
                        .executesNative((proxy, args) -> {
                            CommandSender sender = proxy.getCaller();
                            if (!requirePermission(sender, "set")) {
                                return;
                            }
                            String value = (String) args.get("time");
                            try {
                                int ticks = parseTime(value);
                                proxy.getWorld().setTime(ticks);
                                sender.sendMessage("§aTime set to " + ticks);
                            } catch (IllegalArgumentException e) {
                                sender.sendMessage("§c" + e.getMessage());
                            }
                        }))
                .withSubcommand(new CommandAPICommand("add")
                        .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, BASE_PERM + ".add"))
                        .withArguments(new IntegerArgument("time"))
                        .executesNative((proxy, args) -> {
                            CommandSender sender = proxy.getCaller();
                            if (!requirePermission(sender, "add")) {
                                return;
                            }
                            int ticks = (Integer) args.get("time");
                            World world = proxy.getWorld();
                            world.setTime(world.getTime() + ticks);
                            sender.sendMessage("§aAdded " + ticks + " ticks to time");
                        }))
                .withSubcommand(new CommandAPICommand("query")
                        .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, BASE_PERM + ".query"))
                        .withArguments(new MultiLiteralArgument("type", "daytime", "gametime", "day"))
                        .executesNative((proxy, args) -> {
                            CommandSender sender = proxy.getCaller();
                            if (!requirePermission(sender, "query")) {
                                return;
                            }
                            World world = proxy.getWorld();
                            String type = (String) args.get("type");
                            long time = switch (type) {
                                case "gametime" -> world.getGameTime();
                                case "day" -> world.getFullTime() / 24_000L;
                                default -> world.getTime();
                            };
                            sender.sendMessage("§aTime: " + time);
                        }))
                .register();
    }

    private static boolean requirePermission(CommandSender sender, String action) {
        if (PermissionManager.hasPermissionOrParent(sender, BASE_PERM + "." + action)) {
            return true;
        }
        sender.sendMessage("§cYou don't have permission to use /time " + action + ".");
        return false;
    }

    static int parseTime(String value) {
        return switch (value) {
            case "day" -> 1_000;
            case "noon" -> 6_000;
            case "night" -> 13_000;
            case "midnight" -> 18_000;
            default -> {
                if (value.isEmpty() || !value.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
                    throw new IllegalArgumentException("Invalid time: " + value);
                }
                try {
                    yield Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid time: " + value);
                }
            }
        };
    }
}
