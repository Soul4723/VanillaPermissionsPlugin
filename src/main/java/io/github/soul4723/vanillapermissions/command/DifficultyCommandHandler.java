package io.github.soul4723.vanillapermissions.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class DifficultyCommandHandler {

    private static final String BASE_PERM = "minecraft.command.difficulty";

    public static void registerCommands(JavaPlugin plugin) {
        try {
            CommandAPI.unregister("difficulty");
        } catch (Exception e) {
            plugin.getLogger().fine("Difficulty command not previously registered");
        }

        CommandAPICommand difficulty = new CommandAPICommand("difficulty")
                .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, BASE_PERM))
                .withArguments(new MultiLiteralArgument("difficulty", "peaceful", "easy", "normal", "hard"))
                .executesNative((proxy, args) -> {
                    CommandSender sender = proxy.getCaller();
                    String diffStr = (String) args.get("difficulty");
                    if (diffStr == null) {
                        sender.sendMessage("§cInvalid difficulty specified");
                        return;
                    }
                    Difficulty diff = Difficulty.valueOf(diffStr.toUpperCase());
                    for (org.bukkit.World world : Bukkit.getWorlds()) {
                        world.setDifficulty(diff);
                    }
                    sender.sendMessage("§aSet difficulty to " + diffStr);
                });

        difficulty.register();
    }
}
