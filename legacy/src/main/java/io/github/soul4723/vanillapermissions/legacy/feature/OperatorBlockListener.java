package io.github.soul4723.vanillapermissions.legacy.feature;

import io.github.soul4723.vanillapermissions.listener.OperatorBlocks;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.lang.reflect.Method;

/**
 * {@code minecraft.operator_block.<block>.<place|break|view>} before 1.13, with the rules and messages of the 1.13+
 * {@code brigadier} {@code OperatorBlockListener}: vanilla still requires op and creative mode, so only an explicit
 * false takes access away.
 *
 * <p>Blocks are matched by {@code Material} name so the class links against the 1.8.8 API: {@code COMMAND} on every
 * version, {@code COMMAND_REPEATING}, {@code COMMAND_CHAIN} and {@code STRUCTURE_BLOCK} where they exist (1.9+; structure
 * blocks are usable from 1.10). There is no jigsaw before 1.14. 1.8.8 has no off hand and no
 * {@code PlayerInteractEvent#getHand()}, so every click counts as the main hand there.
 *
 * <p>"view" only cancels the server side of the click. The 1.8.8 command block editor and the 1.9+
 * structure block editor open client-side anyway (only the 1.9+ command block screen really stays shut), and that the
 * command block edit packet ({@code MC|AdvCdm} on 1.8, {@code MC|AdvCmd} on 1.9+) still goes through for an operator
 * in creative; nothing here filters plugin messages.
 */
final class OperatorBlockListener implements Listener {

    /** {@code PlayerInteractEvent#getHand()}, from 1.9; {@code null} on 1.8.8. */
    private static final Method GET_HAND = handMethod();

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (denied(event.getPlayer(), event.getBlock().getType().name(), "place")) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (denied(event.getPlayer(), event.getBlock().getType().name(), "break")) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        if (!opensScreen(hand(event), player.getGameMode(), player.isSneaking(), event.hasItem())) return;
        if (denied(player, event.getClickedBlock().getType().name(), "view")) event.setCancelled(true);
    }

    /**
     * Whether vanilla would open the block's screen for this click: creative, the main hand ({@code "HAND"}), and not
     * sneaking with an item. Every other click is left alone so blocks can still be placed against these blocks.
     */
    static boolean opensScreen(String hand, GameMode gameMode, boolean sneaking, boolean hasItem) {
        return "HAND".equals(hand) && gameMode == GameMode.CREATIVE && !(sneaking && hasItem);
    }

    static boolean denied(Player player, String materialName, String action) {
        return OperatorBlocks.denied(player, blockName(materialName), action);
    }

    /** The node's block name for a pre-1.13 {@code Material} name, or {@code null} if it is not an operator block. */
    static String blockName(String materialName) {
        switch (materialName) {
            case "COMMAND":
            case "COMMAND_REPEATING":
            case "COMMAND_CHAIN":
                return "command_block";
            case "STRUCTURE_BLOCK":
                return "structure_block";
            default:
                return null;
        }
    }

    /** The enum constant name of the clicking hand ({@code HAND} or {@code OFF_HAND}); always {@code HAND} on 1.8.8. */
    static String hand(PlayerInteractEvent event) {
        if (GET_HAND == null) return "HAND";
        Object hand;
        try {
            hand = GET_HAND.invoke(event);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "HAND"; // Checking a click too many only cancels it; skipping one would leave the node unenforced.
        }
        return hand instanceof Enum ? ((Enum<?>) hand).name() : null;
    }

    private static Method handMethod() {
        try {
            return PlayerInteractEvent.class.getMethod("getHand");
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
