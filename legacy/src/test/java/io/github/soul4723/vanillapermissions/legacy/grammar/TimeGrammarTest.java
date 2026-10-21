package io.github.soul4723.vanillapermissions.legacy.grammar;

import org.junit.jupiter.api.Test;

import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.assertPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeGrammarTest {

    private final TimeGrammar grammar = new TimeGrammar();

    @Test
    void setForms() {
        LegacyVersion v = LegacyVersion.V1_8_8;
        assertPath(grammar, v, "set day", "time.set.day");
        assertPath(grammar, v, "set night", "time.set.night");
        assertPath(grammar, v, "set 6000", "time.set.time");
        assertPath(grammar, v, "set +6000", "time.set.time");
        // Not a keyword on 1.8 - 1.12: vanilla parses it as a number and fails, which is still the time node.
        assertPath(grammar, v, "set noon", "time.set.time");
        assertPath(grammar, v, "set", "time.set");
        assertPath(grammar, v, "SET DAY", "time.set.day");
    }

    @Test
    void addAndQuery() {
        assertPath(grammar, LegacyVersion.V1_8_8, "add 100", "time.add.time");
        assertPath(grammar, LegacyVersion.V1_8_8, "add", "time.add");
        assertPath(grammar, LegacyVersion.V1_8_8, "query daytime", "time.query.daytime");
        assertPath(grammar, LegacyVersion.V1_8_8, "query gametime", "time.query.gametime");
        assertPath(grammar, LegacyVersion.V1_8_8, "query bogus", "time.query");
    }

    @Test
    void queryDayIsParsedButOnlyListedFrom1_12() {
        Parse older = grammar.parse("time", new String[] {"query", "day"}, LegacyVersion.V1_11_2);
        assertEquals("time.query.day", older.describe());
        assertPath(grammar, LegacyVersion.V1_12_2, "query day", "time.query.day");
        assertFalse(grammar.nodePaths(LegacyVersion.V1_11_2).contains("time.query.day"));
        assertTrue(grammar.nodePaths(LegacyVersion.V1_12_2).contains("time.query.day"));
    }

    @Test
    void unknownActionStopsAtTheRoot() {
        assertPath(grammar, LegacyVersion.V1_12_2, "", "time");
        assertPath(grammar, LegacyVersion.V1_12_2, "rewind 5", "time");
    }
}
