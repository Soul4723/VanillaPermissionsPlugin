package io.github.soul4723.vanillapermissions.legacy.grammar;

import java.util.Set;

/**
 * {@code /effect <entity> clear} and {@code /effect <entity> <effect> [seconds] [amplifier] [hideParticles]}. Modern
 * nodes (1.21.11): {@code effect.clear.targets}, {@code effect.clear.targets.effect} and
 * {@code effect.give.targets.effect[.seconds[.amplifier[.hideParticles]]]}. The modern path order differs from the
 * typed order: the target (token 0) comes after the {@code clear} or {@code give} literal.
 *
 * <p>Verified against CommandEffect of 1.8.8 and 1.12.2 (same behaviour). Vanilla needs two tokens and otherwise fails
 * with a usage error before doing anything, so {@code /effect <entity>} stays at the root. The target is any living
 * entity. Vanilla compares {@code clear} exactly (case-sensitive); another case is looked up as an effect name and
 * fails. Matching it ignoring case only adds a check. Seconds (token 2) that {@code Integer.parseInt} reads as 0 remove
 * the effect, so they map to {@code effect.clear.targets.effect}; unparsable seconds fail in vanilla and map to
 * {@code seconds}. 1.9.4 - 1.11.2 are assumed to behave like 1.12.2.
 */
public final class EffectGrammar extends Grammar {

    private static final Set<String> PATHS = Tokens.paths(
        "effect.clear.targets.effect",
        "effect.give.targets.effect.seconds.amplifier.hideParticles");

    public EffectGrammar() {
        super(PATHS, "effect");
    }

    @Override
    public Parse parse(String command, String[] args, LegacyVersion version) {
        Parse.Builder parse = Parse.root("effect");
        if (args.length < 2) return parse.build();
        if (Tokens.is(args[1], "clear")) {
            return parse.literal("clear").entities("targets", 0).build();
        }
        if (args.length > 2 && Tokens.isInt(args[2], 0)) {
            return parse.literal("clear").entities("targets", 0).argument("effect").build();
        }
        parse.literal("give").entities("targets", 0).argument("effect");
        parse.arguments(args, 2, "seconds", "amplifier", "hideParticles");
        return parse.build();
    }
}
