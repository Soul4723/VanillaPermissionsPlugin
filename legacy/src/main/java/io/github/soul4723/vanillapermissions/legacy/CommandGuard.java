package io.github.soul4723.vanillapermissions.legacy;

import io.github.soul4723.vanillapermissions.legacy.grammar.Grammar;
import io.github.soul4723.vanillapermissions.legacy.grammar.Grammars;
import io.github.soul4723.vanillapermissions.legacy.grammar.Parse;
import io.github.soul4723.vanillapermissions.legacy.grammar.Tokens;
import io.github.soul4723.vanillapermissions.selector.RandomSelectors;
import io.github.soul4723.vanillapermissions.selector.SelectorDenial;
import io.github.soul4723.vanillapermissions.selector.SelectorRules;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Checks a command line a player typed, before CraftBukkit dispatches it, against the modern node names its grammar
 * maps it to. Only commands that resolve to CraftBukkit's {@code VanillaCommandWrapper} are touched; the wrappers
 * themselves are never replaced (command blocks and {@code /execute} test {@code instanceof
 * VanillaCommandWrapper}).
 */
final class CommandGuard {

    /** Vanilla's {@code commands.generic.permission} text, which a refused sub-command shows on these versions. */
    static final String NO_PERMISSION = "You do not have permission to use this command.";
    static final String SELECTOR_DENIED = SelectorDenial.SYNTAX_DENIED;
    static final String UNRESOLVED = SelectorDenial.UNRESOLVED;
    /** Nested {@code /execute} and {@code commands.yml} alias levels checked before giving up (and denying). */
    static final int MAX_DEPTH = 8;

    /** What the guard needs from the server; the real one is {@link LegacyServer}. */
    interface Server {
        /** The command a label resolves to right now ({@code minecraft:} prefix included), or {@code null}. */
        Command command(String label);

        boolean commandBlockOverride(String token);

        /** The names the main scoreboard tracks, which {@code *} stands for in {@code /scoreboard}. */
        List<String> scoreHolders();

        /** Whether {@code command} is CraftBukkit's {@code VanillaCommandWrapper}. */
        boolean isVanilla(Command command);

        /**
         * The lines a {@code commands.yml} alias expands to for these arguments, or {@code null} if {@code command}
         * is not such an alias.
         */
        List<String> expandAlias(Command command, CommandSender sender, String[] args);

        /**
         * The entities an entity argument token selects, as vanilla would resolve it from {@code origin}.
         *
         * @return the entities (empty if a plain name matches nobody), or {@code null} if they could not be worked
         *     out, which the guard treats as a denial
         */
        List<Entity> resolve(Entity origin, String token, boolean playersOnly);
    }

    private final Server server;
    private final SelectorRules.Permissions permissions;

    CommandGuard(Server server, SelectorRules.Permissions permissions) {
        this.server = server;
        this.permissions = permissions;
    }

    /** Checks a typed line without its slash. Returns the message to show, or {@code null} to let it run. */
    String check(Player player, String line) {
        return check(player, player, line, 0, false);
    }

    /**
     * @param origin the entity selectors are resolved from: the player, or an {@code /execute} target
     * @param offsetContext whether an enclosing execute uses coordinates the selector resolver cannot represent
     * @param depth nesting level of {@code /execute} and aliases
     */
    private String check(Player player, Entity origin, String line, int depth, boolean offsetContext) {
        if (depth > MAX_DEPTH) return NO_PERMISSION;
        String[] tokens = Tokens.split(line);
        if (tokens.length == 0) return null;
        String label = tokens[0];
        String[] args = Arrays.copyOfRange(tokens, 1, tokens.length);
        Command command = server.command(label);
        if (command == null) return null;

        List<String> expanded = server.expandAlias(command, player, args);
        if (expanded != null) {
            // commands.yml aliases dispatch their lines without a PlayerCommandPreprocessEvent; check each line here,
            // as EssentialsX does in EssentialsPlayerListener#onPlayerCommandPreprocess.
            for (String expandedLine : expanded) {
                String denial = check(player, origin, stripSlash(expandedLine), depth + 1, offsetContext);
                if (denial != null) return denial;
            }
            return null;
        }
        if (!server.isVanilla(command)) return null;
        // Without the root node CraftBukkit refuses with its own message; do not mask it.
        if (!command.testPermissionSilent(player)) return null;

        String name = command.getName();
        Map<String, Boolean> deniedNodes = new HashMap<>();
        if (modernRootDenied(player, name, deniedNodes)) return NO_PERMISSION;

        Grammar grammar = Grammars.forCommand(name);
        if (grammar == null) return null;
        Parse parse = grammar.parse(name, args);
        for (int token : parse.selectorTokens()) {
            if (!args[token].startsWith("@")) continue;
            if (offsetContext) return NO_PERMISSION;
            if (!SelectorRules.allowSelectorSyntax(player,
                    player.hasPermission("minecraft.command.selector"), permissions)) return SELECTOR_DENIED;
        }
        if (!nodesAllowed(player, parse.checkedNodes(), deniedNodes)) return NO_PERMISSION;

        List<Entity> executeTargets = null;
        for (Parse.Segment segment : parse.segments()) {
            for (Parse.Step step : segment.steps()) {
                if (!step.kind().isEntity()) continue;
                List<Entity> entities = new ArrayList<>();
                String denial = checkTargets(player, origin, segment, step, args, name.equals("scoreboard"), entities);
                if (denial != null) return denial;
                if (executeTargets == null) executeTargets = entities;
            }
        }

        if (parse.innerCommandStart() >= 0) {
            String inner = stripSlash(join(args, parse.innerCommandStart()).trim()).trim();
            boolean innerOffset = offsetContext || !args[1].equals("~") || !args[2].equals("~") || !args[3].equals("~");
            String[] innerTokens = Tokens.split(inner);
            if (innerTokens.length > 0 && server.commandBlockOverride(innerTokens[0])) {
                Command candidate = server.command("minecraft:" + innerTokens[0]);
                if (candidate != null && server.isVanilla(candidate)) inner = "minecraft:" + inner;
            }
            // Vanilla runs the nested command once per target, with selectors relative to that target.
            List<Entity> callees = executeTargets == null || executeTargets.isEmpty()
                ? Collections.singletonList(origin) : executeTargets;
            for (Entity callee : callees) {
                String denial = check(player, callee, inner, depth + 1, innerOffset);
                if (denial != null) return denial;
            }
        }
        return null;
    }

    /** Applies the selector rules to one entity argument and adds what it selects to {@code entities}. */
    private String checkTargets(Player player, Entity origin, Parse.Segment segment, Parse.Step step, String[] args,
                                boolean wildcard, List<Entity> entities) {
        List<SelectorRules.Target> targets = new ArrayList<>();
        List<SelectorRules.Target> candidates = new ArrayList<>();
        int end = Math.min(args.length, step.token() + step.width());
        for (int i = step.token(); i < end; i++) {
            String token = args[i];
            boolean selector = token.startsWith("@");
            if (step.kind() == Parse.Kind.SCORE_HOLDERS && !selector) {
                // Scoreboard names are not entities, so scope and weight rules skip them.
                int count = token.equals("*") ? server.scoreHolders().size() : 1;
                for (int holder = 0; holder < count; holder++) targets.add(SelectorRules.Target.scoreHolder());
                continue;
            }
            if (wildcard && token.equals("*")) {
                // Vanilla runs the command once per tracked name and looks each up as an entity (players tag).
                for (String holder : server.scoreHolders()) {
                    List<Entity> resolved = server.resolve(origin, holder, step.kind() == Parse.Kind.PLAYERS);
                    if (resolved == null) return UNRESOLVED;
                    for (Entity entity : resolved) targets.add(SelectorRules.Target.entity(entity));
                }
                continue;
            }
            List<Entity> resolved = server.resolve(origin, token, step.kind() == Parse.Kind.PLAYERS);
            if (resolved == null) return UNRESOLVED;
            for (Entity entity : resolved) targets.add(SelectorRules.Target.entity(entity));
            // Vanilla resolves @r again when the command runs, so check (and run /execute for) everyone it could pick.
            String pool = RandomSelectors.candidates(token, true);
            if (pool != null) {
                resolved = server.resolve(origin, pool, step.kind() == Parse.Kind.PLAYERS);
                if (resolved == null) return UNRESOLVED;
                for (Entity entity : resolved) candidates.add(SelectorRules.Target.entity(entity));
            }
            entities.addAll(resolved);
        }
        Optional<SelectorDenial> denial = SelectorRules.check(player, segment.names(), step.name(), targets,
            candidates.isEmpty() ? targets : candidates, permissions);
        return denial.isPresent() ? denial.get().message() : null;
    }

    /**
     * Drops tab completions that would make the line refused. {@code buffer} is the typed line with its slash and
     * the partial last token; {@code completions} complete that last token. Node lookups are memoised for the call,
     * because every candidate (one per online player for {@code /tp }) re-checks mostly the same nodes.
     */
    List<String> filterCompletions(Player player, String buffer, List<String> completions) {
        return filterCompletions(player, buffer, completions, new HashMap<String, Boolean>(), 0);
    }

    private List<String> filterCompletions(Player player, String buffer, List<String> completions,
                                           Map<String, Boolean> deniedNodes, int depth) {
        if (completions == null || completions.isEmpty() || !buffer.startsWith("/") || depth > MAX_DEPTH) return completions;
        String[] tokens = buffer.substring(1).split(" ", -1);
        if (tokens.length < 2) return completions;
        Command command = server.command(tokens[0]);
        if (command == null || !server.isVanilla(command) || !command.testPermissionSilent(player)) return completions;
        if (modernRootDenied(player, command.getName(), deniedNodes)) return Collections.emptyList();
        Grammar grammar = Grammars.forCommand(command.getName());
        if (grammar == null) return completions;
        String[] args = Arrays.copyOfRange(tokens, 1, tokens.length);
        Parse typed = grammar.parse(command.getName(), args);
        // The last token belongs to the command /execute runs: filter it as that command's completion, once the
        // /execute part itself (which no longer depends on the last token) is allowed.
        if (typed.innerCommandStart() >= 0 && typed.innerCommandStart() < args.length - 1) {
            if (!nodesAllowed(player, typed.checkedNodes(), deniedNodes)) return Collections.emptyList();
            return filterCompletions(player, "/" + join(args, typed.innerCommandStart()), completions, deniedNodes, depth + 1);
        }
        String[] probe = args.clone();
        List<String> allowed = new ArrayList<>(completions.size());
        for (String completion : completions) {
            probe[probe.length - 1] = completion;
            if (nodesAllowed(player, grammar.parse(command.getName(), probe).checkedNodes(), deniedNodes)) {
                allowed.add(completion);
            }
        }
        return allowed;
    }

    /** The modern root of a renamed legacy command is denied; an explicit {@code minecraft.command.tp} keeps /tp. */
    private boolean modernRootDenied(Player player, String name, Map<String, Boolean> deniedNodes) {
        String modernRoot = Grammars.modernRoot(name);
        return modernRoot != null && denied(player, modernRoot, deniedNodes)
            && !(name.equals("tp") && permissions.hasPermission(player, "minecraft.command.tp", false));
    }

    private boolean nodesAllowed(Player player, List<String> nodes, Map<String, Boolean> deniedNodes) {
        for (String node : nodes) {
            if (denied(player, node, deniedNodes)) return false;
        }
        return true;
    }

    private boolean denied(Player player, String node, Map<String, Boolean> memo) {
        Boolean known = memo.get(node);
        if (known == null) {
            known = denied(player, node);
            memo.put(node, known);
        }
        return known;
    }

    /** An explicit {@code false} on {@code minecraft.command.<node>}; unset falls back to vanilla, which allows. */
    private boolean denied(Player player, String node) {
        return !permissions.hasPermission(player, "minecraft.command." + node, true);
    }

    private static String stripSlash(String line) {
        return line.startsWith("/") ? line.substring(1) : line;
    }

    private static String join(String[] args, int from) {
        return String.join(" ", Arrays.asList(args).subList(from, args.length));
    }
}
