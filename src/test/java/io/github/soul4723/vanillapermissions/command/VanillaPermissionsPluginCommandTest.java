package io.github.soul4723.vanillapermissions.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** What an unset node means per family, as documented in the README's resolution table. */
class VanillaPermissionsPluginCommandTest {

    @Test
    void unsetMeaningFollowsTheResolutionTable() {
        for (String node : new String[] {"minecraft.command.gamemode", "minecraft.selector",
                "minecraft.operator_block.place", "minecraft.debug_stick.use.minecraft.oak_stairs"}) {
            assertEquals("vanilla behaviour applies", VanillaPermissionsPluginCommand.unsetMeaning(node), node);
        }
        for (String node : new String[] {"minecraft.selector.self.gamemode.target", "minecraft.selector.player.tp",
                "minecraft.selector.entity.kill.targets"}) {
            assertEquals("allowed", VanillaPermissionsPluginCommand.unsetMeaning(node), node);
        }
        assertEquals("no bypass", VanillaPermissionsPluginCommand.unsetMeaning("minecraft.bypass.whitelist"));
        assertEquals("no limit", VanillaPermissionsPluginCommand.unsetMeaning("minecraft.selector.limit.kill.targets"));
        assertEquals("no weight check", VanillaPermissionsPluginCommand.unsetMeaning("minecraft.selector.weight.kill.targets"));
    }

    @Test
    void nodesTheDocsTellAdminsToSetHaveAnUnsetMeaning() {
        for (String node : new String[] {"minecraft.commandblock", "minecraft.debugstick", "minecraft.admin.command_feedback"}) {
            assertEquals("Paper's default: operators", VanillaPermissionsPluginCommand.unsetMeaning(node), node);
        }
        assertEquals("Paper's default: nobody", VanillaPermissionsPluginCommand.unsetMeaning("minecraft.debugstick.always"));
        for (String node : new String[] {"vanillapermissions.use", "vanillapermissions.admin"}) {
            assertEquals("this plugin's default: operators", VanillaPermissionsPluginCommand.unsetMeaning(node), node);
        }
    }

    @Test
    void unknownNodesAreNotChecked() {
        assertNull(VanillaPermissionsPluginCommand.unsetMeaning("minecraft.commandblocks"));
        assertNull(VanillaPermissionsPluginCommand.unsetMeaning("minecraft.debugstick.sometimes"));
        assertNull(VanillaPermissionsPluginCommand.unsetMeaning("vanillapermissions.root"));
        assertNull(VanillaPermissionsPluginCommand.unsetMeaning("minecraft.selectors"));
        assertNull(VanillaPermissionsPluginCommand.unsetMeaning("minecraft.comand.gamemode"));
    }
}
