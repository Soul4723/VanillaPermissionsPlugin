package io.github.soul4723.vanillapermissions.legacy.grammar;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.assertPath;
import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.entities;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ClearGrammarTest {

    private static final LegacyVersion[] VERSIONS = GrammarAssertions.EDGE_VERSIONS;

    private final ClearGrammar grammar = new ClearGrammar();

    @Test
    void forms() {
        for (LegacyVersion v : VERSIONS) {
            assertPath(grammar, v, "", "clear");
            assertPath(grammar, v, "Steve", "clear.targets");
            assertPath(grammar, v, "Steve stone", "clear.targets.item");
            assertPath(grammar, v, "Steve minecraft:stone 1", "clear.targets.item");
            assertPath(grammar, v, "Steve stone 1 10", "clear.targets.item.maxCount");
            assertPath(grammar, v, "Steve stone -1 0", "clear.targets.item.maxCount");
        }
    }

    @Test
    void dataHasNoNodeAndDataTagEndsThePath() {
        for (LegacyVersion v : VERSIONS) {
            assertPath(grammar, v, "Steve stone 3", "clear.targets.item");
            assertPath(grammar, v, "Steve stone 3 5 {display:{}}", "clear.targets.item.maxCount");
            assertPath(grammar, v, "Steve stone 3 5 {a:1} {b:2}", "clear.targets.item.maxCount");
        }
    }

    @Test
    void playersEntityStepAtTokenZero() {
        for (LegacyVersion v : VERSIONS) {
            assertEquals(Collections.singletonList("targets@0:PLAYERS"), entities(assertPath(grammar, v, "@a", "clear.targets")));
            assertEquals(Collections.singletonList("targets@0:PLAYERS"), entities(assertPath(grammar, v, "@a[r=5] stone", "clear.targets.item")));
            assertEquals(Collections.emptyList(), entities(assertPath(grammar, v, "", "clear")));
        }
    }

    @Test
    void junkNumbersStillReachTheNode() {
        for (LegacyVersion v : VERSIONS) {
            assertPath(grammar, v, "Steve stone x y", "clear.targets.item.maxCount");
        }
    }
}
