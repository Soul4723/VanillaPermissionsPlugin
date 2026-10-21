package io.github.soul4723.vanillapermissions.legacy.grammar;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.assertPath;
import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.entities;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KillGrammarTest {

    private static final LegacyVersion[] VERSIONS = GrammarAssertions.EDGE_VERSIONS;

    private final KillGrammar grammar = new KillGrammar();

    @Test
    void killSelfIsTheRoot() {
        for (LegacyVersion v : VERSIONS) {
            Parse parse = assertPath(grammar, v, "", "kill");
            assertEquals(Collections.emptyList(), entities(parse));
        }
    }

    @Test
    void killTargetIsAnyEntityAtTokenZero() {
        for (LegacyVersion v : VERSIONS) {
            assertEquals(Collections.singletonList("targets@0:ENTITIES"), entities(assertPath(grammar, v, "Steve", "kill.targets")));
            assertEquals(Collections.singletonList("targets@0:ENTITIES"), entities(assertPath(grammar, v, "@e[type=Zombie]", "kill.targets")));
            assertEquals(Collections.singletonList("targets@0:ENTITIES"),
                entities(assertPath(grammar, v, "069a79f4-44e9-4726-a5be-fca90e38aaf5", "kill.targets")));
            assertEquals(Collections.singletonList("targets@0:ENTITIES"), entities(assertPath(grammar, v, "@a", "kill.targets")));
        }
    }

    @Test
    void extraTokensAreIgnored() {
        for (LegacyVersion v : VERSIONS) {
            assertEquals(Collections.singletonList("targets@0:ENTITIES"), entities(assertPath(grammar, v, "Steve Alex", "kill.targets")));
        }
    }

    @Test
    void emptyFirstTokenStillCountsAsATarget() {
        for (LegacyVersion v : VERSIONS) {
            // "/kill  Steve" splits to ["", "Steve"]; vanilla tries to resolve "" as a name and fails.
            assertPath(grammar, v, " Steve", "kill.targets");
        }
    }
}
