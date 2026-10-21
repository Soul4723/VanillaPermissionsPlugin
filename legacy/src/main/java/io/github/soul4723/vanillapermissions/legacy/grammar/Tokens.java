package io.github.soul4723.vanillapermissions.legacy.grammar;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Helpers for grammars: loose token matching and node path sets. */
public final class Tokens {

    private Tokens() {}

    /** Whether {@code token} equals any of {@code spellings}, ignoring case. */
    public static boolean is(String token, String... spellings) {
        if (token == null) return false;
        for (String spelling : spellings) {
            if (token.equalsIgnoreCase(spelling)) return true;
        }
        return false;
    }

    /** Whether {@code token} parses with {@code Integer.parseInt} (so {@code 01} and {@code +1} count) to {@code value}. */
    public static boolean isInt(String token, int value) {
        if (token == null) return false;
        try {
            return Integer.parseInt(token) == value;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** {@code args[index]}, or {@code null} past the end. */
    public static String at(String[] args, int index) {
        return index >= 0 && index < args.length ? args[index] : null;
    }

    /** {@code token} lower-cased with the root locale. */
    public static String lower(String token) {
        return token.toLowerCase(Locale.ROOT);
    }

    /**
     * The given paths and every prefix of each, e.g. {@code paths("time.set.day")} is {@code time}, {@code time.set},
     * {@code time.set.day}. Order follows the supplied leaves and their prefixes.
     */
    public static Set<String> paths(String... leaves) {
        Set<String> all = new LinkedHashSet<>();
        for (String leaf : leaves) addWithPrefixes(all, leaf);
        return Collections.unmodifiableSet(all);
    }

    /** Adds {@code path} and its prefixes to {@code into}. */
    public static void addWithPrefixes(Set<String> into, String path) {
        int dot = -1;
        while ((dot = path.indexOf('.', dot + 1)) >= 0) into.add(path.substring(0, dot));
        into.add(path);
    }

    /** Splits like CraftBukkit's {@code SimpleCommandMap#dispatch}: single spaces, trailing empty tokens dropped. */
    public static String[] split(String line) {
        String[] tokens = line.split(" ");
        return tokens.length == 1 && tokens[0].isEmpty() ? new String[0] : tokens;
    }
}
