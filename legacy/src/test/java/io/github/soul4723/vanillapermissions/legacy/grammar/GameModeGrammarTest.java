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
        assertPath(grammar, spelling, "gamemode.creative");
        assertPath(grammar, spelling + " Bob", "gamemode.creative.target");
    }

    @Test
    void otherModesAndTheirShortForms() {
        assertPath(grammar, "survival", "gamemode.survival");
        assertPath(grammar, "s", "gamemode.survival");
        assertPath(grammar, "0", "gamemode.survival");
        assertPath(grammar, "adventure @a", "gamemode.adventure.target");
        assertPath(grammar, "a", "gamemode.adventure");
        assertPath(grammar, "2", "gamemode.adventure");
        assertPath(grammar, "spectator", "gamemode.spectator");
        assertPath(grammar, "sp Bob", "gamemode.spectator.target");
        assertPath(grammar, "3", "gamemode.spectator");
    }

    @Test
    void unknownModeStopsAtTheRoot() {
        assertPath(grammar, "", "gamemode");
        assertPath(grammar, "4", "gamemode");
        assertPath(grammar, "hardcore Bob", "gamemode");
    }

    @Test
    void targetIsAPlayerArgument() {
        Parse parse = assertPath(grammar, "c @a[r=5]", "gamemode.creative.target");
        assertEquals(List.of("target@1:PLAYERS"), entities(parse));
        assertEquals(List.of("gamemode.creative", "gamemode.creative.target"), parse.checkedNodes());
    }

    @Test
    void nodePathsListEveryModeAndTarget() {
        assertEquals(9, grammar.nodePaths(LegacyVersion.V1_8_8).size());
    }
}
