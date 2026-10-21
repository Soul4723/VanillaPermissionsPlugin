package io.github.soul4723.vanillapermissions.legacy.grammar;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.assertPath;
import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.entities;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GiveGrammarTest {

    private static final LegacyVersion[] VERSIONS = GrammarAssertions.EDGE_VERSIONS;

    private final GiveGrammar grammar = new GiveGrammar();

    @Test
    void forms() {
        for (LegacyVersion v : VERSIONS) {
            assertPath(grammar, v, "", "give");
            assertPath(grammar, v, "Steve", "give.targets");
            assertPath(grammar, v, "Steve diamond", "give.targets.item");
            assertPath(grammar, v, "Steve minecraft:diamond 5", "give.targets.item.count");
            assertPath(grammar, v, "Steve 276 +5", "give.targets.item.count");
        }
    }

    @Test
    void dataAndDataTagHaveNoNode() {
        for (LegacyVersion v : VERSIONS) {
            assertPath(grammar, v, "Steve stone 1 3", "give.targets.item.count");
            assertPath(grammar, v, "Steve stone 1 3 {display:{Name:\"x\"}}", "give.targets.item.count");
            assertPath(grammar, v, "Steve stone 1 0 {a:1} {b:2}", "give.targets.item.count");
        }
    }

    @Test
    void playersEntityStepAtTokenZero() {
        for (LegacyVersion v : VERSIONS) {
            assertEquals(Collections.singletonList("targets@0:PLAYERS"), entities(assertPath(grammar, v, "@a stone", "give.targets.item")));
            assertEquals(Collections.singletonList("targets@0:PLAYERS"), entities(assertPath(grammar, v, "@p", "give.targets")));
            assertEquals(Collections.singletonList("targets@0:PLAYERS"),
                entities(assertPath(grammar, v, "069a79f4-44e9-4726-a5be-fca90e38aaf5 stone 64", "give.targets.item.count")));
        }
    }

    @Test
    void nonNumericAmountStillReachesTheCountNode() {
        for (LegacyVersion v : VERSIONS) {
            assertPath(grammar, v, "Steve stone lots", "give.targets.item.count");
        }
    }
}
