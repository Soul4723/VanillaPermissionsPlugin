package io.github.soul4723.extrapermissions.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
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
                .withPermission(BASE_PERM)
                .withArguments(new MultiLiteralArgument("difficulty", "peaceful", "easy", "normal", "hard"))
                .executesPlayer((player, args) -> {
                    String diffStr = (String) args.get("difficulty");
                    if (diffStr == null) {
                        player.sendMessage("§cInvalid difficulty specified");
                        return;
                    }
                    Difficulty diff = Difficulty.valueOf(diffStr.toUpperCase());
                    for (org.bukkit.World world : Bukkit.getWorlds()) {
                        world.setDifficulty(diff);
                    }
                    player.sendMessage("§aSet difficulty to " + diffStr);
                });

        difficulty.register();
    }
}
