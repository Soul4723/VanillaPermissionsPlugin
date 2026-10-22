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
    private static final LegacyVersion[] ALL = GrammarAssertions.ALL_VERSIONS;

    private final ScoreboardGrammar grammar = new ScoreboardGrammar();

    private Parse sb(LegacyVersion version, String line, String expected) {
        return assertPath(grammar, version, line, expected);
    }

    @Test
    void emptyAndUnknownGroupsStopAtTheRoot() {
        for (LegacyVersion v : ALL) {
            sb(v, "", "scoreboard");
            sb(v, "bogus", "scoreboard");
            sb(v, "objectives", "scoreboard.objectives");
            sb(v, "objectives bogus x", "scoreboard.objectives");
            sb(v, "players", "scoreboard.players");
            sb(v, "players bogus Bob", "scoreboard.players");
            sb(v, "teams", "team");
            sb(v, "teams bogus", "team");
        }
    }

    @Test
    void objectives() {
        for (LegacyVersion v : ALL) {
            sb(v, "objectives list", "scoreboard.objectives.list");
            sb(v, "OBJECTIVES LIST", "scoreboard.objectives.list");
            sb(v, "objectives add", "scoreboard.objectives.add");
            sb(v, "objectives add kills", "scoreboard.objectives.add.objective");
            sb(v, "objectives add kills dummy", "scoreboard.objectives.add.objective.criteria");
            sb(v, "objectives add kills dummy Kill Count", "scoreboard.objectives.add.objective.criteria.displayName");
            sb(v, "Objectives Add kills dummy A", "scoreboard.objectives.add.objective.criteria.displayName");
            sb(v, "objectives remove", "scoreboard.objectives.remove");
            sb(v, "objectives remove kills", "scoreboard.objectives.remove.objective");
            sb(v, "objectives setdisplay", "scoreboard.objectives.setdisplay");
            sb(v, "objectives setdisplay sidebar", "scoreboard.objectives.setdisplay.slot");
            sb(v, "objectives setdisplay sidebar kills", "scoreboard.objectives.setdisplay.slot.objective");
            assertEquals(List.of(), entities(sb(v, "objectives add @a dummy", "scoreboard.objectives.add.objective.criteria")));
        }
    }

    @Test
    void playersList() {
        for (LegacyVersion v : ALL) {
            sb(v, "players list", "scoreboard.players.list");
            Parse parse = sb(v, "players list @a", "scoreboard.players.list.target");
            assertEquals(List.of("target@2:SCORE_HOLDERS"), entities(parse));
            assertEquals(List.of("scoreboard.players", "scoreboard.players.list", "scoreboard.players.list.target"),
                parse.checkedNodes());
            sb(v, "players list Bob", "scoreboard.players.list.target");
            sb(v, "players list *", "scoreboard.players.list.target");
        }
    }

    @ParameterizedTest
    @CsvSource({"set", "add", "remove", "SET", "Add"})
    void playersSetAddRemove(String op) {
        String node = "scoreboard.players." + op.toLowerCase();
        for (LegacyVersion v : ALL) {
            sb(v, "players " + op, node);
            sb(v, "players " + op + " Bob", node + ".targets");
            sb(v, "players " + op + " Bob kills", node + ".targets.objective");
            Parse parse = sb(v, "players " + op + " @a kills 5", node + ".targets.objective.score");
            assertEquals(List.of("targets@2:SCORE_HOLDERS"), entities(parse));
            // the legacy dataTag has no node and ends the path
            sb(v, "players " + op + " @a kills 5 {Tag:1b}", node + ".targets.objective.score");
            sb(v, "players " + op + " * kills -5", node + ".targets.objective.score");
        }
    }

    @Test
    void nodeNamesAreTheModernSpellingNotTheTypedOne() {
        // Vanilla's equalsIgnoreCase matches a long s (U+017F) to s and a dotless i (U+0131) to i; lower-casing does not.
        sb(V12, "players \u017Fet Bob kills 5", "scoreboard.players.set.targets.objective.score");
        sb(V12, "players re\u017Fet Bob", "scoreboard.players.reset.targets");
        sb(V12, "teams l\u0131st", "team.list");
        sb(V12, "players tag Bob ADD vip", "tag.targets.add.name");
    }

    @Test
    void playersResetAndEnable() {
        for (LegacyVersion v : ALL) {
            sb(v, "players reset", "scoreboard.players.reset");
            sb(v, "players reset Bob", "scoreboard.players.reset.targets");
            sb(v, "players reset Bob kills", "scoreboard.players.reset.targets.objective");
            assertEquals(List.of("targets@2:SCORE_HOLDERS"), entities(sb(v, "players reset @e", "scoreboard.players.reset.targets")));
            sb(v, "players enable", "scoreboard.players.enable");
            sb(v, "players enable Bob", "scoreboard.players.enable.targets");
            sb(v, "players enable Bob trig", "scoreboard.players.enable.targets.objective");
        }
    }

    @Test
    void playersOperationHasTwoScoreHolders() {
        for (LegacyVersion v : ALL) {
            sb(v, "players operation", "scoreboard.players.operation");
            sb(v, "players operation Bob", "scoreboard.players.operation.targets");
            sb(v, "players operation Bob a", "scoreboard.players.operation.targets.targetObjective");
            sb(v, "players operation Bob a +=", "scoreboard.players.operation.targets.targetObjective.operation");
            sb(v, "players operation Bob a += @p", "scoreboard.players.operation.targets.targetObjective.operation.source");
            Parse parse = sb(v, "players operation @a a >< @p b",
                "scoreboard.players.operation.targets.targetObjective.operation.source.sourceObjective");
            assertEquals(List.of("targets@2:SCORE_HOLDERS", "source@5:SCORE_HOLDERS"), entities(parse));
        }
    }

    @Test
    void playersTestHasNoModernNode() {
        for (LegacyVersion v : ALL) {
            sb(v, "players test Bob kills 1", "scoreboard.players");
            sb(v, "players test Bob kills * 5", "scoreboard.players");
            Parse parse = sb(v, "players test @a kills 1", "scoreboard.players");
            assertEquals(List.of(2), parse.selectorTokens());
            assertEquals(List.of("scoreboard.players"), parse.checkedNodes());
        }
    }

    @Test
    void playersTagIsTheModernTagCommandFromOnePointNine() {
        for (LegacyVersion v : GrammarAssertions.SINCE_1_9) {
            Parse add = sb(v, "players tag Bob add vip", "tag.targets.add.name");
            assertEquals(List.of("targets@2:ENTITIES"), entities(add));
            assertEquals(List.of("tag", "tag.targets", "tag.targets.add", "tag.targets.add.name"), add.checkedNodes());
            sb(v, "players tag Bob add vip {Foo:1b}", "tag.targets.add.name");
            sb(v, "players tag Bob remove vip", "tag.targets.remove.name");
            sb(v, "players tag @e[type=Pig] remove vip", "tag.targets.remove.name");
            Parse list = sb(v, "players tag Bob list", "tag.targets.list");
            assertEquals(List.of("targets@2:ENTITIES"), entities(list));
            sb(v, "players tag Bob add", "tag.targets.add");
            sb(v, "players tag Bob bogus x", "tag.targets");
            sb(v, "players tag Bob", "tag.targets");
            sb(v, "players tag", "tag");
            sb(v, "PLAYERS TAG Bob ADD vip", "tag.targets.add.name");
        }
    }

    @Test
    void playersTagIsParsedButNotListedOnOnePointEight() {
        assertEquals("tag.targets.add.name", grammar.parse("scoreboard",
            new String[] {"players", "tag", "Bob", "add", "vip"}).describe());
        assertEquals("tag", grammar.parse("scoreboard", new String[] {"players", "tag"}).describe());
        assertFalse(grammar.nodePaths(V8).contains("tag"));
    }

    @Test
    void teamsListAddRemoveEmpty() {
        for (LegacyVersion v : ALL) {
            Parse list = sb(v, "teams list", "team.list");
            assertEquals(List.of("team", "team.list"), list.checkedNodes());
            sb(v, "teams list red", "team.list.team");
            sb(v, "teams add", "team.add");
            sb(v, "teams add red", "team.add.team");
            sb(v, "teams add red The Red Team", "team.add.team.displayName");
            sb(v, "TEAMS ADD red", "team.add.team");
            sb(v, "teams remove", "team.remove");
            sb(v, "teams remove red", "team.remove.team");
            sb(v, "teams empty", "team.empty");
            sb(v, "teams empty red", "team.empty.team");
            assertEquals(List.of(), entities(sb(v, "teams remove @a", "team.remove.team")));
        }
    }

    @Test
    void teamsJoinAndLeave() {
        for (LegacyVersion v : ALL) {
            sb(v, "teams join", "team.join");
            // a player sender joins itself
            Parse self = sb(v, "teams join red", "team.join.team");
            assertEquals(List.of(), entities(self));
            Parse one = sb(v, "teams join red Bob", "team.join.team.members");
            assertEquals(List.of("members@3:SCORE_HOLDERS"), entities(one));
            Parse many = sb(v, "teams join red Bob @a[r=3] Carol", "team.join.team.members");
            assertEquals(List.of("members@3+3:SCORE_HOLDERS"), entities(many));
            assertEquals(List.of("team", "team.join", "team.join.team", "team.join.team.members"), many.checkedNodes());

            sb(v, "teams leave", "team.leave");
            Parse leaveOne = sb(v, "teams leave Bob", "team.leave.members");
            assertEquals(List.of("members@2:SCORE_HOLDERS"), entities(leaveOne));
            Parse leaveMany = sb(v, "teams leave Bob @e Carol Dan", "team.leave.members");
            assertEquals(List.of("members@2+4:SCORE_HOLDERS"), entities(leaveMany));
        }
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
        for (LegacyVersion v : ALL) {
            sb(v, "teams option red " + legacy, "team.modify.team." + option);
            sb(v, "teams option red " + legacy + " " + value, "team.modify.team." + modern);
        }
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
        for (LegacyVersion v : ALL) {
            sb(v, "teams option red " + legacy, "team.modify.team." + modern);
            sb(v, "teams option red " + legacy + " " + value, "team.modify.team." + modern + "." + literal);
            // an invalid value stops at the option
            sb(v, "teams option red " + legacy + " sometimes", "team.modify.team." + modern);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"always", "never", "pushOtherTeams", "pushOwnTeam"})
    void teamsOptionCollisionRuleIsParsedButNotListedOnOnePointEight(String value) {
        for (LegacyVersion v : GrammarAssertions.SINCE_1_9) {
            sb(v, "teams option red collisionRule", "team.modify.team.collisionRule");
            sb(v, "teams option red collisionRule " + value, "team.modify.team.collisionRule." + value);
        }
        assertEquals("team.modify.team.collisionRule." + value,
            grammar.parse("scoreboard", new String[] {"teams", "option", "red", "collisionRule", value}).describe());
        assertFalse(grammar.nodePaths(V8).contains("team.modify.team.collisionRule"));
    }

    @Test
    void teamsOptionStopsWhereTheTokensStop() {
        for (LegacyVersion v : ALL) {
            sb(v, "teams option", "team.modify");
            sb(v, "teams option red", "team.modify.team");
            sb(v, "teams option red glowing true", "team.modify.team");
            sb(v, "teams option red color red extra", "team.modify.team.color.value");
        }
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
