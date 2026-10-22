package io.github.soul4723.vanillapermissions.legacy.grammar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.assertPath;
import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.entities;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TeleportGrammarTest {

    private final TeleportGrammar grammar = new TeleportGrammar();

    private Parse tp(String line, String expected) {
        return assertPath(grammar, "tp", line, expected);
    }

    private Parse teleport(String line, String expected) {
        return assertPath(grammar, "teleport", line, expected);
    }

    @Test
    void tpChoosesTheFormByArgumentCount() {
        tp("", "teleport");
        tp("Bob", "teleport.destination");
        tp("Alice Bob", "teleport.targets.destination");
        tp("1 64 ~", "teleport.location");
        tp("Alice 1 64 ~", "teleport.targets.location");
        tp("1 64 1 90 0", "teleport.location");
        tp("Alice 1 64 1 90 0", "teleport.targets.location.rotation");
    }

    @Test
    void sevenOrMoreArgumentsMoveTheSenderLikeVanilla() {
        // not 2, 4 or 6: vanilla reads token 0 as x and ignores the rest
        Parse seven = tp("Alice 1 64 1 90 0 x", "teleport.location");
        assertEquals(List.of(), entities(seven));
        tp("1 2 3 4 5 6 7 8", "teleport.location");
    }

    @Test
    void tpEntityStepsAndTokens() {
        assertEquals(List.of("destination@0:ENTITIES"), entities(tp("@e[type=Pig]", "teleport.destination")));
        assertEquals(List.of("targets@0:ENTITIES", "destination@1:ENTITIES"),
            entities(tp("@a @p", "teleport.targets.destination")));
        assertEquals(List.of(), entities(tp("~ ~ ~", "teleport.location")));
        assertEquals(List.of("targets@0:ENTITIES"), entities(tp("@a 1 2 3", "teleport.targets.location")));
        assertEquals(List.of("targets@0:ENTITIES"), entities(tp("@a 1 2 3 4 5", "teleport.targets.location.rotation")));
        assertEquals(List.of(), entities(tp("1 2 3 4 5", "teleport.location")));
    }

    @Test
    void tpNodesAreCheckedWithoutTheRoot() {
        Parse parse = tp("Alice Bob", "teleport.targets.destination");
        assertEquals(List.of("teleport.targets", "teleport.targets.destination"), parse.checkedNodes());
        assertEquals(List.of(), tp("", "teleport").checkedNodes());
    }

    @Test
    void emptyTokensCountLikeVanilla() {
        // "tp a  b" splits into three tokens, one empty
        assertEquals("teleport.location", grammar.parse("tp", new String[] {"a", "", "b"}).describe());
        assertEquals("teleport.destination", grammar.parse("tp", new String[] {""}).describe());
    }

    @Test
    void teleportCommandFromOnePointTen() {
        teleport("", "teleport");
        teleport("Alice", "teleport");
        teleport("Alice 1 2", "teleport");
        teleport("Alice 1 2 3", "teleport.targets.location");
        teleport("Alice 1 2 3 90", "teleport.targets.location.rotation");
        teleport("Alice 1 2 3 90 0", "teleport.targets.location.rotation");
        teleport("Alice 1 2 3 90 0 more", "teleport.targets.location.rotation");
        assertEquals(List.of("targets@0:ENTITIES"), entities(teleport("@e 1 2 3", "teleport.targets.location")));
        assertEquals(List.of("teleport.targets", "teleport.targets.location", "teleport.targets.location.rotation"),
            teleport("@e 1 2 3 4", "teleport.targets.location.rotation").checkedNodes());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TELEPORT", "Teleport"})
    void commandNameIgnoresCase(String name) {
        assertEquals("teleport.targets.location",
            grammar.parse(name, new String[] {"a", "1", "2", "3"}).describe());
    }

    @Test
    void nodePathsListEveryForm() {
        assertEquals(Set.of("teleport", "teleport.destination", "teleport.targets", "teleport.targets.destination",
            "teleport.location", "teleport.targets.location", "teleport.targets.location.rotation"),
            grammar.nodePaths(LegacyVersion.V1_12_2));
    }
}
