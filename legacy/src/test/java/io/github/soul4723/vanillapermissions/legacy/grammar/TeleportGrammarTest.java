package io.github.soul4723.vanillapermissions.legacy.grammar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.assertPath;
import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.entities;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportGrammarTest {

    private static final LegacyVersion[] ALL = GrammarAssertions.ALL_VERSIONS;

    private final TeleportGrammar grammar = new TeleportGrammar();

    private Parse tp(LegacyVersion version, String line, String expected) {
        return assertPath(grammar, version, "tp", line, expected);
    }

    private Parse teleport(LegacyVersion version, String line, String expected) {
        return assertPath(grammar, version, "teleport", line, expected);
    }

    @Test
    void tpChoosesTheFormByArgumentCount() {
        for (LegacyVersion v : ALL) {
            tp(v, "", "teleport");
            tp(v, "Bob", "teleport.destination");
            tp(v, "Alice Bob", "teleport.targets.destination");
            tp(v, "1 64 ~", "teleport.location");
            tp(v, "Alice 1 64 ~", "teleport.targets.location");
            tp(v, "1 64 1 90 0", "teleport.location");
            tp(v, "Alice 1 64 1 90 0", "teleport.targets.location.rotation");
        }
    }

    @Test
    void sevenOrMoreArgumentsMoveTheSenderLikeVanilla() {
        // not 2, 4 or 6: vanilla reads token 0 as x and ignores the rest
        for (LegacyVersion v : ALL) {
            Parse seven = tp(v, "Alice 1 64 1 90 0 x", "teleport.location");
            assertEquals(List.of(), entities(seven));
            tp(v, "1 2 3 4 5 6 7 8", "teleport.location");
        }
    }

    @Test
    void tpEntityStepsAndTokens() {
        LegacyVersion v = LegacyVersion.V1_8_8;
        assertEquals(List.of("destination@0:ENTITIES"), entities(tp(v, "@e[type=Pig]", "teleport.destination")));
        assertEquals(List.of("targets@0:ENTITIES", "destination@1:ENTITIES"),
            entities(tp(v, "@a @p", "teleport.targets.destination")));
        assertEquals(List.of(), entities(tp(v, "~ ~ ~", "teleport.location")));
        assertEquals(List.of("targets@0:ENTITIES"), entities(tp(v, "@a 1 2 3", "teleport.targets.location")));
        assertEquals(List.of("targets@0:ENTITIES"), entities(tp(v, "@a 1 2 3 4 5", "teleport.targets.location.rotation")));
        assertEquals(List.of(), entities(tp(v, "1 2 3 4 5", "teleport.location")));
    }

    @Test
    void tpNodesAreCheckedWithoutTheRoot() {
        Parse parse = tp(LegacyVersion.V1_12_2, "Alice Bob", "teleport.targets.destination");
        assertEquals(List.of("teleport.targets", "teleport.targets.destination"), parse.checkedNodes());
        assertEquals(List.of(), tp(LegacyVersion.V1_12_2, "", "teleport").checkedNodes());
    }

    @Test
    void emptyTokensCountLikeVanilla() {
        // "tp a  b" splits into three tokens, one empty
        assertEquals("teleport.location", grammar.parse("tp", new String[] {"a", "", "b"}, LegacyVersion.V1_8_8).describe());
        assertEquals("teleport.destination", grammar.parse("tp", new String[] {""}, LegacyVersion.V1_8_8).describe());
    }

    @Test
    void teleportCommandFromOnePointTen() {
        for (LegacyVersion v : new LegacyVersion[] {LegacyVersion.V1_10_2, LegacyVersion.V1_11_2, LegacyVersion.V1_12_2}) {
            teleport(v, "", "teleport");
            teleport(v, "Alice", "teleport");
            teleport(v, "Alice 1 2", "teleport");
            teleport(v, "Alice 1 2 3", "teleport.targets.location");
            teleport(v, "Alice 1 2 3 90", "teleport.targets.location.rotation");
            teleport(v, "Alice 1 2 3 90 0", "teleport.targets.location.rotation");
            teleport(v, "Alice 1 2 3 90 0 more", "teleport.targets.location.rotation");
            assertEquals(List.of("targets@0:ENTITIES"), entities(teleport(v, "@e 1 2 3", "teleport.targets.location")));
            assertEquals(List.of("teleport.targets", "teleport.targets.location", "teleport.targets.location.rotation"),
                teleport(v, "@e 1 2 3 4", "teleport.targets.location.rotation").checkedNodes());
        }
    }

    @Test
    void teleportParsingDoesNotApplyVersionGate() {
        for (LegacyVersion v : new LegacyVersion[] {LegacyVersion.V1_8_8, LegacyVersion.V1_9_4}) {
            assertEquals("teleport.targets.location",
                grammar.parse("teleport", new String[] {"Alice", "1", "2", "3"}, v).describe());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"TELEPORT", "Teleport"})
    void commandNameIgnoresCase(String name) {
        assertEquals("teleport.targets.location",
            grammar.parse(name, new String[] {"a", "1", "2", "3"}, LegacyVersion.V1_12_2).describe());
    }

    @Test
    void nodePathsListEveryFormOnEveryVersion() {
        List<String> expected = List.of("teleport", "teleport.destination", "teleport.targets", "teleport.targets.destination",
            "teleport.location", "teleport.targets.location", "teleport.targets.location.rotation");
        for (LegacyVersion v : ALL) {
            assertEquals(expected.size(), grammar.nodePaths(v).size(), v.toString());
            assertTrue(grammar.nodePaths(v).containsAll(expected), v.toString());
        }
        // /teleport's paths are a subset of /tp's, so the set cannot differ: /tp exists on every version
        assertEquals(grammar.nodePaths(LegacyVersion.V1_8_8), grammar.nodePaths(LegacyVersion.V1_12_2));
    }
}
