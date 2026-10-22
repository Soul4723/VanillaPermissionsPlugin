package io.github.soul4723.vanillapermissions.listener;

import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.entity.Player;

/** Shared explicit-deny decision for operator blocks; adapters identify the block and interaction. */
public final class OperatorBlocks {

    private OperatorBlocks() {}

    public static boolean denied(Player player, String block, String action) {
        if (block == null) return false;
        if (PermissionManager.hasPermissionOrFallback(player, permission(block, action), true)) return false;
        String verb = action.equals("view") ? "open" : action;
        player.sendMessage("§cYou do not have permission to " + verb + " " + block.replace('_', ' ') + "s.");
        return true;
    }

    public static String permission(String block, String action) {
        return "minecraft.operator_block." + block + "." + action;
    }
}
