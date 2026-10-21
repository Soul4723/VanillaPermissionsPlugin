package io.github.soul4723.vanillapermissions.selector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Selectors that pick their targets at random ({@code @r}, and {@code sort=random} from 1.13). The checks resolve a
 * selector before the command runs and the server resolves it again, so a random selector may hit other targets than
 * the ones checked. For those, the checks look at every target the selector could pick instead: the same selector
 * without its count limit and random order.
 */
public final class RandomSelectors {

    private RandomSelectors() {}

    /**
     * The selector that finds every target {@code token} could pick, or {@code null} if {@code token} is not a
     * selector that picks at random. From 1.13 {@code @r} becomes {@code @a} and the {@code limit} and {@code sort}
     * options are dropped; before 1.13 ({@code legacy}) {@code @r} becomes {@code @a}, or {@code @e} with a
     * {@code type} option, and the {@code c} (count) option is dropped.
     */
    public static String candidates(String token, boolean legacy) {
        if (token == null || token.length() < 2 || token.charAt(0) != '@') return null;
        char kind = token.charAt(1);
        String rest = token.substring(2);
        List<String> options = new ArrayList<>();
        if (!rest.isEmpty()) {
            if (rest.charAt(0) != '[' || rest.charAt(rest.length() - 1) != ']') return null;
            options = splitOptions(rest.substring(1, rest.length() - 1));
            if (options == null) return null;
        }

        boolean random = kind == 'r';
        boolean typed = false;
        List<String> kept = new ArrayList<>();
        for (String option : options) {
            String key = key(option);
            if (key.equals("type")) typed = true;
            if (legacy) {
                if (key.equals("c")) continue;
            } else {
                if (key.equals("sort")) {
                    if (value(option).equalsIgnoreCase("random")) random = true;
                    continue;
                }
                if (key.equals("limit")) continue;
            }
            kept.add(option);
        }
        if (!random) return null;

        // 1.13+ @r selects players only, like @a; before 1.13 a type option lets it select other entities.
        char poolKind = kind != 'r' ? kind : legacy && typed ? 'e' : 'a';
        StringBuilder pool = new StringBuilder("@").append(poolKind);
        if (!kept.isEmpty()) pool.append('[').append(String.join(",", kept)).append(']');
        return pool.toString();
    }

    /** Top-level comma-separated options, ignoring commas inside quotes and {@code {}} or {@code []}; null if unbalanced. */
    static List<String> splitOptions(String options) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        char quote = 0;
        int start = 0;
        for (int i = 0; i < options.length(); i++) {
            char c = options.charAt(i);
            if (quote != 0) {
                if (c == '\\') i++;
                else if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                if (--depth < 0) return null;
            } else if (c == ',' && depth == 0) {
                parts.add(options.substring(start, i));
                start = i + 1;
            }
        }
        if (depth != 0 || quote != 0) return null;
        String last = options.substring(start);
        if (!last.trim().isEmpty() || !parts.isEmpty()) parts.add(last);
        return parts;
    }

    private static String key(String option) {
        int equals = option.indexOf('=');
        return (equals < 0 ? "" : option.substring(0, equals)).trim().toLowerCase(Locale.ROOT);
    }

    private static String value(String option) {
        int equals = option.indexOf('=');
        return equals < 0 ? "" : option.substring(equals + 1).trim();
    }
}
