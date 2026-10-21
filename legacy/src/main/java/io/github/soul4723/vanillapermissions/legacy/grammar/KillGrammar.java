package io.github.soul4723.vanillapermissions.legacy.grammar;

import java.util.Set;

/**
 * {@code /kill [target]}. Modern nodes: {@code kill} (the sender itself) and {@code kill.targets}.
 *
 * <p>Verified against CommandKill of 1.8.8 and 1.12.2: no tokens kills the sender, token 0 is any entity (name, UUID
 * or selector; {@code isListStart} index 0). Vanilla ignores further tokens. 1.9.4 - 1.11.2 are assumed the same.
 */
public final class KillGrammar extends Grammar {

    private static final Set<String> PATHS = Tokens.paths("kill.targets");

    public KillGrammar() {
        super(PATHS, "kill");
    }

    @Override
    public Parse parse(String command, String[] args, LegacyVersion version) {
        Parse.Builder parse = Parse.root("kill");
        if (args.length > 0) parse.entities("targets", 0);
        return parse.build();
    }
}
