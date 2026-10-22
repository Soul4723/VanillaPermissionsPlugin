package io.github.soul4723.vanillapermissions.legacy.grammar;

import java.util.Set;

/**
 * {@code /gamemode <mode> [player]}. Modern nodes follow the plugin's literal split ({@code NativeGameModeTree}):
 * {@code gamemode.<mode>} and {@code gamemode.<mode>.target}.
 *
 * <p>1.8.8 compares mode names ignoring case, 1.12.2 exactly; both accept the short forms {@code s c a sp} and the
 * numbers 0 - 3 through {@code Integer.parseInt} (so {@code 01} and {@code +1}). Matching ignoring case covers both.
 */
public final class GameModeGrammar extends Grammar {

    private static final String[][] MODES = {
        {"survival", "s"},
        {"creative", "c"},
        {"adventure", "a"},
        {"spectator", "sp"},
    };

    private static final Set<String> PATHS = Tokens.paths(
        "gamemode.survival.target", "gamemode.creative.target", "gamemode.adventure.target", "gamemode.spectator.target");

    public GameModeGrammar() {
        super(PATHS, "gamemode");
    }

    @Override
    public Parse parse(String command, String[] args) {
        Parse.Builder parse = Parse.root("gamemode");
        String mode = mode(Tokens.at(args, 0));
        if (mode == null) return parse.build();
        parse.literal(mode);
        if (args.length > 1) parse.players("target", 1);
        return parse.build();
    }

    /** The modern mode name for a typed mode, or {@code null} if vanilla would not accept it. */
    static String mode(String token) {
        if (token == null) return null;
        for (int i = 0; i < MODES.length; i++) {
            if (Tokens.is(token, MODES[i]) || Tokens.isInt(token, i)) return MODES[i][0];
        }
        return null;
    }
}
