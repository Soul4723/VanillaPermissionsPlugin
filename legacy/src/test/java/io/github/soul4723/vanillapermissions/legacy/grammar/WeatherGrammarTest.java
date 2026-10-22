package io.github.soul4723.vanillapermissions.legacy.grammar;

import org.junit.jupiter.api.Test;

import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.assertPath;
import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.entities;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherGrammarTest {

    private final WeatherGrammar grammar = new WeatherGrammar();

    @Test
    void keywordsAndDuration() {
        assertPath(grammar, "clear", "weather.clear");
        assertPath(grammar, "rain", "weather.rain");
        assertPath(grammar, "thunder", "weather.thunder");
        assertPath(grammar, "clear 100", "weather.clear.duration");
        assertPath(grammar, "rain 100", "weather.rain.duration");
        assertPath(grammar, "thunder 1000000", "weather.thunder.duration");
    }

    @Test
    void keywordsIgnoreCase() {
        assertPath(grammar, "CLEAR", "weather.clear");
        assertPath(grammar, "Rain 5", "weather.rain.duration");
        assertPath(grammar, "tHuNdEr", "weather.thunder");
    }

    @Test
    void numericSpellingsAndJunkDurationsStillReachTheDurationNode() {
        assertPath(grammar, "rain +5", "weather.rain.duration");
        assertPath(grammar, "rain 007", "weather.rain.duration");
        assertPath(grammar, "rain soon", "weather.rain.duration");
    }

    @Test
    void unknownOrMissingKeywordStopsAtTheRoot() {
        assertPath(grammar, "", "weather");
        assertPath(grammar, "storm", "weather");
        assertPath(grammar, "storm 100", "weather");
        assertPath(grammar, "1", "weather");
    }

    @Test
    void extraTokensAreIgnored() {
        // Vanilla fails with a usage error on three tokens; the extra check is harmless.
        assertPath(grammar, "rain 5 6 7", "weather.rain.duration");
    }

    @Test
    void selectorTokenIsNotAnEntityHere() {
        assertPath(grammar, "@a", "weather");
        assertEquals(java.util.Collections.emptyList(), entities(assertPath(grammar, "rain 5", "weather.rain.duration")));
    }

    @Test
    void nodePaths() {
        assertEquals(7, grammar.nodePaths(LegacyVersion.V1_12_2).size());
        assertTrue(grammar.nodePaths(LegacyVersion.V1_12_2).contains("weather"));
    }
}
