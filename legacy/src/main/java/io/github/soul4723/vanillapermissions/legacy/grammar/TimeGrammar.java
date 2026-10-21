package io.github.soul4723.vanillapermissions.legacy.grammar;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@code /time <set|add|query> <value>}. Modern nodes as in Minecraft 1.21.11: {@code time.set.day},
 * {@code time.set.night}, {@code time.set.time}, {@code time.add.time}, {@code time.query.daytime|gametime|day}.
 * (26.x reworked {@code /time}; its {@code time.set.timemarker} and {@code time.query.time} have no legacy form.)
 *
 * <p>Vanilla compares the keywords exactly and lower-case; any other {@code set} value is parsed as a number, so
 * every other token maps to {@code time.set.time}.
 */
public final class TimeGrammar extends Grammar {

    private static final Set<String> PATHS = Tokens.paths(
        "time.set.day", "time.set.night", "time.set.time", "time.add.time", "time.query.daytime", "time.query.gametime");
    private static final Set<String> PATHS_1_12;

    static {
        Set<String> paths = new LinkedHashSet<>(PATHS);
        Tokens.addWithPrefixes(paths, "time.query.day");
        PATHS_1_12 = Collections.unmodifiableSet(paths);
    }

    public TimeGrammar() {
        super(PATHS, "time");
    }

    @Override
    public Set<String> nodePaths(LegacyVersion version) {
        return version.atLeast(12) ? PATHS_1_12 : PATHS;
    }

    @Override
    public Parse parse(String command, String[] args, LegacyVersion version) {
        Parse.Builder parse = Parse.root("time");
        String action = Tokens.at(args, 0);
        String value = Tokens.at(args, 1);
        if (Tokens.is(action, "set")) {
            parse.literal("set");
            if (value == null) return parse.build();
            if (Tokens.is(value, "day")) parse.literal("day");
            else if (Tokens.is(value, "night")) parse.literal("night");
            else parse.argument("time");
        } else if (Tokens.is(action, "add")) {
            parse.literal("add");
            if (value != null) parse.argument("time");
        } else if (Tokens.is(action, "query")) {
            parse.literal("query");
            if (Tokens.is(value, "daytime")) parse.literal("daytime");
            else if (Tokens.is(value, "gametime")) parse.literal("gametime");
            else if (Tokens.is(value, "day")) parse.literal("day");
        }
        return parse.build();
    }
}
