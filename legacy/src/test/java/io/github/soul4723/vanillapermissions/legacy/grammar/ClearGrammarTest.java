package io.github.soul4723.vanillapermissions.legacy.grammar;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.assertPath;
import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.entities;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ClearGrammarTest {

    private final ClearGrammar grammar = new ClearGrammar();

    @Test
    void forms() {
        assertPath(grammar, "", "clear");
        assertPath(grammar, "Steve", "clear.targets");
        assertPath(grammar, "Steve stone", "clear.targets.item");
        assertPath(grammar, "Steve minecraft:stone 1", "clear.targets.item");
        assertPath(grammar, "Steve stone 1 10", "clear.targets.item.maxCount");
        assertPath(grammar, "Steve stone -1 0", "clear.targets.item.maxCount");
    }

    @Test
    void dataHasNoNodeAndDataTagEndsThePath() {
        assertPath(grammar, "Steve stone 3", "clear.targets.item");
        assertPath(grammar, "Steve stone 3 5 {display:{}}", "clear.targets.item.maxCount");
        assertPath(grammar, "Steve stone 3 5 {a:1} {b:2}", "clear.targets.item.maxCount");
    }

    @Test
    void playersEntityStepAtTokenZero() {
        assertEquals(Collections.singletonList("targets@0:PLAYERS"), entities(assertPath(grammar, "@a", "clear.targets")));
        assertEquals(Collections.singletonList("targets@0:PLAYERS"), entities(assertPath(grammar, "@a[r=5] stone", "clear.targets.item")));
        assertEquals(Collections.emptyList(), entities(assertPath(grammar, "", "clear")));
    }

    @Test
    void junkNumbersStillReachTheNode() {
        assertPath(grammar, "Steve stone x y", "clear.targets.item.maxCount");
    }
}
