package io.github.soul4723.vanillapermissions.legacy.grammar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.assertPath;
import static io.github.soul4723.vanillapermissions.legacy.grammar.GrammarAssertions.entities;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreboardGrammarTest {

    private static final LegacyVersion V8 = LegacyVersion.V1_8_8;
    private static final LegacyVersion V9 = LegacyVersion.V1_9_4;
    private static final LegacyVersion V10 = LegacyVersion.V1_10_2;
    private static final LegacyVersion V12 = LegacyVersion.V1_12_2;

    private final ScoreboardGrammar grammar = new ScoreboardGrammar();

    private Parse sb(String line, String expected) {
        return assertPath(grammar, line, expected);
    }

    @Test
    void emptyAndUnknownGroupsStopAtTheRoot() {
        sb("", "scoreboard");
        sb("bogus", "scoreboard");
        sb("objectives", "scoreboard.objectives");
        sb("objectives bogus x", "scoreboard.objectives");
        sb("players", "scoreboard.players");
        sb("players bogus Bob", "scoreboard.players");
        sb("teams", "team");
        sb("teams bogus", "team");
    }

    @Test
    void objectives() {
        sb("objectives list", "scoreboard.objectives.list");
        sb("OBJECTIVES LIST", "scoreboard.objectives.list");
        sb("objectives add", "scoreboard.objectives.add");
        sb("objectives add kills", "scoreboard.objectives.add.objective");
        sb("objectives add kills dummy", "scoreboard.objectives.add.objective.criteria");
        sb("objectives add kills dummy Kill Count", "scoreboard.objectives.add.objective.criteria.displayName");
        sb("Objectives Add kills dummy A", "scoreboard.objectives.add.objective.criteria.displayName");
        sb("objectives remove", "scoreboard.objectives.remove");
        sb("objectives remove kills", "scoreboard.objectives.remove.objective");
        sb("objectives setdisplay", "scoreboard.objectives.setdisplay");
        sb("objectives setdisplay sidebar", "scoreboard.objectives.setdisplay.slot");
        sb("objectives setdisplay sidebar kills", "scoreboard.objectives.setdisplay.slot.objective");
        assertEquals(List.of(), entities(sb("objectives add @a dummy", "scoreboard.objectives.add.objective.criteria")));
    }

    @Test
    void playersList() {
        sb("players list", "scoreboard.players.list");
        Parse parse = sb("players list @a", "scoreboard.players.list.target");
        assertEquals(List.of("target@2:SCORE_HOLDERS"), entities(parse));
        assertEquals(List.of("scoreboard.players", "scoreboard.players.list", "scoreboard.players.list.target"),
            parse.checkedNodes());
        sb("players list Bob", "scoreboard.players.list.target");
        sb("players list *", "scoreboard.players.list.target");
    }

    @ParameterizedTest
    @CsvSource({"set", "add", "remove", "SET", "Add"})
    void playersSetAddRemove(String op) {
        String node = "scoreboard.players." + op.toLowerCase();
        sb("players " + op, node);
        sb("players " + op + " Bob", node + ".targets");
        sb("players " + op + " Bob kills", node + ".targets.objective");
        Parse parse = sb("players " + op + " @a kills 5", node + ".targets.objective.score");
        assertEquals(List.of("targets@2:SCORE_HOLDERS"), entities(parse));
        // the legacy dataTag has no node and ends the path
        sb("players " + op + " @a kills 5 {Tag:1b}", node + ".targets.objective.score");
        sb("players " + op + " * kills -5", node + ".targets.objective.score");
    }

    @Test
    void nodeNamesAreTheModernSpellingNotTheTypedOne() {
        // Vanilla's equalsIgnoreCase matches a long s (U+017F) to s and a dotless i (U+0131) to i; lower-casing does not.
        sb("players \u017Fet Bob kills 5", "scoreboard.players.set.targets.objective.score");
        sb("players re\u017Fet Bob", "scoreboard.players.reset.targets");
        sb("teams l\u0131st", "team.list");
        sb("players tag Bob ADD vip", "tag.targets.add.name");
    }

    @Test
    void playersResetAndEnable() {
        sb("players reset", "scoreboard.players.reset");
        sb("players reset Bob", "scoreboard.players.reset.targets");
        sb("players reset Bob kills", "scoreboard.players.reset.targets.objective");
        assertEquals(List.of("targets@2:SCORE_HOLDERS"), entities(sb("players reset @e", "scoreboard.players.reset.targets")));
        sb("players enable", "scoreboard.players.enable");
        sb("players enable Bob", "scoreboard.players.enable.targets");
        sb("players enable Bob trig", "scoreboard.players.enable.targets.objective");
    }

    @Test
    void playersOperationHasTwoScoreHolders() {
        sb("players operation", "scoreboard.players.operation");
        sb("players operation Bob", "scoreboard.players.operation.targets");
        sb("players operation Bob a", "scoreboard.players.operation.targets.targetObjective");
        sb("players operation Bob a +=", "scoreboard.players.operation.targets.targetObjective.operation");
        sb("players operation Bob a += @p", "scoreboard.players.operation.targets.targetObjective.operation.source");
        Parse parse = sb("players operation @a a >< @p b",
            "scoreboard.players.operation.targets.targetObjective.operation.source.sourceObjective");
        assertEquals(List.of("targets@2:SCORE_HOLDERS", "source@5:SCORE_HOLDERS"), entities(parse));
    }

    @Test
    void playersTestHasNoModernNode() {
        sb("players test Bob kills 1", "scoreboard.players");
        sb("players test Bob kills * 5", "scoreboard.players");
        Parse parse = sb("players test @a kills 1", "scoreboard.players");
        assertEquals(List.of(2), parse.selectorTokens());
        assertEquals(List.of("scoreboard.players"), parse.checkedNodes());
    }

    @Test
    void playersTagIsTheModernTagCommandFromOnePointNine() {
        Parse add = sb("players tag Bob add vip", "tag.targets.add.name");
        assertEquals(List.of("targets@2:ENTITIES"), entities(add));
        assertEquals(List.of("tag", "tag.targets", "tag.targets.add", "tag.targets.add.name"), add.checkedNodes());
        sb("players tag Bob add vip {Foo:1b}", "tag.targets.add.name");
        sb("players tag Bob remove vip", "tag.targets.remove.name");
        sb("players tag @e[type=Pig] remove vip", "tag.targets.remove.name");
        Parse list = sb("players tag Bob list", "tag.targets.list");
        assertEquals(List.of("targets@2:ENTITIES"), entities(list));
        sb("players tag Bob add", "tag.targets.add");
        sb("players tag Bob bogus x", "tag.targets");
        sb("players tag Bob", "tag.targets");
        sb("players tag", "tag");
        sb("PLAYERS TAG Bob ADD vip", "tag.targets.add.name");
    }

    @Test
    void playersTagIsParsedButNotListedOnOnePointEight() {
        assertFalse(grammar.nodePaths(V8).contains("tag"));
    }

    @Test
    void teamsListAddRemoveEmpty() {
        Parse list = sb("teams list", "team.list");
        assertEquals(List.of("team", "team.list"), list.checkedNodes());
        sb("teams list red", "team.list.team");
        sb("teams add", "team.add");
        sb("teams add red", "team.add.team");
        sb("teams add red The Red Team", "team.add.team.displayName");
        sb("TEAMS ADD red", "team.add.team");
        sb("teams remove", "team.remove");
        sb("teams remove red", "team.remove.team");
        sb("teams empty", "team.empty");
        sb("teams empty red", "team.empty.team");
        assertEquals(List.of(), entities(sb("teams remove @a", "team.remove.team")));
    }

    @Test
    void teamsJoinAndLeave() {
        sb("teams join", "team.join");
        // a player sender joins itself
        Parse self = sb("teams join red", "team.join.team");
        assertEquals(List.of(), entities(self));
        Parse one = sb("teams join red Bob", "team.join.team.members");
        assertEquals(List.of("members@3:SCORE_HOLDERS"), entities(one));
        Parse many = sb("teams join red Bob @a[r=3] Carol", "team.join.team.members");
        assertEquals(List.of("members@3+3:SCORE_HOLDERS"), entities(many));
        assertEquals(List.of("team", "team.join", "team.join.team", "team.join.team.members"), many.checkedNodes());

        sb("teams leave", "team.leave");
        Parse leaveOne = sb("teams leave Bob", "team.leave.members");
        assertEquals(List.of("members@2:SCORE_HOLDERS"), entities(leaveOne));
        Parse leaveMany = sb("teams leave Bob @e Carol Dan", "team.leave.members");
        assertEquals(List.of("members@2+4:SCORE_HOLDERS"), entities(leaveMany));
    }

    @ParameterizedTest
    @CsvSource({
        "color, color.value, red",
        "COLOR, color.value, red",
        "friendlyfire, friendlyFire.allowed, true",
        "friendlyFire, friendlyFire.allowed, false",
        "seeFriendlyInvisibles, seeFriendlyInvisibles.allowed, true",
        "SEEFRIENDLYINVISIBLES, seeFriendlyInvisibles.allowed, false",
    })
    void teamsOptionWithArgumentValues(String legacy, String modern, String value) {
        String option = modern.substring(0, modern.indexOf('.'));
        sb("teams option red " + legacy, "team.modify.team." + option);
        sb("teams option red " + legacy + " " + value, "team.modify.team." + modern);
    }

    @ParameterizedTest
    @CsvSource({
        "nametagVisibility, nametagVisibility, hideForOtherTeams, hideForOtherTeams",
        "NAMETAGVISIBILITY, nametagVisibility, always, always",
        "nametagVisibility, nametagVisibility, NEVER, never",
        "deathMessageVisibility, deathMessageVisibility, hideForOwnTeam, hideForOwnTeam",
        "deathmessagevisibility, deathMessageVisibility, never, never",
    })
    void teamsOptionWithLiteralValues(String legacy, String modern, String value, String literal) {
        sb("teams option red " + legacy, "team.modify.team." + modern);
        sb("teams option red " + legacy + " " + value, "team.modify.team." + modern + "." + literal);
        // an invalid value stops at the option
        sb("teams option red " + legacy + " sometimes", "team.modify.team." + modern);
    }

    @ParameterizedTest
    @ValueSource(strings = {"always", "never", "pushOtherTeams", "pushOwnTeam"})
    void teamsOptionCollisionRuleIsParsedButNotListedOnOnePointEight(String value) {
        sb("teams option red collisionRule", "team.modify.team.collisionRule");
        sb("teams option red collisionRule " + value, "team.modify.team.collisionRule." + value);
        assertFalse(grammar.nodePaths(V8).contains("team.modify.team.collisionRule"));
    }

    @Test
    void teamsOptionStopsWhereTheTokensStop() {
        sb("teams option", "team.modify");
        sb("teams option red", "team.modify.team");
        sb("teams option red glowing true", "team.modify.team");
        sb("teams option red color red extra", "team.modify.team.color.value");
    }

    @Test
    void nodePathsDifferWhereTheCodeDiffers() {
        Set<String> v8 = grammar.nodePaths(V8);
        Set<String> v9 = grammar.nodePaths(V9);
        assertEquals(v9, grammar.nodePaths(V12));
        assertEquals(v9, grammar.nodePaths(V10));
        assertTrue(v9.containsAll(v8));
        Set<String> added = new java.util.TreeSet<>(v9);
        added.removeAll(v8);
        assertEquals(Set.of(
            "tag", "tag.targets", "tag.targets.add", "tag.targets.add.name", "tag.targets.remove", "tag.targets.remove.name",
            "tag.targets.list",
            "team.modify.team.collisionRule", "team.modify.team.collisionRule.always", "team.modify.team.collisionRule.never",
            "team.modify.team.collisionRule.pushOtherTeams", "team.modify.team.collisionRule.pushOwnTeam"), added);
        assertEquals(72, v8.size());
        assertEquals(84, v9.size());
    }

    @Test
    void everyPathIsAModernNode() {
        // names copied from the 1.21.11 command tree; the team options and tag are the modern top-level commands
        for (String path : grammar.nodePaths(V12)) {
            assertTrue(path.startsWith("scoreboard") || path.startsWith("team") || path.startsWith("tag"), path);
        }
        assertTrue(grammar.nodePaths(V12).contains("scoreboard.players.operation.targets.targetObjective.operation.source.sourceObjective"));
        assertTrue(grammar.nodePaths(V12).contains("team.modify.team.seeFriendlyInvisibles.allowed"));
        assertTrue(grammar.nodePaths(V12).contains("team.join.team.members"));
        assertTrue(grammar.nodePaths(V12).contains("team.leave.members"));
        assertFalse(grammar.nodePaths(V12).contains("scoreboard.players.test"));
    }
}
