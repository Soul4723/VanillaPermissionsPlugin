package io.github.soul4723.vanillapermissions.legacy.grammar;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The grammars this adapter ships. Every other legacy command keeps only the root
 * node CraftBukkit checks, plus the {@link #MODERN_ROOTS} extra check.
 */
public final class Grammars {

    /** Shipped grammars, in stable order. */
    private static final List<Grammar> ALL = Collections.unmodifiableList(Arrays.<Grammar>asList(
        new GameModeGrammar(),
        new TimeGrammar(),
        new WeatherGrammar(),
        new KillGrammar(),
        new EffectGrammar(),
        new GiveGrammar(),
        new TeleportGrammar(),
        new ClearGrammar(),
        new ExecuteGrammar(),
        new ScoreboardGrammar()));

    /**
     * Legacy roots whose modern command has another name. CraftBukkit checks the legacy root; an explicit
     * {@code false} on the modern root denies as well, so a configuration written for modern servers keeps working.
     */
    public static final Map<String, String> MODERN_ROOTS;

    static {
        Map<String, String> roots = new HashMap<>();
        roots.put("tell", "msg");
        roots.put("xp", "experience");
        roots.put("tp", "teleport");
        MODERN_ROOTS = Collections.unmodifiableMap(roots);
    }

    private static final Map<String, Grammar> BY_COMMAND;

    static {
        Map<String, Grammar> byCommand = new HashMap<>();
        for (Grammar grammar : ALL) {
            for (String command : grammar.commands()) {
                if (byCommand.put(command, grammar) != null) throw new IllegalStateException("two grammars for " + command);
            }
        }
        BY_COMMAND = Collections.unmodifiableMap(byCommand);
    }

    private Grammars() {}

    /** The grammar for legacy command {@code name} (CraftBukkit's wrapper name), or {@code null}. */
    public static Grammar forCommand(String name) {
        return BY_COMMAND.get(name);
    }

    /**
     * Every node path the grammars for {@code commands} can produce on {@code version}, plus the modern roots of
     * {@link #MODERN_ROOTS} for those commands; without the {@code minecraft.command.} prefix.
     */
    public static Set<String> nodePaths(Collection<String> commands, LegacyVersion version) {
        Set<String> paths = new LinkedHashSet<>();
        for (String command : commands) {
            Grammar grammar = forCommand(command);
            if (grammar != null) paths.addAll(grammar.nodePaths(version));
            String modern = MODERN_ROOTS.get(command);
            if (modern != null) paths.add(modern);
        }
        return paths;
    }
}
