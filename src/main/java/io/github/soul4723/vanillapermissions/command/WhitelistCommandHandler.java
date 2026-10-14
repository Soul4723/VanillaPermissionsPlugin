package io.github.soul4723.vanillapermissions.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import com.destroystokyo.paper.profile.PlayerProfile;
import dev.jorel.commandapi.arguments.PlayerProfileArgument;
import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.List;
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
                        .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, BASE_PERM + ".add"))
                        .withArguments(new PlayerProfileArgument("player"))
                        .executes((sender, args) -> {
                            @SuppressWarnings("unchecked")
                            List<PlayerProfile> profiles = (List<PlayerProfile>) args.get("player");
                            for (PlayerProfile profile : profiles) {
                                if (profile == null || profile.getId() == null || profile.getName() == null || profile.getName().isBlank()) {
                                    sender.sendMessage("§cInvalid player identity");
                                    continue;
                                }
                                String name = profile.getName();
                                try {
                                    OfflinePlayer target = Bukkit.getOfflinePlayer(profile.getId());
                                    if (target.isWhitelisted()) {
                                        sender.sendMessage("§c" + name + " is already whitelisted.");
                                        continue;
                                    }
                                    target.setWhitelisted(true);
                                    sender.sendMessage(target.isWhitelisted()
                                            ? "§a" + name + " added to whitelist."
                                            : "§cCould not add " + name + " to whitelist.");
                                } catch (RuntimeException e) {
                                    sender.sendMessage("§cCould not add " + name + " to whitelist.");
                                }
                            }
                        })
                )
                .withSubcommand(new CommandAPICommand("remove")
                        .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, BASE_PERM + ".remove"))
                        .withArguments(new PlayerProfileArgument("player"))
                        .executes((sender, args) -> {
                            @SuppressWarnings("unchecked")
                            List<PlayerProfile> profiles = (List<PlayerProfile>) args.get("player");
                            for (PlayerProfile profile : profiles) {
                                if (profile == null || profile.getId() == null || profile.getName() == null || profile.getName().isBlank()) {
                                    sender.sendMessage("§cInvalid player identity");
                                    continue;
                                }
                                String name = profile.getName();
                                try {
                                    OfflinePlayer target = Bukkit.getOfflinePlayer(profile.getId());
                                    if (!target.isWhitelisted()) {
                                        sender.sendMessage("§c" + name + " is not whitelisted.");
                                        continue;
                                    }
                                    target.setWhitelisted(false);
                                    sender.sendMessage(!target.isWhitelisted()
                                            ? "§a" + name + " removed from whitelist."
                                            : "§cCould not remove " + name + " from whitelist.");
                                } catch (RuntimeException e) {
                                    sender.sendMessage("§cCould not remove " + name + " from whitelist.");
                                }
                            }
                        })
                )
                .withSubcommand(new CommandAPICommand("on")
                        .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, BASE_PERM + ".on"))
                        .executes((sender, args) -> {
                            Bukkit.setWhitelist(true);
                            sender.sendMessage("§aWhitelist enabled.");
                        })
                )
                .withSubcommand(new CommandAPICommand("off")
                        .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, BASE_PERM + ".off"))
                        .executes((sender, args) -> {
                            Bukkit.setWhitelist(false);
                            sender.sendMessage("§aWhitelist disabled.");
                        })
                );

        whitelist.register();
    }
}
