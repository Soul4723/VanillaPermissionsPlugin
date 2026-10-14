package io.github.soul4723.vanillapermissions.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.AbstractArgument;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.LocationArgument;
import dev.jorel.commandapi.arguments.RotationArgument;
import dev.jorel.commandapi.executors.CommandArguments;
import dev.jorel.commandapi.wrappers.NativeProxyCommandSender;
import dev.jorel.commandapi.wrappers.Rotation;
import io.github.soul4723.vanillapermissions.VanillaPermissionsPlugin;
import io.github.soul4723.vanillapermissions.util.PermissionManager;
import io.github.soul4723.vanillapermissions.util.SelectorPermissionCheck;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Locale;

public final class VanillaPermissionsPluginCommand {

    private static final String TELEPORT = "minecraft.command.teleport";

    private VanillaPermissionsPluginCommand() {}

    public static void registerCommands(VanillaPermissionsPlugin plugin) {
        registerAdminCommands(plugin);
        if (plugin.isFeatureEnabled("command_permissions")) {
            registerTeleportCommands(plugin);
        }
    }

    private static void registerAdminCommands(VanillaPermissionsPlugin plugin) {
        try {
            new CommandAPICommand("vanillapermissions")
                .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, "vanillapermissions.use"))
                .withSubcommand(new CommandAPICommand("reload")
                    .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, "vanillapermissions.reload"))
                    .executes((sender, args) -> {
                        plugin.reloadPluginConfig();
                        sender.sendMessage("§aVanillaPermissionsPlugin configuration reloaded!");
                        sender.sendMessage("§7Some changes require a plugin restart to take effect.");
                    }))
                .withSubcommand(new CommandAPICommand("check")
                    .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, "vanillapermissions.admin"))
                    .withArguments(new EntitySelectorArgument.OnePlayer("player"))
                    .executes((sender, args) -> {
                        Object raw = args.getUnchecked("player");
                        if (!(raw instanceof Player target)) {
                            sender.sendMessage("§cPlayer not found.");
                            return;
                        }

                        sender.sendMessage("§6Permissions for " + target.getName() + ":");
                        String[] permissions = {
                            "vanillapermissions.use",
                            "vanillapermissions.reload",
                            "vanillapermissions.admin",
                            "minecraft.command.gamemode",
                            "minecraft.command.teleport",
                            "minecraft.selector.self.s",
                            "minecraft.operator_block.command_block.place"
                        };
                        for (String permission : permissions) {
                            String status = PermissionManager.hasPermissionOrParent(target, permission) ? "§a✓" : "§c✗";
                            sender.sendMessage("§7  " + status + " §f" + permission);
                        }
                    }))
                .withSubcommand(new CommandAPICommand("debug")
                    .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, "vanillapermissions.admin"))
                    .executes((sender, args) -> {
                        sender.sendMessage("§6=== VanillaPermissionsPlugin Debug Info ===");
                        sender.sendMessage("§7Plugin Version: §f" + plugin.getDescription().getVersion());

                        var hook = PermissionManager.getLuckPermsHook();
                        sender.sendMessage("§7LuckPerms Integration: "
                            + (hook != null && hook.isEnabled() ? "§aEnabled" : "§cDisabled"));
                        sender.sendMessage("§7Feature Status:");
                        for (String feature : new String[] {
                                "command_permissions", "selector_permissions", "bypass_permissions", "admin_permissions"}) {
                            sender.sendMessage("§7  " + feature + ": "
                                + (plugin.isFeatureEnabled(feature) ? "§aEnabled" : "§cDisabled"));
                        }
                    }))
                .register();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register VanillaPermissionsPlugin admin commands: " + e.getMessage());
        }
    }

    private static void registerTeleportCommands(VanillaPermissionsPlugin plugin) {
        try {
            CommandAPI.unregister("tp");
        } catch (Exception ignored) {
        }
        try {
            CommandAPI.unregister("teleport");
        } catch (Exception ignored) {
        }

        boolean selectorPermissions = plugin.isFeatureEnabled("selector_permissions");
        var target = new EntitySelectorArgument.OneEntity("target");
        new CommandAPICommand("tp")
            .withAliases("teleport")
            .withArguments(target)
            .executesNative((proxy, args) -> {
                teleportToEntity(selectorPermissions, proxy, args, target);
            })
            .register();

        var location = new LocationArgument("location");
        var rotation = new RotationArgument("rotation");
        new CommandAPICommand("tp")
            .withAliases("teleport")
            .withArguments(location)
            .withOptionalArguments(rotation)
            .executesNative((proxy, args) -> {
                teleportToLocation(proxy, args);
            })
            .register();

        var targets = new EntitySelectorArgument.ManyEntities("targets");
        var destination = new EntitySelectorArgument.OneEntity("destination");
        new CommandAPICommand("tp")
            .withAliases("teleport")
            .withArguments(targets, destination)
            .executesNative((proxy, args) -> {
                teleportTargetsToEntity(selectorPermissions, proxy, args, targets, destination);
            })
            .register();

        var locationTargets = new EntitySelectorArgument.ManyEntities("targets");
        var targetLocation = new LocationArgument("location");
        var targetRotation = new RotationArgument("rotation");
        new CommandAPICommand("tp")
            .withAliases("teleport")
            .withArguments(locationTargets, targetLocation)
            .withOptionalArguments(targetRotation)
            .executesNative((proxy, args) -> {
                teleportTargetsToLocation(selectorPermissions, proxy, args, locationTargets);
            })
            .register();
    }

    private static void teleportToEntity(boolean selectorPermissions, NativeProxyCommandSender proxy,
                                         CommandArguments args, EntitySelectorArgument.OneEntity targetArgument) {
        CommandSender caller = proxy.getCaller();
        if (!canUse(caller, TELEPORT + ".targets")) return;
        if (!canUseSelector(selectorPermissions, caller, args, targetArgument)) return;
        if (!(proxy.getCallee() instanceof Entity executor)) {
            caller.sendMessage("§cThis command needs an entity executor.");
            return;
        }
        Object parsedTarget = args.getUnchecked("target");
        if (!(parsedTarget instanceof Entity target)) {
            caller.sendMessage("§cNo entity matched the target.");
            return;
        }
        sendTeleportResult(caller, executor.teleport(target), "to " + target.getName());
    }

    private static void teleportToLocation(NativeProxyCommandSender proxy, CommandArguments args) {
        CommandSender caller = proxy.getCaller();
        if (!canUse(caller, TELEPORT + ".location")) return;
        if (!(proxy.getCallee() instanceof Entity executor)) {
            caller.sendMessage("§cThis command needs an entity executor.");
            return;
        }
        Object parsedLocation = args.getUnchecked("location");
        if (!(parsedLocation instanceof Location destination) || !withinWorldBounds(destination)) {
            caller.sendMessage("§cCoordinates out of world bounds.");
            return;
        }
        Location target = withRotation(destination, rotation(args), executor);
        sendTeleportResult(caller, executor.teleport(target), "to " + formatLocation(target));
    }

    private static void teleportTargetsToEntity(boolean selectorPermissions, NativeProxyCommandSender proxy,
                                                CommandArguments args, EntitySelectorArgument.ManyEntities targetsArgument,
                                                EntitySelectorArgument.OneEntity destinationArgument) {
        CommandSender caller = proxy.getCaller();
        if (!canUse(caller, TELEPORT + ".targets.targets")) return;
        if (!canUseSelector(selectorPermissions, caller, args, targetsArgument)
                || !canUseSelector(selectorPermissions, caller, args, destinationArgument)) return;

        Object parsedTargets = args.getUnchecked("targets");
        Object parsedDestination = args.getUnchecked("destination");
        if (!(parsedTargets instanceof Collection<?> targets) || !(parsedDestination instanceof Entity destination)) {
            caller.sendMessage("§cNo valid entity target was found.");
            return;
        }
        if (targets.isEmpty()) {
            caller.sendMessage("§cNo entities matched the target.");
            return;
        }
        int successes = 0;
        for (Object item : targets) {
            if (item instanceof Entity target && target.teleport(destination)) successes++;
        }
        sendTeleportCount(caller, successes, targets.size(), "to " + destination.getName());
    }

    private static void teleportTargetsToLocation(boolean selectorPermissions, NativeProxyCommandSender proxy,
                                                  CommandArguments args,
                                                  EntitySelectorArgument.ManyEntities targetsArgument) {
        CommandSender caller = proxy.getCaller();
        if (!canUse(caller, TELEPORT + ".targets.location")) return;
        if (!canUseSelector(selectorPermissions, caller, args, targetsArgument)) return;

        Object parsedTargets = args.getUnchecked("targets");
        Object parsedLocation = args.getUnchecked("location");
        if (!(parsedTargets instanceof Collection<?> targets) || !(parsedLocation instanceof Location destination)
                || !withinWorldBounds(destination)) {
            caller.sendMessage("§cNo valid entity target or destination was found.");
            return;
        }
        if (targets.isEmpty()) {
            caller.sendMessage("§cNo entities matched the target.");
            return;
        }
        Rotation rotation = rotation(args);
        int successes = 0;
        for (Object item : targets) {
            if (item instanceof Entity target && target.teleport(withRotation(destination, rotation, target))) successes++;
        }
        sendTeleportCount(caller, successes, targets.size(), "to " + formatLocation(destination));
    }

    private static boolean canUse(CommandSender sender, String permission) {
        if (PermissionManager.hasPermissionOrParent(sender, TELEPORT)
                && PermissionManager.hasPermissionOrParent(sender, permission)) return true;
        sender.sendMessage("§cYou don't have permission to use this teleport form.");
        return false;
    }

    private static boolean canUseSelector(boolean selectorPermissions, CommandSender sender,
                                          CommandArguments args, AbstractArgument<?, ?, ?, ?> argument) {
        if (!selectorPermissions || SelectorPermissionCheck.hasPermission(sender, args, argument)) return true;
        sender.sendMessage("§cYou don't have permission to use that selector.");
        return false;
    }

    private static Rotation rotation(CommandArguments args) {
        Object parsed = args.getUnchecked("rotation");
        return parsed instanceof Rotation value ? value : null;
    }

    private static Location withRotation(Location destination, Rotation rotation, Entity target) {
        Location location = destination.clone();
        if (rotation == null) {
            Location current = target.getLocation();
            location.setYaw(current.getYaw());
            location.setPitch(current.getPitch());
        } else {
            location.setYaw(rotation.getNormalizedYaw());
            location.setPitch(rotation.getNormalizedPitch());
        }
        return location;
    }

    private static boolean withinWorldBounds(Location location) {
        return Double.isFinite(location.getX()) && Double.isFinite(location.getY()) && Double.isFinite(location.getZ())
            && Math.abs(location.getX()) <= 30_000_000
            && Math.abs(location.getZ()) <= 30_000_000
            && Math.abs(location.getY()) <= 20_000_000;
    }

    private static void sendTeleportResult(CommandSender sender, boolean success, String destination) {
        sender.sendMessage((success ? "§aTeleported " : "§cCould not teleport ") + destination);
    }

    private static void sendTeleportCount(CommandSender sender, int successes, int total, String destination) {
        if (successes == 0) {
            sender.sendMessage("§cTeleport failed for all " + total + " targets.");
            return;
        }
        sender.sendMessage("§aTeleported " + successes + " of " + total + " targets " + destination + ".");
    }

    private static String formatLocation(Location location) {
        return String.format(Locale.ROOT, "%.2f, %.2f, %.2f", location.getX(), location.getY(), location.getZ());
    }
}
