package io.github.soul4723.extrapermissions.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.TextArgument;
import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.World;
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
                        .executesPlayer((player, args) -> {
                            String rule = (String) args.get("rule");
                            if (rule == null || rule.isEmpty()) {
                                player.sendMessage("§cRule name is required");
                                return;
                            }
                            World world = player.getWorld();
                            try {
                                org.bukkit.GameRule<?> gameRule = org.bukkit.GameRule.getByName(rule);
                                if (gameRule == null) {
                                    player.sendMessage("§cUnknown gamerule: " + rule);
                                    return;
                                }
                                Object ruleValue = world.getGameRuleValue(gameRule);
                                player.sendMessage("§a" + rule + " = " + ruleValue);
                            } catch (Exception e) {
                                player.sendMessage("§cUnknown gamerule: " + rule);
                            }
                        })
                )
                .withSubcommand(new CommandAPICommand("set")
                        .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, BASE_PERM + ".set"))
                        .withArguments(new TextArgument("rule"), new TextArgument("value"))
                        .executesPlayer((player, args) -> {
                            String rule = (String) args.get("rule");
                            String value = (String) args.get("value");
                            if (rule == null || rule.isEmpty()) {
                                player.sendMessage("§cRule name is required");
                                return;
                            }
                            if (value == null || value.isEmpty()) {
                                player.sendMessage("§cValue is required");
                                return;
                            }
                            World world = player.getWorld();
                            try {
                                org.bukkit.GameRule<?> gameRule = org.bukkit.GameRule.getByName(rule);
                                if (gameRule == null) {
                                    player.sendMessage("§cUnknown gamerule: " + rule);
                                    return;
                                }
                                setGameRuleValue(world, gameRule, value);
                                player.sendMessage("§aSet " + rule + " to " + value);
                            } catch (Exception e) {
                                player.sendMessage("§cError setting gamerule: " + e.getMessage());
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
