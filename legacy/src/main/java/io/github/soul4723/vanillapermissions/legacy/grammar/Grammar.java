package io.github.soul4723.vanillapermissions.legacy.grammar;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Reads the arguments of one pre-1.13 vanilla command and names them like the modern (1.13+) Brigadier tree, so that a
 * node such as {@code minecraft.command.gamemode.creative.target} means the same on 1.8 as on 1.21.
 *
 * <p>Rules every grammar follows:
 * <ul>
 *   <li>Node names are the modern ones (Minecraft 1.21.11's command tree, as the {@code paper} adapter sees it). Legacy
 *   spellings are aliases of the modern literal ({@code c}, {@code 1}, {@code 01} are {@code creative}), never nodes of
 *   their own.</li>
 *   <li>Match at least as much as vanilla's parser does, never less: compare literals ignoring case, accept every number
 *   format {@code Integer.parseInt} accepts, and accept any token where vanilla takes an argument. Matching more only
 *   adds a check to input vanilla rejects anyway; matching less silently skips a check.</li>
 *   <li>Stop at the deepest node the typed tokens reach; tokens that have no modern node (legacy {@code dataTag},
 *   {@code data}) end the path.</li>
 *   <li>Be pure: no server calls, so every grammar is unit-tested with plain token arrays.</li>
 * </ul>
 */
public abstract class Grammar {

    private final Set<String> paths;
    private final List<String> commands;

    protected Grammar(Set<String> paths, String... commands) {
        this.paths = paths;
        this.commands = Collections.unmodifiableList(Arrays.asList(commands));
    }

    /**
     * The legacy command names this grammar reads, as CraftBukkit's {@code VanillaCommandWrapper#getName()} returns
     * them (the label without {@code minecraft:}), e.g. {@code tp} and {@code teleport}.
     */
    public final List<String> commands() {
        return commands;
    }

    /**
     * Every node path advertised on {@code version}, including each root and every prefix, without
     * the {@code minecraft.command.} prefix. Counted in the adapter's start-up log line.
     * Parsing also checks newer syntax on older versions; vanilla rejects it if unavailable.
     */
    public Set<String> nodePaths(LegacyVersion version) {
        return paths;
    }

    /**
     * Maps typed arguments to modern nodes.
     *
     * @param command the legacy command name, one of {@link #commands()}
     * @param args the tokens after the label, split on single spaces like CraftBukkit does (may contain empty tokens)
     * @param version the running server's version
     * @return the deepest path the tokens reach; at least the root
     */
    public abstract Parse parse(String command, String[] args, LegacyVersion version);
}
