package io.github.soul4723.vanillapermissions.legacy.grammar;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.assertPath;
import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.entities;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KillGrammarTest {

    private final KillGrammar grammar = new KillGrammar();

    @Test
    void killSelfIsTheRoot() {
        Parse parse = assertPath(grammar, "", "kill");
        assertEquals(Collections.emptyList(), entities(parse));
    }

    @Test
    void killTargetIsAnyEntityAtTokenZero() {
        assertEquals(Collections.singletonList("targets@0:ENTITIES"), entities(assertPath(grammar, "Steve", "kill.targets")));
        assertEquals(Collections.singletonList("targets@0:ENTITIES"), entities(assertPath(grammar, "@e[type=Zombie]", "kill.targets")));
        assertEquals(Collections.singletonList("targets@0:ENTITIES"),
            entities(assertPath(grammar, "069a79f4-44e9-4726-a5be-fca90e38aaf5", "kill.targets")));
        assertEquals(Collections.singletonList("targets@0:ENTITIES"), entities(assertPath(grammar, "@a", "kill.targets")));
    }

    @Test
    void extraTokensAreIgnored() {
        assertEquals(Collections.singletonList("targets@0:ENTITIES"), entities(assertPath(grammar, "Steve Alex", "kill.targets")));
    }

    @Test
    void emptyFirstTokenStillCountsAsATarget() {
        // "/kill  Steve" splits to ["", "Steve"]; vanilla tries to resolve "" as a name and fails.
        assertPath(grammar, " Steve", "kill.targets");
    }
}
