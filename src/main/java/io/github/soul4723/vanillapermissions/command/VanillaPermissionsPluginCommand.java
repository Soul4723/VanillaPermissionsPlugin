package io.github.soul4723.vanillapermissions.command;

import io.github.soul4723.vanillapermissions.VanillaPermissionsPlugin;
import io.github.soul4723.vanillapermissions.util.NativeCommandPermissions;
import io.github.soul4723.vanillapermissions.util.PermissionManager;
import io.github.soul4723.vanillapermissions.util.PermissionProvider;
import io.github.soul4723.vanillapermissions.util.SupportedVersions;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;

/** {@code /vanillapermissions}: inspect how this plugin's nodes resolve. */
public final class VanillaPermissionsPluginCommand implements TabExecutor {

    public static final String ADMIN_PERMISSION = "vanillapermissions.admin";

    private static final List<String> SUBCOMMANDS = List.of("check", "debug");
    private static final List<String> SUMMARY_NODES = List.of(
        "minecraft.selector",
        "minecraft.command.gamemode",
        "minecraft.command.teleport",
        "minecraft.command.tp",
        "minecraft.bypass.whitelist",
        "minecraft.bypass.player-limit");
    private static final List<String> NODE_SUGGESTIONS = List.of(
        "minecraft.command.", "minecraft.selector", "minecraft.selector.self.", "minecraft.selector.player.",
        "minecraft.selector.entity.", "minecraft.selector.limit.", "minecraft.selector.weight.",
        "minecraft.bypass.", "minecraft.operator_block.", "minecraft.debug_stick.use.");

    /** Paper's own nodes that the docs tell admins to set; Paper registers them, so Paper's default is the unset meaning. */
    private static final Set<String> PAPER_OPERATOR_NODES = Set.of(
        "minecraft.commandblock", "minecraft.debugstick", "minecraft.admin.command_feedback");
    private static final Set<String> PLUGIN_OPERATOR_NODES = Set.of("vanillapermissions.use", ADMIN_PERMISSION);

    private static final String NOT_CHECKED = " §7(not a node this plugin checks)";

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
        String requires = sender.hasPermission(ADMIN_PERMISSION) ? "" : " §7(requires " + ADMIN_PERMISSION + ")";
        sender.sendMessage("§e/" + label + " check <player> [node] §7- show how a node resolves for an online player" + requires);
        sender.sendMessage("§e/" + label + " debug §7- show plugin, server and permissions plugin status" + requires);
    }

    private void check(CommandSender sender, String playerName, String node) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage("§cPlayer " + playerName + " is not online. Only online players can be checked, in their current context.");
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
            return "§7unset (" + unsetMeaning(node) + ")";
        }
        String meaning = unsetMeaning(node);
        return switch (PermissionManager.queryPermission(target, node)) {
            case TRUE -> "§atrue" + (meaning == null ? NOT_CHECKED : "");
            case FALSE -> "§cfalse" + (meaning == null ? NOT_CHECKED : "");
            case UNDEFINED -> meaning == null ? "§7unset" + NOT_CHECKED : "§7unset (" + meaning + ")";
        };
    }

    /** What an unset value means for the node's family, or {@code null} if neither this plugin nor Paper's documented nodes cover it. */
    static String unsetMeaning(String node) {
        if (node.startsWith("minecraft.selector.limit.")) return "no limit";
        if (node.startsWith("minecraft.selector.weight.")) return "no weight check";
        if (node.startsWith("minecraft.selector.self.") || node.startsWith("minecraft.selector.player.")
                || node.startsWith("minecraft.selector.entity.")) return "allowed";
        if (node.startsWith("minecraft.bypass.")) return "no bypass";
        if (node.startsWith("minecraft.command.") || node.equals("minecraft.selector")
                || node.startsWith("minecraft.operator_block.") || node.startsWith("minecraft.debug_stick.use.")) {
            return "vanilla behaviour applies";
        }
        if (PAPER_OPERATOR_NODES.contains(node)) return "Paper's default: operators";
        if (node.equals("minecraft.debugstick.always")) return "Paper's default: nobody";
        if (PLUGIN_OPERATOR_NODES.contains(node)) return "this plugin's default: operators";
        return null;
    }

    private void sendDebug(CommandSender sender) {
        sender.sendMessage("§6VanillaPermissionsPlugin " + plugin.getPluginMeta().getVersion());
        String minecraft = Bukkit.getMinecraftVersion();
        sender.sendMessage("§7Server: §f" + Bukkit.getName() + " " + minecraft
            + (SupportedVersions.isVerified(minecraft) ? " §a(tested)"
                : " §e(untested; tested on Paper " + SupportedVersions.range() + ")"));

        PermissionProvider provider = PermissionManager.getProvider();
        sender.sendMessage("§7Permissions: " + provider.describePermissions());
        sender.sendMessage("§7Selector limits and weights: " + provider.describeMeta());
        int nodes = NativeCommandPermissions.installedNodeCount();
        sender.sendMessage("§7Vanilla command nodes with permission checks: "
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
