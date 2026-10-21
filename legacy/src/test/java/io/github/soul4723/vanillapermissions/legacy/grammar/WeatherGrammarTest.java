package io.github.soul4723.vanillapermissions.legacy.grammar;

import org.junit.jupiter.api.Test;

import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.assertPath;
import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.entities;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherGrammarTest {

    private static final LegacyVersion[] VERSIONS = GrammarAssertions.EDGE_VERSIONS;

    private final WeatherGrammar grammar = new WeatherGrammar();

    @Test
    void keywordsAndDuration() {
        for (LegacyVersion v : VERSIONS) {
            assertPath(grammar, v, "clear", "weather.clear");
            assertPath(grammar, v, "rain", "weather.rain");
            assertPath(grammar, v, "thunder", "weather.thunder");
            assertPath(grammar, v, "clear 100", "weather.clear.duration");
            assertPath(grammar, v, "rain 100", "weather.rain.duration");
            assertPath(grammar, v, "thunder 1000000", "weather.thunder.duration");
        }
    }

    @Test
    void keywordsIgnoreCase() {
        for (LegacyVersion v : VERSIONS) {
            assertPath(grammar, v, "CLEAR", "weather.clear");
            assertPath(grammar, v, "Rain 5", "weather.rain.duration");
            assertPath(grammar, v, "tHuNdEr", "weather.thunder");
        }
    }

    @Test
    void numericSpellingsAndJunkDurationsStillReachTheDurationNode() {
        for (LegacyVersion v : VERSIONS) {
            assertPath(grammar, v, "rain +5", "weather.rain.duration");
            assertPath(grammar, v, "rain 007", "weather.rain.duration");
            assertPath(grammar, v, "rain soon", "weather.rain.duration");
        }
    }

    @Test
    void unknownOrMissingKeywordStopsAtTheRoot() {
        for (LegacyVersion v : VERSIONS) {
            assertPath(grammar, v, "", "weather");
            assertPath(grammar, v, "storm", "weather");
            assertPath(grammar, v, "storm 100", "weather");
            assertPath(grammar, v, "1", "weather");
        }
    }

    @Test
    void extraTokensAreIgnored() {
        for (LegacyVersion v : VERSIONS) {
            // Vanilla fails with a usage error on three tokens; the extra check is harmless.
            assertPath(grammar, v, "rain 5 6 7", "weather.rain.duration");
        }
    }

    @Test
    void selectorTokenIsNotAnEntityHere() {
        for (LegacyVersion v : VERSIONS) {
            assertPath(grammar, v, "@a", "weather");
            assertEquals(java.util.Collections.emptyList(), entities(assertPath(grammar, v, "rain 5", "weather.rain.duration")));
        }
    }

    @Test
    void nodePaths() {
        for (LegacyVersion v : VERSIONS) {
            assertEquals(7, grammar.nodePaths(v).size());
            assertTrue(grammar.nodePaths(v).contains("weather"));
        }
    }
}
