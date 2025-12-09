package io.github.soul4723.extrapermissions.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.IntegerArgument;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class TimeCommandHandler {

    private static final String BASE_PERM = "minecraft.command.time";

    public static void registerCommands() {
        try {
            CommandAPI.unregister("time");
        } catch (Exception e) {
            Bukkit.getLogger().fine("Time command not previously registered");
        }

        CommandAPICommand timeCmd = new CommandAPICommand("time")
                .withSubcommand(new CommandAPICommand("set")
                        .withPermission(BASE_PERM + ".set")
                        .withArguments(new IntegerArgument("time", 0, 24000))
                        .executesPlayer((player, args) -> {
                            Integer ticksObj = (Integer) args.get("time");
                            if (ticksObj == null) {
                                player.sendMessage("§cTime is required");
                                return;
                            }
                            int ticks = ticksObj;
                            World world = player.getWorld();
                            world.setTime(ticks);
                            player.sendMessage("§aTime set to " + ticks);
                        })
                )
                .withSubcommand(new CommandAPICommand("add")
                        .withPermission(BASE_PERM + ".add")
                        .withArguments(new IntegerArgument("time", 0))
                        .executesPlayer((player, args) -> {
                            Integer ticksObj = (Integer) args.get("time");
                            if (ticksObj == null) {
                                player.sendMessage("§cTime is required");
                                return;
                            }
                            int ticks = ticksObj;
                            World world = player.getWorld();
                            world.setTime(world.getTime() + ticks);
                            player.sendMessage("§aAdded " + ticks + " ticks to time");
                        })
                )
                .withSubcommand(new CommandAPICommand("query")
                        .withPermission(BASE_PERM + ".query")
                        .withArguments(new dev.jorel.commandapi.arguments.MultiLiteralArgument("type", "daytime", "gametime", "day"))
                        .executesPlayer((player, args) -> {
                            World world = player.getWorld();
                            String type = (String) args.get("type");
                            if (type == null) {
                                player.sendMessage("§cTime type is required");
                                return;
                            }
                            long time;
                            if (type.equals("gametime")) {
                                time = world.getGameTime();
                            } else if (type.equals("day")) {
                                time = world.getTime() / 24000L;
                            } else {
                                time = world.getTime();
                            }
                            player.sendMessage("§aTime: " + time);
                        })
                );

        timeCmd.register();
    }
}
