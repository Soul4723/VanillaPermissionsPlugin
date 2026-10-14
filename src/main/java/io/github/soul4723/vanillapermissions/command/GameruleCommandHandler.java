package io.github.soul4723.vanillapermissions.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.TextArgument;
import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class GameruleCommandHandler {

    private static final String BASE_PERM = "minecraft.command.gamerule";

    public static void registerCommands(JavaPlugin plugin) {
        try {
            CommandAPI.unregister("gamerule");
        } catch (Exception e) {
            plugin.getLogger().fine("Gamerule command not previously registered");
        }

        CommandAPICommand gamerule = new CommandAPICommand("gamerule")
                .withSubcommand(new CommandAPICommand("query")
                        .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, BASE_PERM + ".query"))
                        .withArguments(new TextArgument("rule"))
                        .executesNative((proxy, args) -> {
                            CommandSender sender = proxy.getCaller();
                            String rule = (String) args.get("rule");
                            if (rule == null || rule.isEmpty()) {
                                sender.sendMessage("§cRule name is required");
                                return;
                            }
                            World world = proxy.getWorld();
                            try {
                                org.bukkit.GameRule<?> gameRule = org.bukkit.GameRule.getByName(rule);
                                if (gameRule == null) {
                                    sender.sendMessage("§cUnknown gamerule: " + rule);
                                    return;
                                }
                                Object ruleValue = world.getGameRuleValue(gameRule);
                                sender.sendMessage("§a" + rule + " = " + ruleValue);
                            } catch (Exception e) {
                                sender.sendMessage("§cUnknown gamerule: " + rule);
                            }
                        })
                )
                .withSubcommand(new CommandAPICommand("set")
                        .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, BASE_PERM + ".set"))
                        .withArguments(new TextArgument("rule"), new TextArgument("value"))
                        .executesNative((proxy, args) -> {
                            CommandSender sender = proxy.getCaller();
                            String rule = (String) args.get("rule");
                            String value = (String) args.get("value");
                            if (rule == null || rule.isEmpty()) {
                                sender.sendMessage("§cRule name is required");
                                return;
                            }
                            if (value == null || value.isEmpty()) {
                                sender.sendMessage("§cValue is required");
                                return;
                            }
                            World world = proxy.getWorld();
                            try {
                                org.bukkit.GameRule<?> gameRule = org.bukkit.GameRule.getByName(rule);
                                if (gameRule == null) {
                                    sender.sendMessage("§cUnknown gamerule: " + rule);
                                    return;
                                }
                                setGameRuleValue(world, gameRule, value);
                                sender.sendMessage("§aSet " + rule + " to " + value);
                            } catch (Exception e) {
                                sender.sendMessage("§cError setting gamerule: " + e.getMessage());
                            }
                        })
                );

        gamerule.register();
    }

    @SuppressWarnings("unchecked")
    private static <T> void setGameRuleValue(org.bukkit.World world, org.bukkit.GameRule<T> gameRule, String value) throws IllegalArgumentException {
        Class<?> type = gameRule.getType();
        boolean success;

        if (type == Boolean.class) {
            if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException("Invalid boolean value: " + value + " (expected true or false)");
            }
            Boolean boolValue = Boolean.parseBoolean(value);
            success = world.setGameRule((org.bukkit.GameRule<Boolean>) gameRule, boolValue);
        } else if (type == Integer.class) {
            try {
                Integer intValue = Integer.parseInt(value);
                success = world.setGameRule((org.bukkit.GameRule<Integer>) gameRule, intValue);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid integer value: " + value);
            }
        } else {
            throw new IllegalArgumentException("Unsupported gamerule type: " + type.getName());
        }
        if (!success) {
            throw new IllegalArgumentException("Failed to set gamerule: " + gameRule.getName());
        }
    }
}
