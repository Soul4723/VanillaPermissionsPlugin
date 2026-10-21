package io.github.soul4723.vanillapermissions.legacy.grammar;

import java.util.Set;

/**
 * {@code /clear [player] [item] [data] [maxCount] [dataTag]}. Modern nodes: {@code clear} (the sender itself),
 * {@code clear.targets}, {@code clear.targets.item} and {@code clear.targets.item.maxCount}. The legacy {@code data}
 * (token 2) has no node of its own, so the path stays at {@code item}; {@code maxCount} is token 3; {@code dataTag}
 * (token 4 on) has no node and ends the path.
 *
 * <p>Verified against CommandClear of 1.8.8 and 1.12.2 (same indexes): player is token 0 (players only). 1.9.4 - 1.11.2
 * are assumed to behave the same.
 */
public final class ClearGrammar extends Grammar {

    private static final Set<String> PATHS = Tokens.paths("clear.targets.item.maxCount");

    public ClearGrammar() {
        super(PATHS, "clear");
    }

    @Override
    public Parse parse(String command, String[] args, LegacyVersion version) {
        Parse.Builder parse = Parse.root("clear");
        if (args.length > 0) parse.players("targets", 0);
        if (args.length > 1) parse.argument("item");
        if (args.length > 3) parse.argument("maxCount");
        return parse.build();
    }
}
