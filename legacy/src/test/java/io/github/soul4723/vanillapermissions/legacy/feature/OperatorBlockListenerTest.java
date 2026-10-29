package io.github.soul4723.vanillapermissions.legacy.feature;

import io.github.soul4723.vanillapermissions.util.TestLuckPerms;
import net.luckperms.api.util.Tristate;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.github.soul4723.vanillapermissions.util.TestLuckPerms.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Operator block nodes only take access away; unset keeps vanilla's op requirement. */
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
    private final OperatorBlockListener listener = new OperatorBlockListener();
    private TestLuckPerms luckPerms;

    @BeforeEach
    void installLuckPerms() throws Exception {
        luckPerms = new TestLuckPerms();
    }

    @AfterEach
    void restoreLuckPerms() throws Exception {
        luckPerms.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMMAND", "COMMAND_REPEATING", "COMMAND_CHAIN"})
    void commandBlockMaterials(String material) {
        assertEquals("command_block", OperatorBlockListener.blockName(material));
    }

    @Test
    void structureBlockMaterial() {
        assertEquals("structure_block", OperatorBlockListener.blockName("STRUCTURE_BLOCK"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"STONE", "COMMAND_MINECART", "JIGSAW", "COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK"})
    void otherMaterialsAreNotOperatorBlocks(String material) {
        assertNull(OperatorBlockListener.blockName(material));
    }

    @Test
    void opensScreenTruthTable() {
        assertTrue(OperatorBlockListener.opensScreen("HAND", GameMode.CREATIVE, false, false));
        assertTrue(OperatorBlockListener.opensScreen("HAND", GameMode.CREATIVE, false, true));
        assertTrue(OperatorBlockListener.opensScreen("HAND", GameMode.CREATIVE, true, false));
        assertFalse(OperatorBlockListener.opensScreen("HAND", GameMode.CREATIVE, true, true));
        assertFalse(OperatorBlockListener.opensScreen("OFF_HAND", GameMode.CREATIVE, false, false));
        assertFalse(OperatorBlockListener.opensScreen(null, GameMode.CREATIVE, false, false));
        for (GameMode mode : new GameMode[]{GameMode.SURVIVAL, GameMode.ADVENTURE, GameMode.SPECTATOR}) {
            assertFalse(OperatorBlockListener.opensScreen("HAND", mode, false, false), mode.name());
        }
    }

    @Test
    void handIsTheMainHandOn188() {
        PlayerInteractEvent event = click(Action.RIGHT_CLICK_BLOCK, null, Material.COMMAND);
        assertEquals("HAND", OperatorBlockListener.hand(event));
    }

    @Test
    void placeIsDeniedByAnExplicitFalse() {
        luckPerms.nodes.put("minecraft.operator_block.command_block.place", Tristate.FALSE);
        BlockPlaceEvent event = place(Material.COMMAND);
        listener.onBlockPlace(event);
        assertTrue(event.isCancelled());
        assertEquals(List.of("§cYou can't place command blocks."), messages);
    }

    @Test
    void placeIsAllowedWhenUnset() {
        BlockPlaceEvent event = place(Material.COMMAND);
        listener.onBlockPlace(event);
        assertFalse(event.isCancelled());
        assertTrue(messages.isEmpty());
    }

    @Test
    void placeDenialDoesNotLeakToOtherBlocks() {
        luckPerms.nodes.put("minecraft.operator_block.command_block.place", Tristate.FALSE);
        BlockPlaceEvent event = place(Material.STONE);
        listener.onBlockPlace(event);
        assertFalse(event.isCancelled());
        assertTrue(messages.isEmpty());
    }

    @Test
    void breakIsDeniedByAnExplicitFalse() {
        luckPerms.nodes.put("minecraft.operator_block.command_block.break", Tristate.FALSE);
        BlockBreakEvent event = new BlockBreakEvent(block(Material.COMMAND), player);
        listener.onBlockBreak(event);
        assertTrue(event.isCancelled());
        assertEquals(List.of("§cYou can't break command blocks."), messages);
    }

    @Test
    void breakIsAllowedWhenUnsetOrForAnotherAction() {
        BlockBreakEvent unset = new BlockBreakEvent(block(Material.COMMAND), player);
        listener.onBlockBreak(unset);
        assertFalse(unset.isCancelled());

        luckPerms.nodes.put("minecraft.operator_block.command_block.place", Tristate.FALSE);
        BlockBreakEvent other = new BlockBreakEvent(block(Material.COMMAND), player);
        listener.onBlockBreak(other);
        assertFalse(other.isCancelled(), "Place denial leaked to breaking");
        assertTrue(messages.isEmpty());
    }

    @Test
    void rightClickInCreativeIsDeniedWithTheOpenMessage() {
        luckPerms.nodes.put("minecraft.operator_block.command_block.view", Tristate.FALSE);
        PlayerInteractEvent event = click(Action.RIGHT_CLICK_BLOCK, null, Material.COMMAND);
        listener.onPlayerInteract(event);
        assertTrue(event.isCancelled());
        assertEquals(List.of("§cYou can't open command blocks."), messages);
    }

    @Test
    void rightClickIsAllowedWhenUnset() {
        PlayerInteractEvent event = click(Action.RIGHT_CLICK_BLOCK, null, Material.COMMAND);
        listener.onPlayerInteract(event);
        assertFalse(event.isCancelled());
        assertTrue(messages.isEmpty());
    }

    @Test
    void clicksThatOpenNothingAreLeftAlone() {
        luckPerms.nodes.put("minecraft.operator_block.command_block.view", Tristate.FALSE);

        PlayerInteractEvent left = click(Action.LEFT_CLICK_BLOCK, null, Material.COMMAND);
        listener.onPlayerInteract(left);
        assertFalse(left.isCancelled(), "Left click denied");

        // Bukkit starts a click without a block as cancelled for the block, so only check that the event is not touched.
        PlayerInteractEvent air = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, null, null, BlockFace.UP);
        boolean before = air.isCancelled();
        listener.onPlayerInteract(air);
        assertEquals(before, air.isCancelled());

        sneaking = true;
        PlayerInteractEvent sneakPlace = click(Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.STONE), Material.COMMAND);
        listener.onPlayerInteract(sneakPlace);
        assertFalse(sneakPlace.isCancelled(), "Sneak-placing against a command block was blocked");
        sneaking = false;

        for (GameMode mode : new GameMode[]{GameMode.SURVIVAL, GameMode.ADVENTURE, GameMode.SPECTATOR}) {
            gameMode = mode;
            PlayerInteractEvent event = click(Action.RIGHT_CLICK_BLOCK, null, Material.COMMAND);
            listener.onPlayerInteract(event);
            assertFalse(event.isCancelled(), mode + " click was blocked");
        }
        assertTrue(messages.isEmpty(), "Denial message sent for a click that opens nothing");
    }

    @Test
    void sneakingWithoutAnItemStillOpensTheScreen() {
        luckPerms.nodes.put("minecraft.operator_block.command_block.view", Tristate.FALSE);
        sneaking = true;
        PlayerInteractEvent event = click(Action.RIGHT_CLICK_BLOCK, null, Material.COMMAND);
        listener.onPlayerInteract(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void ordinaryBlocksAndOtherNodesAreIgnored() {
        luckPerms.nodes.put("minecraft.operator_block.command_block.view", Tristate.FALSE);
        PlayerInteractEvent chest = click(Action.RIGHT_CLICK_BLOCK, null, Material.CHEST);
        listener.onPlayerInteract(chest);
        assertFalse(chest.isCancelled());

        luckPerms.nodes.clear();
        luckPerms.nodes.put("minecraft.operator_block.structure_block.view", Tristate.FALSE);
        PlayerInteractEvent command = click(Action.RIGHT_CLICK_BLOCK, null, Material.COMMAND);
        listener.onPlayerInteract(command);
        assertFalse(command.isCancelled(), "Structure block denial leaked to command blocks");
        assertTrue(messages.isEmpty());
    }

    private BlockPlaceEvent place(Material material) {
        Block placed = block(material);
        return new BlockPlaceEvent(placed, null, placed, null, player, true);
    }

    private PlayerInteractEvent click(Action action, ItemStack item, Material material) {
        return new PlayerInteractEvent(player, action, item, block(material), BlockFace.UP);
    }

    private static Block block(Material material) {
        return proxy(Block.class, (p, m, a) -> {
            assertEquals("getType", m.getName());
            return material;
        });
    }
}
