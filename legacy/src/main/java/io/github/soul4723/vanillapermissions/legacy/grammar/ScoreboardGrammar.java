package io.github.soul4723.vanillapermissions.legacy.grammar;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Legacy {@code /scoreboard <objectives|players|teams> ...} (1.8.8 and 1.12.2 {@code CommandScoreboard}; 1.9 - 1.11
 * assumed to be the same code as 1.12.2).
 *
 * <p>Modern names:
 * <ul>
 *   <li>{@code objectives} and {@code players} map to {@code scoreboard.objectives.*} and {@code scoreboard.players.*}
 *   (same literals; legacy {@code dataTag} and {@code players test} have no node and end the path).</li>
 *   <li>{@code teams ...} is the modern top-level {@code team} ({@code team.add.team}, {@code team.join.team.members},
 *   {@code team.modify.team.friendlyFire.allowed}, ...). Its root is checked: modern servers check
 *   {@code minecraft.command.team}.</li>
 *   <li>{@code players tag <entity> add|remove|list} (from 1.9) is the modern top-level {@code tag}
 *   ({@code tag.targets.add.name}); its root is checked too.</li>
 * </ul>
 *
 * <p>The path follows the typed tokens as far as they go, whatever the argument count: vanilla's usage errors do
 * nothing, so a longer path for an incomplete line only denies a line that fails anyway. Sub-commands compare ignoring
 * case (the {@code players tag} operation, exact in vanilla, is matched ignoring case too, which adds checks only to
 * lines vanilla rejects).
 */
public final class ScoreboardGrammar extends Grammar {

    /** Legacy team option, modern literal, and the value nodes below it (a single argument node, or the literals). */
    private static final class Option {
        final String modern;
        final String argument;
        final String[] values;
        final int since;

        Option(String modern, String argument, String[] values, int since) {
            this.modern = modern;
            this.argument = argument;
            this.values = values;
            this.since = since;
        }
    }

    private static final String[] VISIBILITY = {"always", "never", "hideForOtherTeams", "hideForOwnTeam"};
    private static final String[] COLLISION = {"always", "never", "pushOtherTeams", "pushOwnTeam"};

    private static final Option[] OPTIONS = {
        new Option("color", "value", null, 8),
        new Option("friendlyFire", "allowed", null, 8),
        new Option("seeFriendlyInvisibles", "allowed", null, 8),
        new Option("nametagVisibility", null, VISIBILITY, 8),
        new Option("deathMessageVisibility", null, VISIBILITY, 8),
        new Option("collisionRule", null, COLLISION, 9),
    };

    private static final Set<String> PATHS = Tokens.paths(
        "scoreboard.objectives.list",
        "scoreboard.objectives.add.objective.criteria.displayName",
        "scoreboard.objectives.remove.objective",
        "scoreboard.objectives.setdisplay.slot.objective",
        "scoreboard.players.list.target",
        "scoreboard.players.set.targets.objective.score",
        "scoreboard.players.add.targets.objective.score",
        "scoreboard.players.remove.targets.objective.score",
        "scoreboard.players.reset.targets.objective",
        "scoreboard.players.enable.targets.objective",
        "scoreboard.players.operation.targets.targetObjective.operation.source.sourceObjective",
        "team",
        "team.list.team",
        "team.add.team.displayName",
        "team.remove.team",
        "team.empty.team",
        "team.join.team.members",
        "team.leave.members",
        "team.modify.team");

    public ScoreboardGrammar() {
        super(PATHS, "scoreboard");
    }

    @Override
    public Set<String> nodePaths(LegacyVersion version) {
        Set<String> paths = new LinkedHashSet<>(PATHS);
        for (Option option : OPTIONS) {
            if (!version.atLeast(option.since)) continue;
            String base = "team.modify.team." + option.modern;
            if (option.argument != null) {
                Tokens.addWithPrefixes(paths, base + "." + option.argument);
            } else {
                for (String value : option.values) Tokens.addWithPrefixes(paths, base + "." + value);
            }
        }
        if (version.atLeast(9)) {
            Tokens.addWithPrefixes(paths, "tag.targets.add.name");
            Tokens.addWithPrefixes(paths, "tag.targets.remove.name");
            Tokens.addWithPrefixes(paths, "tag.targets.list");
        }
        return Collections.unmodifiableSet(paths);
    }

    @Override
    public Parse parse(String command, String[] args, LegacyVersion version) {
        String group = Tokens.at(args, 0);
        if (Tokens.is(group, "objectives")) return objectives(args);
        if (Tokens.is(group, "players")) return players(args);
        if (Tokens.is(group, "teams")) return teams(args);
        return Parse.root("scoreboard").build();
    }

    private static Parse objectives(String[] args) {
        int n = args.length;
        Parse.Builder parse = Parse.root("scoreboard").literal("objectives");
        String action = Tokens.at(args, 1);
        if (Tokens.is(action, "list")) {
            parse.literal("list");
        } else if (Tokens.is(action, "add")) {
            parse.literal("add");
            parse.arguments(args, 2, "objective", "criteria", "displayName");
        } else if (Tokens.is(action, "remove")) {
            parse.literal("remove");
            if (n > 2) parse.argument("objective");
        } else if (Tokens.is(action, "setdisplay")) {
            parse.literal("setdisplay");
            parse.arguments(args, 2, "slot", "objective");
        }
        return parse.build();
    }

    private static Parse players(String[] args) {
        int n = args.length;
        String action = Tokens.at(args, 1);
        if (Tokens.is(action, "tag")) return tag(args);
        Parse.Builder parse = Parse.root("scoreboard").literal("players");
        if (Tokens.is(action, "list")) {
            parse.literal("list");
            if (n > 2) parse.scoreHolders("target", 2);
        } else if (Tokens.is(action, "set", "add", "remove")) {
            parse.literal(Tokens.lower(action));
            if (n > 2) parse.scoreHolders("targets", 2);
            parse.arguments(args, 3, "objective", "score");
        } else if (Tokens.is(action, "reset", "enable")) {
            parse.literal(Tokens.lower(action));
            if (n > 2) parse.scoreHolders("targets", 2);
            if (n > 3) parse.argument("objective");
        } else if (Tokens.is(action, "operation")) {
            parse.literal("operation");
            if (n > 2) parse.scoreHolders("targets", 2);
            parse.arguments(args, 3, "targetObjective", "operation");
            if (n > 5) parse.scoreHolders("source", 5);
            if (n > 6) parse.argument("sourceObjective");
        } else if (Tokens.is(action, "test") && n > 2) {
            // No modern node (execute if score), but the target still accepts selectors.
            parse.selector(2);
        }
        return parse.build();
    }

    /** {@code players tag <entity> add|remove <tag> [dataTag]} or {@code ... list}: modern {@code tag}. */
    private static Parse tag(String[] args) {
        int n = args.length;
        Parse.Builder parse = Parse.root("tag").checkRoot();
        if (n > 2) parse.entities("targets", 2);
        String operation = Tokens.at(args, 3);
        if (Tokens.is(operation, "list")) {
            parse.literal("list");
        } else if (Tokens.is(operation, "add", "remove")) {
            parse.literal(Tokens.lower(operation));
            if (n > 4) parse.argument("name");
        }
        return parse.build();
    }

    /** {@code teams ...}: modern top-level {@code team}. */
    private static Parse teams(String[] args) {
        int n = args.length;
        Parse.Builder parse = Parse.root("team").checkRoot();
        String action = Tokens.at(args, 1);
        if (Tokens.is(action, "add")) {
            parse.literal("add");
            parse.arguments(args, 2, "team", "displayName");
        } else if (Tokens.is(action, "remove", "empty", "list")) {
            parse.literal(Tokens.lower(action));
            if (n > 2) parse.argument("team");
        } else if (Tokens.is(action, "join")) {
            // with only a team, a player sender joins it itself
            parse.literal("join");
            if (n > 2) parse.argument("team");
            if (n > 3) parse.scoreHolders("members", 3, n - 3);
        } else if (Tokens.is(action, "leave")) {
            parse.literal("leave");
            if (n > 2) parse.scoreHolders("members", 2, n - 2);
        } else if (Tokens.is(action, "option")) {
            parse.literal("modify");
            if (n > 2) parse.argument("team");
            option(parse, args);
        }
        return parse.build();
    }

    private static void option(Parse.Builder parse, String[] args) {
        String name = Tokens.at(args, 3);
        if (name == null) return;
        for (Option option : OPTIONS) {
            if (!Tokens.is(name, option.modern)) continue;
            parse.literal(option.modern);
            String value = Tokens.at(args, 4);
            if (value == null) return;
            if (option.argument != null) {
                parse.argument(option.argument);
                return;
            }
            for (String literal : option.values) {
                if (Tokens.is(value, literal)) {
                    parse.literal(literal);
                    return;
                }
            }
            return;
        }
    }
}
