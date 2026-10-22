package io.github.soul4723.vanillapermissions.legacy.grammar;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.assertPath;
import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.entities;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EffectGrammarTest {

    private final EffectGrammar grammar = new EffectGrammar();

    @Test
    void clearAllEffects() {
        assertPath(grammar, "Steve clear", "effect.clear.targets");
        // Vanilla compares "clear" exactly on 1.8.8 and 1.12.2 (any other case is an unknown effect name that
        // fails); matching it ignoring case only adds a check.
        assertPath(grammar, "Steve CLEAR", "effect.clear.targets");
    }

    @Test
    void giveForms() {
        assertPath(grammar, "Steve speed", "effect.give.targets.effect");
        assertPath(grammar, "Steve minecraft:speed", "effect.give.targets.effect");
        assertPath(grammar, "Steve 1", "effect.give.targets.effect");
        assertPath(grammar, "Steve speed 30", "effect.give.targets.effect.seconds");
        assertPath(grammar, "Steve speed 30 2", "effect.give.targets.effect.seconds.amplifier");
        assertPath(grammar, "Steve speed 30 2 true", "effect.give.targets.effect.seconds.amplifier.hideParticles");
        assertPath(grammar, "Steve speed 30 2 whatever", "effect.give.targets.effect.seconds.amplifier.hideParticles");
        assertPath(grammar, "Steve speed 30 2 true extra", "effect.give.targets.effect.seconds.amplifier.hideParticles");
    }

    @Test
    void zeroSecondsRemovesTheEffect() {
        assertPath(grammar, "Steve speed 0", "effect.clear.targets.effect");
        assertPath(grammar, "Steve speed 00", "effect.clear.targets.effect");
        assertPath(grammar, "Steve speed +0", "effect.clear.targets.effect");
        assertPath(grammar, "Steve speed -0", "effect.clear.targets.effect");
        assertPath(grammar, "Steve speed 0 5 true", "effect.clear.targets.effect");
        assertPath(grammar, "Steve speed 1", "effect.give.targets.effect.seconds");
        assertPath(grammar, "Steve speed 0.5", "effect.give.targets.effect.seconds");
        assertPath(grammar, "Steve speed abc", "effect.give.targets.effect.seconds");
    }

    @Test
    void missingTokensStopAtTheRoot() {
        assertPath(grammar, "", "effect");
        // Vanilla needs two tokens and fails with a usage error before resolving the target.
        Parse parse = assertPath(grammar, "Steve", "effect");
        assertEquals(Collections.emptyList(), entities(parse));
    }

    @Test
    void entityStepIsAnyEntityAtTokenZero() {
        assertEquals(Collections.singletonList("targets@0:ENTITIES"), entities(assertPath(grammar, "@e[type=Zombie] clear", "effect.clear.targets")));
        assertEquals(Collections.singletonList("targets@0:ENTITIES"), entities(assertPath(grammar, "@a speed 5", "effect.give.targets.effect.seconds")));
        assertEquals(Collections.singletonList("targets@0:ENTITIES"), entities(assertPath(grammar, "@p speed 0", "effect.clear.targets.effect")));
    }
}
