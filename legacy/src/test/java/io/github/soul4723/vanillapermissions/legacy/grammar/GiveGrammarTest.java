package io.github.soul4723.vanillapermissions.legacy.grammar;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.assertPath;
import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.entities;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GiveGrammarTest {

    private final GiveGrammar grammar = new GiveGrammar();

    @Test
    void forms() {
        assertPath(grammar, "", "give");
        assertPath(grammar, "Steve", "give.targets");
        assertPath(grammar, "Steve diamond", "give.targets.item");
        assertPath(grammar, "Steve minecraft:diamond 5", "give.targets.item.count");
        assertPath(grammar, "Steve 276 +5", "give.targets.item.count");
    }

    @Test
    void dataAndDataTagHaveNoNode() {
        assertPath(grammar, "Steve stone 1 3", "give.targets.item.count");
        assertPath(grammar, "Steve stone 1 3 {display:{Name:\"x\"}}", "give.targets.item.count");
        assertPath(grammar, "Steve stone 1 0 {a:1} {b:2}", "give.targets.item.count");
    }

    @Test
    void playersEntityStepAtTokenZero() {
        assertEquals(Collections.singletonList("targets@0:PLAYERS"), entities(assertPath(grammar, "@a stone", "give.targets.item")));
        assertEquals(Collections.singletonList("targets@0:PLAYERS"), entities(assertPath(grammar, "@p", "give.targets")));
        assertEquals(Collections.singletonList("targets@0:PLAYERS"),
            entities(assertPath(grammar, "069a79f4-44e9-4726-a5be-fca90e38aaf5 stone 64", "give.targets.item.count")));
    }

    @Test
    void nonNumericAmountStillReachesTheCountNode() {
        assertPath(grammar, "Steve stone lots", "give.targets.item.count");
    }
}
