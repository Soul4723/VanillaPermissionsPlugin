package io.github.soul4723.vanillapermissions.legacy.grammar;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The grammars this adapter ships. Every other legacy command keeps only the root
 * node CraftBukkit checks, plus the {@link #modernRoot} extra check.
 */
public final class Grammars {

    /** Shipped grammars, in stable order. */
    private static final List<Grammar> ALL = Arrays.<Grammar>asList(
        new GameModeGrammar(),
        new TimeGrammar(),
        new WeatherGrammar(),
        new KillGrammar(),
        new EffectGrammar(),
        new GiveGrammar(),
        new TeleportGrammar(),
        new ClearGrammar(),
        new ExecuteGrammar(),
        new ScoreboardGrammar());

    /** Legacy roots whose modern command has another name. */
    private static final Map<String, String> MODERN_ROOTS = new HashMap<>();
    private static final Map<String, Grammar> BY_COMMAND = new HashMap<>();

    static {
        MODERN_ROOTS.put("tell", "msg");
        MODERN_ROOTS.put("xp", "experience");
        MODERN_ROOTS.put("tp", "teleport");
        for (Grammar grammar : ALL) {
            for (String command : grammar.commands()) BY_COMMAND.put(command, grammar);
        }
    }

    private Grammars() {}

    /** How many commands have a grammar (sub-command checks), e.g. for the startup line. */
    public static int count() {
        return ALL.size();
    }

    /**
     * The modern name of legacy command {@code name} where it differs, else {@code null}. CraftBukkit checks the
     * legacy root; an explicit {@code false} on the modern root denies as well, so a configuration written for modern
     * servers keeps working.
     */
    public static String modernRoot(String name) {
        return MODERN_ROOTS.get(name);
    }

    /** The grammar for legacy command {@code name} (CraftBukkit's wrapper name), or {@code null}. */
    public static Grammar forCommand(String name) {
        return BY_COMMAND.get(name);
    }

    /**
     * Every node path the grammars for {@code commands} can produce on {@code version}, plus the modern roots of
     * {@link #modernRoot} for those commands; without the {@code minecraft.command.} prefix.
     */
    public static Set<String> nodePaths(Collection<String> commands, LegacyVersion version) {
        Set<String> paths = new LinkedHashSet<>();
        for (String command : commands) {
            Grammar grammar = forCommand(command);
            if (grammar != null) paths.addAll(grammar.nodePaths(version));
            String modern = modernRoot(command);
            if (modern != null) paths.add(modern);
        }
        return paths;
    }
}
