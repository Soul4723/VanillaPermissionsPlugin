package io.github.soul4723.vanillapermissions.util;

import dev.jorel.commandapi.arguments.AbstractArgument;
import dev.jorel.commandapi.arguments.CommandAPIArgumentType;
import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;

/** Permission check for selector syntax in a parsed CommandAPI entity argument. */
public final class SelectorPermissionCheck {

    private SelectorPermissionCheck() {}

    public static boolean hasPermission(
            CommandSender sender,
            CommandArguments arguments,
            AbstractArgument<?, ?, ?, ?> selectorArgument) {
        if (selectorArgument.getArgumentType() != CommandAPIArgumentType.ENTITY_SELECTOR) {
            throw new IllegalArgumentException("Argument must be an EntitySelectorArgument");
        }

        String permission = permissionForParsedSelector(arguments.getRaw(selectorArgument.getNodeName()));
        return permission == null || PermissionManager.hasPermissionOrParent(sender, permission);
    }

    static String permissionForParsedSelector(String rawArgument) {
        if (rawArgument == null || rawArgument.length() < 2 || rawArgument.charAt(0) != '@') {
            return null;
        }

        return switch (rawArgument.charAt(1)) {
            case 's' -> "minecraft.selector.self.s";
            case 'a', 'p', 'r' -> "minecraft.selector.player." + rawArgument.charAt(1);
            case 'e' -> "minecraft.selector.entity.e";
            default -> null;
        };
    }
}
