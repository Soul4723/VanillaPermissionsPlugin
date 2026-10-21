package io.github.soul4723.vanillapermissions.legacy;

import io.github.soul4723.vanillapermissions.legacy.grammar.Grammars;
import io.github.soul4723.vanillapermissions.platform.AdapterLog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LegacyAdapterTest {

    @Test
    void startupLineCountsTheShippedGrammars() {
        assertEquals(Grammars.count(), AdapterLog.LEGACY_COMMANDS);
        assertEquals("Using legacy mode on Spigot 1.12.2 (sub-command checks for " + Grammars.count()
                + " commands only; other vanilla commands keep their root node)",
            AdapterLog.using(new LegacyAdapter().id(), "Spigot", "1.12.2"));
    }

    @Test
    void nodesWithoutEffectInLegacyModeAreNamed() {
        LegacyAdapter adapter = new LegacyAdapter();
        assertNull(adapter.inactiveReason("minecraft.command.say.message"), "nothing is known before the install");
        assertEquals("there is no debug stick before 1.13", adapter.inactiveReason("minecraft.debug_stick.use.minecraft.stone"));
        assertNull(adapter.inactiveReason("minecraft.operator_block.command_block.place"));
        assertNull(adapter.inactiveReason("minecraft.command.say"), "roots are CraftBukkit's own nodes");
    }
}
