package io.github.soul4723.vanillapermissions.listener;

import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Lets LuckPerms take operator block access away. Vanilla still requires op and creative mode
 * (or Paper's own minecraft.commandblock for command blocks), so an unset node keeps vanilla
 * behaviour and only an explicit false changes anything.
 */
public class OperatorBlockListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (denied(event.getPlayer(), event.getBlock().getType(), "place")) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (denied(event.getPlayer(), event.getBlock().getType(), "break")) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        // Vanilla only opens these screens for creative players clicking with the main hand, and not when they
        // sneak with an item; leave every other click alone so blocks can still be placed against them.
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || player.getGameMode() != GameMode.CREATIVE
                || player.isSneaking() && event.hasItem()) return;
        if (denied(player, event.getClickedBlock().getType(), "view")) event.setCancelled(true);
    }

    private static boolean denied(Player player, Material material, String action) {
        String block = blockName(material);
        if (block == null) return false;
        if (PermissionManager.hasPermissionOrFallback(player, permission(block, action), true)) return false;
        String verb = action.equals("view") ? "open" : action;
        player.sendMessage("§cYou do not have permission to " + verb + " " + block.replace('_', ' ') + "s.");
        return true;
    }

    static String permission(String block, String action) {
        return "minecraft.operator_block." + block + "." + action;
    }

    static String blockName(Material material) {
        return switch (material) {
            case COMMAND_BLOCK, REPEATING_COMMAND_BLOCK, CHAIN_COMMAND_BLOCK -> "command_block";
            case JIGSAW -> "jigsaw";
            case STRUCTURE_BLOCK -> "structure_block";
            default -> null;
        };
    }
}
