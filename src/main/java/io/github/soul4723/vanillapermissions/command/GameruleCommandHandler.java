package io.github.soul4723.vanillapermissions.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.BooleanArgument;
import dev.jorel.commandapi.arguments.IntegerArgument;
import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class GameruleCommandHandler {

    private static final String BASE_PERM = "minecraft.command.gamerule";

    public static void registerCommands(JavaPlugin plugin) {
        try {
            CommandAPI.unregister("gamerule");
        } catch (Exception e) {
            plugin.getLogger().fine("Gamerule command not previously registered");
        }

        List<CommandAPICommand> rules = new ArrayList<>();
        for (GameRule<?> rule : GameRule.values()) {
            CommandAPICommand command = ruleCommand(rule);
            if (command != null) {
                rules.add(command);
            }
        }
        new CommandAPICommand("gamerule")
                .withSubcommands(rules.toArray(CommandAPICommand[]::new))
                .register();
    }

    private static CommandAPICommand ruleCommand(GameRule<?> rule) {
        CommandAPICommand command = new CommandAPICommand(rule.getName())
                .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, BASE_PERM + ".query")
                        || PermissionManager.hasPermissionOrParent(sender, BASE_PERM + ".set"));

        if (rule.getType() == Boolean.class) {
            command.withOptionalArguments(new BooleanArgument("value"));
        } else if (rule.getType() == Integer.class) {
            command.withOptionalArguments(new IntegerArgument("value"));
        } else {
            return null;
        }

        return command.executesNative((proxy, args) -> {
            CommandSender sender = proxy.getCaller();
            Object value = args.get("value");
            String permission = value == null ? BASE_PERM + ".query" : BASE_PERM + ".set";
            if (!PermissionManager.hasPermissionOrParent(sender, permission)) {
                sender.sendMessage("§cYou don't have permission to " + (value == null ? "query" : "set") + " gamerules.");
                return;
            }

            World world = proxy.getWorld();
            if (value == null) {
                sender.sendMessage("§a" + rule.getName() + " = " + world.getGameRuleValue(rule));
                return;
            }

            try {
                setGameRuleValue(world, rule, value);
                sender.sendMessage("§aSet " + rule.getName() + " to " + value);
            } catch (IllegalArgumentException e) {
                sender.sendMessage("§cError setting gamerule: " + e.getMessage());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> void setGameRuleValue(World world, GameRule<?> rule, Object value) {
        GameRule<T> typedRule = (GameRule<T>) rule;
        T typedValue = typedRule.getType().cast(value);
        if (!world.setGameRule(typedRule, typedValue)) {
            throw new IllegalArgumentException("Failed to set gamerule: " + typedRule.getName());
        }
    }
}
