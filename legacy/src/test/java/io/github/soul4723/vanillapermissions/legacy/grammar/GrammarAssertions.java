package io.github.soul4723.vanillapermissions.legacy.grammar;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure token checks shared by the grammar tests. */
final class GrammarAssertions {
    private GrammarAssertions() {}

    /**
     * Parses {@code line} (the arguments after the label, split on single spaces like CraftBukkit) and checks the
     * described path ({@link Parse#describe()}, segments joined by {@code " | "}). Also checks that every segment's
     * path is in {@link Grammar#nodePaths} of 1.12.2 (the largest set), so the start-up count cannot drift.
     */
    static Parse assertPath(Grammar grammar, String command, String line, String expected) {
        Parse parse = grammar.parse(command, Tokens.split(line));
        assertEquals(expected, parse.describe(), () -> "/" + command + " " + line);
        Set<String> paths = grammar.nodePaths(LegacyVersion.V1_12_2);
        for (Parse.Segment segment : parse.segments()) {
            assertTrue(paths.contains(segment.path()), () -> segment.path() + " is not in nodePaths = " + paths);
        }
        return parse;
    }

    /** {@link #assertPath} for a grammar with one command. */
    static Parse assertPath(Grammar grammar, String line, String expected) {
        return assertPath(grammar, grammar.commands().get(0), line, expected);
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
