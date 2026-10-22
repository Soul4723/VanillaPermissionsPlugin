package io.github.soul4723.vanillapermissions.legacy.grammar;

import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.assertPath;
import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.entities;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ExecuteGrammarTest {

    private final ExecuteGrammar grammar = new ExecuteGrammar();

    @Test
    void plainFormIsAsTargetsThenRun() {
        Parse parse = assertPath(grammar, "@p ~ ~ ~ gamemode creative", "execute.as.targets | execute.run");
        assertEquals(4, parse.innerCommandStart());
        assertEquals(List.of("targets@0:ENTITIES"), entities(parse));
        assertEquals(List.of("execute.as", "execute.as.targets", "execute.run"), parse.checkedNodes());
    }

    @Test
    void detectFormAddsIfBlock() {
        Parse parse = assertPath(grammar, "Bob ~ ~ ~ detect ~ ~-1 ~ stone 0 say hi",
            "execute.as.targets | execute.if.block.pos.block | execute.run");
        assertEquals(10, parse.innerCommandStart());
    }

    @Test
    void detectNeedsMoreThanTenTokensAndExactCase() {
        // Vanilla then treats "detect" as the nested command's name.
        assertEquals(4, assertPath(grammar, "Bob ~ ~ ~ detect ~ ~ ~ stone 0", "execute.as.targets | execute.run").innerCommandStart());
        assertEquals(4, assertPath(grammar, "Bob ~ ~ ~ DETECT ~ ~ ~ stone 0 say", "execute.as.targets | execute.run").innerCommandStart());
    }

    @Test
    void incompleteFormsHaveNoInnerCommand() {
        assertEquals(-1, assertPath(grammar, "", "execute").innerCommandStart());
        assertEquals(-1, assertPath(grammar, "Bob ~ ~ ~", "execute.as.targets").innerCommandStart());
    }
}
