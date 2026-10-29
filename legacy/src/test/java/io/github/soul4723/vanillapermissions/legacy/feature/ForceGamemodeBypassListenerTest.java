package io.github.soul4723.vanillapermissions.legacy.feature;

import io.github.soul4723.vanillapermissions.util.TestLuckPerms;
import net.luckperms.api.util.Tristate;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static io.github.soul4723.vanillapermissions.util.TestLuckPerms.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForceGamemodeBypassListenerTest {

    @ParameterizedTest
    @CsvSource(value = {
        "NULL,SURVIVAL,NULL",
        "0,CREATIVE,SURVIVAL",
        "1,SURVIVAL,CREATIVE",
        "2,CREATIVE,ADVENTURE",
        "3,CREATIVE,SPECTATOR",
        "1,CREATIVE,NULL",
        "0,SURVIVAL,NULL",
        "-1,SURVIVAL,NULL",
        "99,SURVIVAL,NULL"
    }, nullValues = "NULL")
    void restoredMode(Integer saved, GameMode current, GameMode expected) {
        assertEquals(expected, ForceGamemodeBypassListener.restoredMode(saved, current));
    }

    @Test
    void defaultGamemodeCommandIsRecognisedOnEveryLegacyPackage() {
        for (String nms : new String[] {"v1_8_R3", "v1_9_R2", "v1_10_R1", "v1_11_R1", "v1_12_R1"}) {
            assertTrue(ForceGamemodeBypassListener.fromDefaultGamemodeCommand(stack(
                "org.bukkit.craftbukkit." + nms + ".entity.CraftPlayer",
                "net.minecraft.server." + nms + ".EntityPlayer",
                "net.minecraft.server." + nms + ".CommandGamemodeDefault",
                "net.minecraft.server." + nms + ".CommandHandler")), nms);
        }
    }

    @Test
    void otherGameModeChangesAreNotTheDefaultGamemodeCommand() {
        assertFalse(ForceGamemodeBypassListener.fromDefaultGamemodeCommand(stack(
            "net.minecraft.server.v1_12_R1.EntityPlayer", "net.minecraft.server.v1_12_R1.CommandGamemode")));
        assertFalse(ForceGamemodeBypassListener.fromDefaultGamemodeCommand(stack("com.example.CommandGamemodeDefault")));
        assertFalse(ForceGamemodeBypassListener.fromDefaultGamemodeCommand(new StackTraceElement[0]));
    }

    @Test
    void ordinaryGameModeChangeIsLeftAloneEvenWithTheNode() throws Exception {
        UUID id = UUID.randomUUID();
        Player player = proxy(Player.class, (p, m, a) -> switch (m.getName()) {
            case "getUniqueId" -> id;
            default -> throw new AssertionError("Unexpected Player call: " + m);
        });
        try (TestLuckPerms luckPerms = new TestLuckPerms()) {
            luckPerms.nodes.put(ForceGamemodeBypassListener.BYPASS_PERMISSION, Tristate.TRUE);
            PlayerGameModeChangeEvent event = new PlayerGameModeChangeEvent(player, GameMode.SURVIVAL);
            // No CommandGamemodeDefault frame on this test's stack: a /gamemode or plugin change must go through.
            listener().onGameModeChange(event);
            assertFalse(event.isCancelled());
        }
    }

    private static ForceGamemodeBypassListener listener() throws ReflectiveOperationException {
        var constructor = ForceGamemodeBypassListener.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return (ForceGamemodeBypassListener) constructor.newInstance(null, null, null, null);
    }

    private static StackTraceElement[] stack(String... classes) {
        StackTraceElement[] stack = new StackTraceElement[classes.length];
        for (int i = 0; i < classes.length; i++) stack[i] = new StackTraceElement(classes[i], "a", null, -1);
        return stack;
    }
}
