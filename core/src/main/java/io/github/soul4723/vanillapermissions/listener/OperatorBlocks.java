package io.github.soul4723.vanillapermissions.listener;

import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.entity.Player;

/** Shared explicit-deny decision for operator blocks; adapters identify the block and interaction. */
public final class OperatorBlocks {

    private OperatorBlocks() {}

    public static boolean denied(Player player, String block, String action) {
        if (block == null) return false;
        if (PermissionManager.hasPermissionOrFallback(player, permission(block, action), true)) return false;
        player.sendMessage("§c" + message(block, action));
        return true;
    }

    /** The chat refusal, e.g. {@code You can't open command blocks.}; the block's plural never comes from appending "s". */
    static String message(String block, String action) {
        return "You can't " + (action.equals("view") ? "open" : action) + " " + plural(block) + ".";
    }

    private static String plural(String block) {
        switch (block) {
            case "command_block": return "command blocks";
            case "structure_block": return "structure blocks";
            case "jigsaw": return "jigsaw blocks";
            case "test_block": return "test blocks";
            case "test_instance_block": return "test instance blocks";
            default: return block.replace('_', ' ') + " blocks";
        }
    }

    public static String permission(String block, String action) {
        return "minecraft.operator_block." + block + "." + action;
    }
}
