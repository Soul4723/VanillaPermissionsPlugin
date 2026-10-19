package io.github.soul4723.vanillapermissions.command;

import io.github.soul4723.vanillapermissions.VanillaPermissionsPlugin;
import io.github.soul4723.vanillapermissions.util.LuckPermsHook;
import io.github.soul4723.vanillapermissions.util.NativeCommandPermissions;
import io.github.soul4723.vanillapermissions.util.PermissionManager;
import net.luckperms.api.util.Tristate;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

/** {@code /vanillapermissions}: inspect how LuckPerms resolves this plugin's nodes. */
public final class VanillaPermissionsPluginCommand implements TabExecutor {

    public static final String ADMIN_PERMISSION = "vanillapermissions.admin";

    private static final List<String> SUBCOMMANDS = List.of("check", "debug");
    private static final List<String> SUMMARY_NODES = List.of(
        "minecraft.selector",
        "minecraft.command.gamemode",
        "minecraft.command.teleport",
        "minecraft.bypass.whitelist",
        "minecraft.bypass.player-limit");
    private static final List<String> NODE_SUGGESTIONS = List.of(
        "minecraft.command.", "minecraft.selector", "minecraft.selector.self.", "minecraft.selector.player.",
        "minecraft.selector.entity.", "minecraft.selector.limit.", "minecraft.selector.weight.",
        "minecraft.bypass.", "minecraft.operator_block.", "minecraft.debug_stick.use.block");

    private final VanillaPermissionsPlugin plugin;

    private VanillaPermissionsPluginCommand(VanillaPermissionsPlugin plugin) {
        this.plugin = plugin;
    }

    public static void register(VanillaPermissionsPlugin plugin) {
        PluginCommand command = plugin.getCommand("vanillapermissions");
        if (command == null) throw new IllegalStateException("plugin.yml does not declare /vanillapermissions");
        VanillaPermissionsPluginCommand executor = new VanillaPermissionsPluginCommand(plugin);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !SUBCOMMANDS.contains(args[0].toLowerCase(Locale.ROOT))) {
            sendHelp(sender, label);
            return true;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cYou do not have permission to use /" + label + " " + subcommand
                + " (requires " + ADMIN_PERMISSION + ").");
            return true;
        }
        if (subcommand.equals("debug")) {
            sendDebug(sender);
        } else if (args.length < 2 || args.length > 3) {
            sender.sendMessage("§cUsage: /" + label + " check <player> [node]");
        } else {
            check(sender, args[1], args.length == 3 ? args[2] : null);
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage("§6VanillaPermissionsPlugin " + plugin.getPluginMeta().getVersion());
        sender.sendMessage("§e/" + label + " check <player> [node] §7- show how LuckPerms resolves a node for an online player");
        sender.sendMessage("§e/" + label + " debug §7- show plugin, server and LuckPerms status");
    }

    private void check(CommandSender sender, String playerName, String node) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage("§cPlayer " + playerName + " is not online. Nodes are checked in the player's current LuckPerms context.");
            return;
        }
        if (node != null) {
            sender.sendMessage("§6" + target.getName() + "§7: §f" + node + " §7= " + describe(target, node.toLowerCase(Locale.ROOT)));
            return;
        }
        sender.sendMessage("§6Nodes for " + target.getName() + " §7(operator: " + (target.isOp() ? "§ayes" : "§fno") + "§7)");
        for (String summaryNode : SUMMARY_NODES) {
            sender.sendMessage("§7  " + summaryNode + " = " + describe(target, summaryNode));
        }
        sender.sendMessage("§7Check any node with §e/vanillapermissions check " + target.getName() + " <node>");
    }

    private static String describe(Player target, String node) {
        boolean limit = node.startsWith("minecraft.selector.limit.");
        if (limit || node.startsWith("minecraft.selector.weight.")) {
            OptionalInt value = PermissionManager.getIntegerMeta(target.getUniqueId(), target, node);
            if (value.isPresent()) return "§f" + value.getAsInt() + " §7(meta)";
            return "§7unset (" + (limit ? "no limit" : "no weight check") + ")";
        }
        Tristate value = PermissionManager.queryPermission(target, node);
        return switch (value) {
            case TRUE -> "§atrue";
            case FALSE -> "§cfalse";
            case UNDEFINED -> "§7unset (vanilla behaviour applies)";
        };
    }

    private void sendDebug(CommandSender sender) {
        sender.sendMessage("§6VanillaPermissionsPlugin " + plugin.getPluginMeta().getVersion());
        String minecraft = Bukkit.getMinecraftVersion();
        String tested = VanillaPermissionsPlugin.TESTED_MINECRAFT_VERSION;
        sender.sendMessage("§7Server: §f" + Bukkit.getName() + " " + minecraft
            + (tested.equals(minecraft) ? " §a(tested)" : " §e(untested; only Paper " + tested + " is tested)"));

        LuckPermsHook hook = PermissionManager.getLuckPermsHook();
        if (hook != null && hook.isEnabled()) {
            sender.sendMessage("§7LuckPerms: §aconnected §7(" + hook.getLuckPerms().getPluginMetadata().getVersion() + ")");
        } else {
            sender.sendMessage("§7LuckPerms: §cnot connected");
        }
        int nodes = NativeCommandPermissions.installedNodeCount();
        sender.sendMessage("§7Vanilla command nodes with LuckPerms checks: "
            + (nodes > 0 ? "§f" + nodes : "§c0 (see the startup log)"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) return List.of();
        return switch (args.length) {
            case 1 -> matching(SUBCOMMANDS, args[0]);
            case 2 -> args[0].equalsIgnoreCase("check")
                ? matching(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1])
                : List.of();
            case 3 -> args[0].equalsIgnoreCase("check") ? matching(NODE_SUGGESTIONS, args[2]) : List.of();
            default -> List.of();
        };
    }

    private static List<String> matching(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
