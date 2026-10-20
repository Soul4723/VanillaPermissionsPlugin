package io.github.soul4723.vanillapermissions.listener;

import io.github.soul4723.vanillapermissions.util.TestLuckPerms;
import net.luckperms.api.util.Tristate;
import org.bukkit.GameMode;
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
    private GameMode gameMode = GameMode.CREATIVE;
    private boolean sneaking;
    private final Player player = proxy(Player.class, (p, m, a) -> switch (m.getName()) {
        case "getUniqueId" -> id;
        case "getGameMode" -> gameMode;
        case "isSneaking" -> sneaking;
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
        assertEquals(List.of("§cYou do not have permission to open command blocks."), messages);
        assertFalse(viewCancelled(Material.STRUCTURE_BLOCK), "Denial leaked to another block type");
    }

    @Test
    void viewDenialOnlyAppliesWhereVanillaWouldOpenTheScreen() {
        luckPerms.nodes.put("minecraft.operator_block.command_block.view", Tristate.FALSE);
        assertFalse(view(Material.COMMAND_BLOCK, null, EquipmentSlot.OFF_HAND).isCancelled(), "Off-hand click denied twice");
        sneaking = true;
        assertFalse(view(Material.COMMAND_BLOCK, new ItemStack(Material.STONE), EquipmentSlot.HAND).isCancelled(),
            "Sneak-placing against a command block was blocked");
        sneaking = false;
        gameMode = GameMode.SURVIVAL;
        assertFalse(viewCancelled(Material.COMMAND_BLOCK), "Survival click was blocked although vanilla opens nothing");
        assertTrue(messages.isEmpty(), "Denial message sent for a click that opens nothing");
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

        assertEquals("minecraft.debug_stick.use.minecraft.stone", DebugStickListener.permission(Material.STONE));
        luckPerms.nodes.put("minecraft.debug_stick.use.minecraft.stone", Tristate.FALSE);
        for (EquipmentSlot hand : new EquipmentSlot[]{EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
            PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, stick, block, BlockFace.UP, hand);
            listener.onPlayerInteractWithDebugStick(event);
            assertTrue(event.isCancelled(), "Debug stick denial bypassed with " + hand);
        }
        PlayerInteractEvent other = new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK, stick, block(Material.OAK_TRAPDOOR), BlockFace.UP);
        listener.onPlayerInteractWithDebugStick(other);
        assertFalse(other.isCancelled(), "Debug stick denial leaked to another block");
    }

    private boolean viewCancelled(Material material) {
        return view(material, null, EquipmentSlot.HAND).isCancelled();
    }

    private PlayerInteractEvent view(Material material, ItemStack item, EquipmentSlot hand) {
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, item, block(material), BlockFace.UP, hand);
        new OperatorBlockListener().onPlayerInteract(event);
        return event;
    }

    private static Block block(Material material) {
        return proxy(Block.class, (p, m, a) -> {
            assertEquals("getType", m.getName());
            return material;
        });
    }
}
