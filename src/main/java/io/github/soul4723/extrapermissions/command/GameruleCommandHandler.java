package io.github.soul4723.extrapermissions.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.TextArgument;
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
                        .withPermission(BASE_PERM + ".query")
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
                        .withPermission(BASE_PERM + ".set")
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
        
        if (type == Boolean.class) {
            Boolean boolValue = Boolean.parseBoolean(value);
            world.setGameRule((org.bukkit.GameRule<Boolean>) gameRule, boolValue);
        } else if (type == Integer.class) {
            try {
                Integer intValue = Integer.parseInt(value);
                world.setGameRule((org.bukkit.GameRule<Integer>) gameRule, intValue);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid integer value: " + value);
            }
        } else {
            throw new IllegalArgumentException("Unsupported gamerule type: " + type.getName());
        }
    }
}
