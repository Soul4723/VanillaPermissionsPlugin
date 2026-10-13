package io.github.soul4723.extrapermissions.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.IntegerArgument;
import io.github.soul4723.extrapermissions.util.PermissionManager;
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

        CommandAPICommand timeCmd = new CommandAPICommand("time")
                .withSubcommand(new CommandAPICommand("set")
                        .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, BASE_PERM + ".set"))
                        .withArguments(new IntegerArgument("time"))
                        .executesNative((proxy, args) -> {
                            CommandSender sender = proxy.getCaller();
                            Integer ticksObj = (Integer) args.get("time");
                            if (ticksObj == null) {
                                sender.sendMessage("§cTime is required");
                                return;
                            }
                            int ticks = ticksObj;
                            World world = proxy.getWorld();
                            world.setTime(ticks);
                            sender.sendMessage("§aTime set to " + ticks);
                        })
                )
                .withSubcommand(new CommandAPICommand("add")
                        .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, BASE_PERM + ".add"))
                        .withArguments(new IntegerArgument("time"))
                        .executesNative((proxy, args) -> {
                            CommandSender sender = proxy.getCaller();
                            Integer ticksObj = (Integer) args.get("time");
                            if (ticksObj == null) {
                                sender.sendMessage("§cTime is required");
                                return;
                            }
                            int ticks = ticksObj;
                            World world = proxy.getWorld();
                            world.setTime(world.getTime() + ticks);
                            sender.sendMessage("§aAdded " + ticks + " ticks to time");
                        })
                )
                .withSubcommand(new CommandAPICommand("query")
                        .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, BASE_PERM + ".query"))
                        .withArguments(new dev.jorel.commandapi.arguments.MultiLiteralArgument("type", "daytime", "gametime", "day"))
                        .executesNative((proxy, args) -> {
                            CommandSender sender = proxy.getCaller();
                            World world = proxy.getWorld();
                            String type = (String) args.get("type");
                            if (type == null) {
                                sender.sendMessage("§cTime type is required");
                                return;
                            }
                            long time;
                            if (type.equals("gametime")) {
                                time = world.getGameTime();
                            } else if (type.equals("day")) {
                                time = world.getFullTime() / 24000L;
                            } else {
                                time = world.getTime();
                            }
                            sender.sendMessage("§aTime: " + time);
                        })
                );

        timeCmd.register();
    }
}
