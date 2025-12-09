package io.github.soul4723.extrapermissions.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.TextArgument;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class GameruleCommandHandler {

    private static final String BASE_PERM = "minecraft.command.gamerule";

    public static void registerCommands() {
        try {
            CommandAPI.unregister("gamerule");
        } catch (Exception e) {
            Bukkit.getLogger().fine("Gamerule command not previously registered");
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

    private static <T> void setGameRuleValue(org.bukkit.World world, org.bukkit.GameRule<T> gameRule, String value) {
        T val = (T) value;
        world.setGameRule(gameRule, val);
    }
}
