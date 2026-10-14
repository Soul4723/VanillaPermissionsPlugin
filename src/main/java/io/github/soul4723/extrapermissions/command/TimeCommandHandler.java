package io.github.soul4723.extrapermissions.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.wrappers.NativeProxyCommandSender;
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

        CommandAPICommand set = new CommandAPICommand("set")
                        .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, BASE_PERM + ".set"))
                        .withArguments(new IntegerArgument("time", 0))
                        .executesNative((proxy, args) -> {
                            Integer ticksObj = (Integer) args.get("time");
                            if (ticksObj == null) {
                                proxy.getCaller().sendMessage("§cTime is required");
                                return;
                            }
                            setTime(proxy, ticksObj);
                        });

        set.withSubcommands(
                timeAlias("day", 1_000),
                timeAlias("noon", 6_000),
                timeAlias("night", 13_000),
                timeAlias("midnight", 18_000)
        );

        CommandAPICommand timeCmd = new CommandAPICommand("time")
                .withSubcommand(set)
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

    private static CommandAPICommand timeAlias(String name, int ticks) {
        return new CommandAPICommand(name)
                .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, BASE_PERM + ".set"))
                .executesNative((proxy, args) -> setTime(proxy, ticks));
    }

    private static void setTime(NativeProxyCommandSender proxy, int ticks) {
        World world = proxy.getWorld();
        world.setTime(ticks);
        proxy.getCaller().sendMessage("§aTime set to " + ticks);
    }
}
