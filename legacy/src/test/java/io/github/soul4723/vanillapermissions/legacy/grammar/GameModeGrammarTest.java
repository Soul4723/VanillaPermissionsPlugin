package io.github.soul4723.vanillapermissions.legacy.grammar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.assertPath;
import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.entities;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GameModeGrammarTest {

    private final GameModeGrammar grammar = new GameModeGrammar();

    @ParameterizedTest
    @ValueSource(strings = {"creative", "c", "1", "01", "+1", "CREATIVE", "Creative", "C"})
    void everyCreativeSpellingIsTheCreativeNode(String spelling) {
        for (LegacyVersion version : GrammarAssertions.EDGE_VERSIONS) {
            assertPath(grammar, version, spelling, "gamemode.creative");
            assertPath(grammar, version, spelling + " Bob", "gamemode.creative.target");
        }
    }

    @Test
    void otherModesAndTheirShortForms() {
        LegacyVersion v = LegacyVersion.V1_12_2;
        assertPath(grammar, v, "survival", "gamemode.survival");
        assertPath(grammar, v, "s", "gamemode.survival");
        assertPath(grammar, v, "0", "gamemode.survival");
        assertPath(grammar, v, "adventure @a", "gamemode.adventure.target");
        assertPath(grammar, v, "a", "gamemode.adventure");
        assertPath(grammar, v, "2", "gamemode.adventure");
        assertPath(grammar, v, "spectator", "gamemode.spectator");
        assertPath(grammar, v, "sp Bob", "gamemode.spectator.target");
        assertPath(grammar, v, "3", "gamemode.spectator");
    }

    @Test
    void unknownModeStopsAtTheRoot() {
        assertPath(grammar, LegacyVersion.V1_8_8, "", "gamemode");
        assertPath(grammar, LegacyVersion.V1_8_8, "4", "gamemode");
        assertPath(grammar, LegacyVersion.V1_8_8, "hardcore Bob", "gamemode");
    }

    @Test
    void targetIsAPlayerArgument() {
        Parse parse = assertPath(grammar, LegacyVersion.V1_8_8, "c @a[r=5]", "gamemode.creative.target");
        assertEquals(List.of("target@1:PLAYERS"), entities(parse));
        assertEquals(List.of("gamemode.creative", "gamemode.creative.target"), parse.checkedNodes());
    }

    @Test
    void nodePathsListEveryModeAndTarget() {
        assertEquals(9, grammar.nodePaths(LegacyVersion.V1_8_8).size());
    }
}
