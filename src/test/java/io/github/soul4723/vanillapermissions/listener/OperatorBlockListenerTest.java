package io.github.soul4723.vanillapermissions.listener;

import io.github.soul4723.vanillapermissions.util.TestLuckPerms;
import net.luckperms.api.util.Tristate;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.github.soul4723.vanillapermissions.util.TestLuckPerms.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Operator block and debug stick nodes only take access away; unset keeps vanilla's op requirement. */
class OperatorBlockListenerTest {
    private final UUID id = UUID.randomUUID();
    private final List<String> messages = new ArrayList<>();
    private final Player player = proxy(Player.class, (p, m, a) -> switch (m.getName()) {
        case "getUniqueId" -> id;
        case "sendMessage" -> messages.add((String) a[0]);
        default -> throw new AssertionError("Unexpected Player call: " + m);
    });
    private TestLuckPerms luckPerms;

    @BeforeEach
    void installLuckPerms() throws Exception {
        luckPerms = new TestLuckPerms();
    }

    @AfterEach
    void restoreLuckPerms() throws Exception {
        luckPerms.close();
    }

    @Test
    void permissionNamesMatchUpstream() {
        assertEquals("command_block", OperatorBlockListener.blockName(Material.REPEATING_COMMAND_BLOCK));
        assertEquals("command_block", OperatorBlockListener.blockName(Material.CHAIN_COMMAND_BLOCK));
        assertEquals("jigsaw", OperatorBlockListener.blockName(Material.JIGSAW));
        assertEquals("structure_block", OperatorBlockListener.blockName(Material.STRUCTURE_BLOCK));
        assertNull(OperatorBlockListener.blockName(Material.STONE));
        assertEquals("minecraft.operator_block.command_block.view", OperatorBlockListener.permission("command_block", "view"));
    }

    @Test
    void unsetNodeKeepsVanillaBehaviour() {
        assertFalse(viewCancelled(Material.COMMAND_BLOCK), "Unset node locked operators out of command blocks");
        assertTrue(messages.isEmpty());
    }

    @Test
    void explicitDenialCancelsWithMessage() {
        luckPerms.nodes.put("minecraft.operator_block.command_block.view", Tristate.FALSE);
        assertTrue(viewCancelled(Material.CHAIN_COMMAND_BLOCK));
        assertEquals(List.of("§cYou do not have permission to view command blocks."), messages);
        assertFalse(viewCancelled(Material.STRUCTURE_BLOCK), "Denial leaked to another block type");
    }

    @Test
    void ordinaryBlocksAreIgnored() {
        luckPerms.nodes.put("minecraft.operator_block.command_block.view", Tristate.FALSE);
        assertFalse(viewCancelled(Material.CHEST));
    }

    @Test
    void debugStickDenialCoversBothHands() {
        DebugStickListener listener = new DebugStickListener();
        Block block = block(Material.STONE);
        ItemStack stick = new ItemStack(Material.DEBUG_STICK);
        PlayerInteractEvent unset = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, stick, block, BlockFace.UP, EquipmentSlot.OFF_HAND);
        listener.onPlayerInteractWithDebugStick(unset);
        assertFalse(unset.isCancelled(), "Unset node locked operators out of the debug stick");

        luckPerms.nodes.put(DebugStickListener.PERMISSION, Tristate.FALSE);
        for (EquipmentSlot hand : new EquipmentSlot[]{EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
            PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, stick, block, BlockFace.UP, hand);
            listener.onPlayerInteractWithDebugStick(event);
            assertTrue(event.isCancelled(), "Debug stick denial bypassed with " + hand);
        }
    }

    private boolean viewCancelled(Material material) {
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block(material), BlockFace.UP);
        new OperatorBlockListener().onPlayerInteract(event);
        return event.isCancelled();
    }

    private static Block block(Material material) {
        return proxy(Block.class, (p, m, a) -> {
            assertEquals("getType", m.getName());
            return material;
        });
    }
}
