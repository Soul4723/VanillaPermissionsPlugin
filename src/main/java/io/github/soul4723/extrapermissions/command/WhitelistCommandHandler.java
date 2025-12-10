package io.github.soul4723.extrapermissions.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.TextArgument;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class WhitelistCommandHandler {

    private static final String BASE_PERM = "minecraft.command.whitelist";

    public static void registerCommands(JavaPlugin plugin) {
        try {
            CommandAPI.unregister("whitelist");
        } catch (Exception e) {
            plugin.getLogger().fine("Whitelist command not previously registered");
        }

        CommandAPICommand whitelist = new CommandAPICommand("whitelist")
                .withSubcommand(new CommandAPICommand("add")
                        .withPermission(BASE_PERM + ".add")
                        .withArguments(new EntitySelectorArgument.OnePlayer("player"))
                        .executesPlayer((player, args) -> {
                            Player target = (Player) args.get("player");
                            if (target == null) {
                                player.sendMessage("§cPlayer not found");
                                return;
                            }
                            Bukkit.getWhitelistedPlayers().add(target);
                            player.sendMessage("§a" + target.getName() + " added to whitelist.");
                        })
                )
                .withSubcommand(new CommandAPICommand("remove")
                        .withPermission(BASE_PERM + ".remove")
                        .withArguments(new TextArgument("player"))
                        .executesPlayer((player, args) -> {
                            String targetName = (String) args.get("player");
                            if (targetName == null || targetName.isEmpty()) {
                                player.sendMessage("§cPlayer name is required");
                                return;
                            }
                            org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                            Bukkit.getWhitelistedPlayers().remove(target);
                            player.sendMessage("§a" + targetName + " removed from whitelist.");
                        })
                )
                .withSubcommand(new CommandAPICommand("on")
                        .withPermission(BASE_PERM + ".on")
                        .executesPlayer((player, args) -> {
                            Bukkit.setWhitelist(true);
                            player.sendMessage("§aWhitelist enabled.");
                        })
                )
                .withSubcommand(new CommandAPICommand("off")
                        .withPermission(BASE_PERM + ".off")
                        .executesPlayer((player, args) -> {
                            Bukkit.setWhitelist(false);
                            player.sendMessage("§aWhitelist disabled.");
                        })
                );

        whitelist.register();
    }
}
