package io.github.soul4723.vanillapermissions.legacy.grammar;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure token checks shared by the grammar tests. */
final class GrammarAssertions {
    static final LegacyVersion[] EDGE_VERSIONS = {LegacyVersion.V1_8_8, LegacyVersion.V1_12_2};
    static final LegacyVersion[] ALL_VERSIONS = {LegacyVersion.V1_8_8, LegacyVersion.V1_9_4,
        LegacyVersion.V1_10_2, LegacyVersion.V1_11_2, LegacyVersion.V1_12_2};
    static final LegacyVersion[] SINCE_1_9 = {LegacyVersion.V1_9_4, LegacyVersion.V1_10_2,
        LegacyVersion.V1_11_2, LegacyVersion.V1_12_2};

    private GrammarAssertions() {}

    /**
     * Parses {@code line} (the arguments after the label, split on single spaces like CraftBukkit) and checks the
     * described path ({@link Parse#describe()}, segments joined by {@code " | "}). Also checks that the path of
     * every segment of the parse is one of {@link Grammar#nodePaths}, so the start-up count cannot drift.
     */
    static Parse assertPath(Grammar grammar, LegacyVersion version, String command, String line, String expected) {
        Parse parse = grammar.parse(command, Tokens.split(line), version);
        assertEquals(expected, parse.describe(), () -> "/" + command + " " + line + " on " + version);
        assertProducible(grammar, version, parse);
        return parse;
    }

    /** {@link #assertPath} for a grammar with one command. */
    static Parse assertPath(Grammar grammar, LegacyVersion version, String line, String expected) {
        return assertPath(grammar, version, grammar.commands().get(0), line, expected);
    }

    /** The path of every segment of {@code parse} is listed in {@link Grammar#nodePaths}. */
    static void assertProducible(Grammar grammar, LegacyVersion version, Parse parse) {
        for (Parse.Segment segment : parse.segments()) {
            assertTrue(grammar.nodePaths(version).contains(segment.path()),
                () -> segment.path() + " is not in nodePaths(" + version + ") = " + grammar.nodePaths(version));
        }
    }

    /** The entity steps as {@code name@token[+width]:KIND}, e.g. {@code [targets@0:ENTITIES]}, over all segments. */
    static List<String> entities(Parse parse) {
        List<String> entities = new ArrayList<>();
        for (Parse.Segment segment : parse.segments()) {
            for (Parse.Step step : segment.steps()) {
                if (!step.kind().isEntity()) continue;
                entities.add(step + ":" + step.kind());
            }
        }
        return entities;
    }
}
