package io.github.soul4723.vanillapermissions.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.executors.CommandArguments;
import dev.jorel.commandapi.executors.NativeCommandExecutor;
import dev.jorel.commandapi.wrappers.NativeProxyCommandSender;
import io.github.soul4723.vanillapermissions.VanillaPermissionsPlugin;
import io.github.soul4723.vanillapermissions.util.PermissionManager;
import io.github.soul4723.vanillapermissions.util.SelectorPermissionCheck;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class GameModeCommandHandler {

    private static final String BASE_PERMISSION = "minecraft.command.gamemode";
    private static final GameMode[] MODES = GameMode.values();

    private GameModeCommandHandler() {}

    public static void registerCommands(JavaPlugin plugin) {
        try {
            CommandAPI.unregister("gamemode");
        } catch (Exception e) {
            plugin.getLogger().fine("Gamemode command not previously registered");
        }

        boolean selectorPermissions = ((VanillaPermissionsPlugin) plugin).isFeatureEnabled("selector_permissions");
        CommandAPICommand gamemode = new CommandAPICommand("gamemode")
                .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, BASE_PERMISSION));
        for (GameMode mode : MODES) {
            String modeName = mode.name().toLowerCase(Locale.ROOT);
            String modePermission = BASE_PERMISSION + "." + modeName;
            EntitySelectorArgument.ManyPlayers targets = new EntitySelectorArgument.ManyPlayers("targets");
            gamemode.withSubcommand(new CommandAPICommand(modeName)
                    .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, modePermission))
                    .withOptionalArguments(targets)
                    .executesNative((NativeCommandExecutor) (proxy, args) -> execute(proxy, args, targets, mode, selectorPermissions)));
        }
        gamemode.register();
    }

    private static void execute(NativeProxyCommandSender proxy, CommandArguments args,
                                EntitySelectorArgument.ManyPlayers targetsArgument, GameMode mode,
                                boolean selectorPermissions) {
        CommandSender caller = proxy.getCaller();
        String modePermission = BASE_PERMISSION + "." + mode.name().toLowerCase(Locale.ROOT);
        if (!PermissionManager.hasPermissionOrParent(caller, BASE_PERMISSION)) {
            caller.sendMessage("§cYou don't have permission to use /gamemode.");
            return;
        }
        if (!PermissionManager.hasPermissionOrParent(caller, modePermission)) {
            caller.sendMessage("§cYou don't have permission to set gamemode " + mode.name().toLowerCase(Locale.ROOT) + ".");
            return;
        }

        Object parsedTargets = args.get("targets");
        if (selectorPermissions && parsedTargets != null
                && !SelectorPermissionCheck.hasPermission(caller, args, targetsArgument)) {
            caller.sendMessage("§cYou don't have permission to use that selector with /gamemode.");
            return;
        }

        Collection<Player> targets;
        if (parsedTargets == null) {
            if (!(proxy.getCallee() instanceof Player defaultTarget)) {
                caller.sendMessage("§cSpecify one or more players when running /gamemode from console.");
                return;
            }
            targets = List.of(defaultTarget);
        } else if (parsedTargets instanceof Collection<?> parsedPlayers) {
            if (parsedPlayers.stream().anyMatch(target -> !(target instanceof Player))) {
                caller.sendMessage("§cInvalid player target.");
                return;
            }
            @SuppressWarnings("unchecked")
            Collection<Player> selectedPlayers = (Collection<Player>) parsedPlayers;
            targets = selectedPlayers;
        } else {
            caller.sendMessage("§cInvalid player target.");
            return;
        }

        applyMode(caller, targets, mode);
    }

    private static void applyMode(CommandSender caller, Collection<Player> targets, GameMode mode) {
        if (targets.isEmpty()) {
            caller.sendMessage("§cNo players matched the target.");
            return;
        }

        String modeName = mode.name().toLowerCase(Locale.ROOT);
        String otherPermission = BASE_PERMISSION + ".other";
        String modeOtherPermission = BASE_PERMISSION + "." + modeName + ".other";
        for (Player target : targets) {
            boolean self = caller instanceof Player player
                    && player.getUniqueId().equals(target.getUniqueId());
            if (!self && (!PermissionManager.hasPermissionOrParent(caller, otherPermission)
                    || !PermissionManager.hasPermissionOrParent(caller, modeOtherPermission))) {
                caller.sendMessage("§cYou don't have permission to set other players' gamemode " + modeName + ".");
                return;
            }
        }

        for (Player target : targets) {
            target.setGameMode(mode);
        }
        caller.sendMessage("§aSet " + targets.size() + " player" + (targets.size() == 1 ? "'s" : "s'")
                + " gamemode to " + modeName + ".");
    }
}
