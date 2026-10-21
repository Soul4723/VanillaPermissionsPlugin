package io.github.soul4723.vanillapermissions.spigot;

import io.github.soul4723.vanillapermissions.spigot.SpigotNames.LevelShape;
import io.github.soul4723.vanillapermissions.spigot.SpigotNames.NativeClass;
import io.github.soul4723.vanillapermissions.spigot.SpigotNames.NativeMember;
import io.github.soul4723.vanillapermissions.spigot.SpigotNames.Since;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpigotNamesTest {

    private static String name(NativeMember member, String token) {
        return member.name(MappingGeneration.of(token));
    }

    @Test
    void rowsAreOrderedOldestFirstAndStartAt113OrAtPermissionSets() {
        for (NativeClass type : NativeClass.values()) checkRows(type.name(), type.shape, type.rows);
        for (NativeMember member : NativeMember.values()) checkRows(member.name(), member.shape, member.rows);
    }

    private static void checkRows(String what, LevelShape shape, List<Since> rows) {
        String first = shape == LevelShape.PERMISSION_SET ? "v1_21_R7" : "v1_13_R1";
        assertEquals(MappingGeneration.of(first), rows.get(0).from, what + " must start at " + first);
        for (int i = 1; i < rows.size(); i++) {
            assertTrue(rows.get(i - 1).from.compareTo(rows.get(i).from) < 0, what + " rows are not in order");
        }
    }

    @Test
    void namesMatchServerBytecode() {
        assertEquals("m", name(NativeMember.SELECTOR_USES_SELECTOR, "v1_13_R2"));
        assertEquals("checkPermissions", name(NativeMember.SELECTOR_USES_SELECTOR, "v1_16_R3"));
        assertEquals("o", name(NativeMember.SELECTOR_USES_SELECTOR, "v1_17_R1"));
        assertEquals("p", name(NativeMember.SELECTOR_USES_SELECTOR, "v1_19_R3"));
        assertEquals("p", name(NativeMember.SELECTOR_USES_SELECTOR, "v1_20_R3"));
        assertEquals("b", name(NativeMember.ENTITY_ARGUMENT_ENTITIES, "v1_13_R2"));
        assertEquals("multipleEntities", name(NativeMember.ENTITY_ARGUMENT_ENTITIES, "v1_17_R1"));
        assertEquals("b", name(NativeMember.ENTITY_ARGUMENT_ENTITIES, "v1_19_R3"));
        assertEquals("y", name(NativeMember.ENTITY_PERMISSION_LEVEL, "v1_17_R1"));
        assertEquals("B", name(NativeMember.ENTITY_PERMISSION_LEVEL, "v1_19_R3"));
        assertEquals("F", name(NativeMember.ENTITY_PERMISSION_LEVEL, "v1_20_R3"));
        assertEquals("f", name(NativeMember.SOURCE_PERMISSION_LEVEL, "v1_20_R3"));
        assertEquals("j", name(NativeMember.SOURCE_SILENT, "v1_20_R3"));
        assertNull(name(NativeMember.SOURCE_WITH_SOURCE, "v1_16_R3"));
        assertEquals("a", name(NativeMember.SOURCE_WITH_SOURCE, "v1_17_R1"));
    }

    @Test
    void classNamesUseTheVersionPackageBefore117() {
        assertEquals("net.minecraft.server.v1_16_R3.CommandListenerWrapper", NativeClass.SOURCE.name(MappingGeneration.of("v1_16_R3")));
        assertEquals("net.minecraft.commands.CommandListenerWrapper", NativeClass.SOURCE.name(MappingGeneration.of("v1_17_R1")));
        assertEquals("net.minecraft.commands.CommandSourceStack", NativeClass.SOURCE.name(MappingGeneration.MOJANG));
        assertEquals("net.minecraft.world.entity.player.EntityHuman",
                NativeClass.PERMISSION_LEVEL_HOLDER.name(MappingGeneration.of("v1_21_R2")));
    }

    @Test
    void anUnknownNewerTokenUsesTheNewestRow() {
        assertEquals("I", name(NativeMember.ENTITY_PERMISSION_LEVEL, "v1_21_R9"));
        assertEquals("p", name(NativeMember.SELECTOR_USES_SELECTOR, "v2_0_R1"));
    }

    @Test
    void mojangNamesAreTheMappingNames() {
        assertEquals("usesSelector", NativeMember.SELECTOR_USES_SELECTOR.name(MappingGeneration.MOJANG));
        assertEquals("withSource", NativeMember.SOURCE_WITH_SOURCE.name(MappingGeneration.MOJANG));
    }

    @Test
    void permissionSetNamesAreObfuscatedOn1_21_11AndMojangOn26() {
        MappingGeneration spigot = MappingGeneration.of("v1_21_R7");
        assertEquals("g", NativeMember.SOURCE_PERMISSIONS.name(spigot));
        assertEquals("permissions", NativeMember.SOURCE_PERMISSIONS.name(MappingGeneration.MOJANG));
        assertEquals("a", NativeMember.SOURCE_WITH_PERMISSION_SET.name(spigot));
        assertEquals("withPermission", NativeMember.SOURCE_WITH_PERMISSION_SET.name(MappingGeneration.MOJANG));
        assertEquals("I", NativeMember.PLAYER_PERMISSIONS.name(spigot));
        assertEquals("e", NativeMember.SELECTORS_PERMISSION.name(spigot));
        assertEquals("COMMANDS_ENTITY_SELECTORS", NativeMember.SELECTORS_PERMISSION.name(MappingGeneration.MOJANG));
        assertEquals("forLevel", NativeMember.FOR_LEVEL.name(MappingGeneration.MOJANG));
        assertEquals("net.minecraft.commands.CommandListenerWrapper", NativeClass.SOURCE.name(spigot));
        assertEquals("net.minecraft.world.entity.player.EntityHuman", NativeClass.PLAYER.name(spigot));
        assertEquals("net.minecraft.world.entity.player.Player", NativeClass.PLAYER.name(MappingGeneration.MOJANG));
        assertEquals("net.minecraft.server.permissions.Permission$b", NativeClass.HAS_COMMAND_LEVEL.name(spigot));
        assertEquals("net.minecraft.server.permissions.Permission$HasCommandLevel",
                NativeClass.HAS_COMMAND_LEVEL.name(MappingGeneration.MOJANG));
        assertThrows(IllegalStateException.class, () -> NativeMember.SOURCE_PERMISSIONS.name(MappingGeneration.of("v1_21_R6")));
    }

    @Test
    void generationsCompareNumerically() {
        assertTrue(MappingGeneration.of("v1_9_R2").compareTo(MappingGeneration.of("v1_13_R1")) < 0);
        assertTrue(MappingGeneration.of("v1_20_R3").compareTo(MappingGeneration.of("v1_20_R10")) < 0);
        assertTrue(MappingGeneration.of("v1_21_R7").compareTo(MappingGeneration.MOJANG) < 0);
        assertThrows(IllegalArgumentException.class, () -> MappingGeneration.of("1_20_R3"));
        assertThrows(IllegalStateException.class, () -> NativeMember.SOURCE_SILENT.name(MappingGeneration.of("v1_12_R1")));
    }

    @Test
    void detectReadsTheCraftBukkitPackageUnlessMojangNamesExist() {
        // No server classes on the test class path: neither the Mojang probe nor a version package is present.
        assertThrows(IllegalStateException.class,
                () -> MappingGeneration.detect(SpigotNamesTest.class, SpigotNamesTest.class.getClassLoader()));
    }
}
