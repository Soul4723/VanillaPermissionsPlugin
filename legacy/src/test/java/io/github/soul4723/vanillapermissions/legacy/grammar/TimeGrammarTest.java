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
        assertPath(grammar, "set day", "time.set.day");
        assertPath(grammar, "set night", "time.set.night");
        assertPath(grammar, "set 6000", "time.set.time");
        assertPath(grammar, "set +6000", "time.set.time");
        // Not a keyword on 1.8 - 1.12: vanilla parses it as a number and fails, which is still the time node.
        assertPath(grammar, "set noon", "time.set.time");
        assertPath(grammar, "set", "time.set");
        assertPath(grammar, "SET DAY", "time.set.day");
    }

    @Test
    void addAndQuery() {
        assertPath(grammar, "add 100", "time.add.time");
        assertPath(grammar, "add", "time.add");
        assertPath(grammar, "query daytime", "time.query.daytime");
        assertPath(grammar, "query gametime", "time.query.gametime");
        assertPath(grammar, "query bogus", "time.query");
    }

    @Test
    void queryDayIsParsedButOnlyListedFrom1_12() {
        Parse older = grammar.parse("time", new String[] {"query", "day"});
        assertEquals("time.query.day", older.describe());
        assertPath(grammar, "query day", "time.query.day");
        assertFalse(grammar.nodePaths(LegacyVersion.V1_11_2).contains("time.query.day"));
        assertTrue(grammar.nodePaths(LegacyVersion.V1_12_2).contains("time.query.day"));
    }

    @Test
    void unknownActionStopsAtTheRoot() {
        assertPath(grammar, "", "time");
        assertPath(grammar, "rewind 5", "time");
    }
}
