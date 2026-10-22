package io.github.soul4723.vanillapermissions.legacy.grammar;

import java.util.Set;

/**
 * {@code /give <player> <item> [amount] [data] [dataTag]}. Modern nodes: {@code give.targets}, {@code give.targets.item}
 * and {@code give.targets.item.count}. The legacy {@code data} (token 3) and {@code dataTag} (token 4 on) have no
 * modern node and end the path.
 *
 * <p>Verified against CommandGive of 1.8.8 and 1.12.2: player is token 0 (players only), item token 1, amount token 2.
 * The amount cap differs (1 - 64 on 1.8.8, the item's stack size on 1.12.2) but that does not change the nodes.
 * Vanilla needs two tokens; with only the player it fails with a usage error, but {@code give.targets} is still
 * reported, which only adds a check. 1.9.4 - 1.11.2 are assumed to behave like 1.12.2.
 */
public final class GiveGrammar extends Grammar {

    private static final Set<String> PATHS = Tokens.paths("give.targets.item.count");

    public GiveGrammar() {
        super(PATHS, "give");
    }

    @Override
    public Parse parse(String command, String[] args) {
        Parse.Builder parse = Parse.root("give");
        if (args.length > 0) parse.players("targets", 0);
        parse.arguments(args, 1, "item", "count");
        return parse.build();
    }
}
