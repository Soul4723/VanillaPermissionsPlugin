package io.github.soul4723.vanillapermissions.legacy;

import io.github.soul4723.vanillapermissions.legacy.grammar.Tokens;
import io.github.soul4723.vanillapermissions.selector.SelectorRules;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.UUID;

import static io.github.soul4723.vanillapermissions.util.TestLuckPerms.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The interception logic against a fake command map, resolver and permission source. */
class CommandGuardTest {

    private final Map<String, Integer> meta = new HashMap<>();
    private final Map<String, Boolean> nodes = new HashMap<>();
    private final Player alice = player("Alice", UUID.fromString("00000000-0000-0000-0000-00000000000a"));
    private final Player bob = player("Bob", UUID.fromString("00000000-0000-0000-0000-00000000000b"));
    private final Pig pig = proxy(Pig.class,
        (proxy, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> UUID.fromString("00000000-0000-0000-0000-0000000000ff");
            case "toString" -> "Pig";
            case "hashCode" -> 7;
            case "equals" -> proxy == args[0];
            default -> null;
        });
    private final FakeServer server = new FakeServer();
    private final CommandGuard guard = new CommandGuard(server, new FakePermissions());

    CommandGuardTest() {
        for (String name : List.of("gamemode", "time", "kill", "execute", "tell", "xp", "say", "tp", "scoreboard")) server.vanilla(name);
        server.commands.put("msg", server.commands.get("tell"));
        server.commands.put("w", server.commands.get("tell"));
        nodes.put("minecraft.command.gamemode", true);
        nodes.put("minecraft.command.time", true);
        nodes.put("minecraft.command.kill", true);
        nodes.put("minecraft.command.execute", true);
        nodes.put("minecraft.command.tell", true);
        nodes.put("minecraft.command.xp", true);
        nodes.put("minecraft.command.tp", true);
    }

    @Test
    void deniedSubNodeRefusesEverySpellingAndTheNamespacedLabel() {
        nodes.put("minecraft.command.gamemode.creative", false);
        for (String line : List.of("gamemode creative", "gamemode c", "gamemode 1", "gamemode 01", "minecraft:gamemode creative",
                "GAMEMODE c Bob")) {
            assertEquals(CommandGuard.NO_PERMISSION, guard.check(alice, line), line);
        }
        assertNull(guard.check(alice, "gamemode survival"));
        assertNull(guard.check(alice, "gamemode a Bob"));
    }

    @Test
    void unsetNodesFallBackToVanilla() {
        assertNull(guard.check(alice, "gamemode creative Bob"));
        assertNull(guard.check(alice, "time set 1000"));
    }

    @Test
    void targetNodeOnlyDeniesTheTargetedForm() {
        nodes.put("minecraft.command.gamemode.survival.target", false);
        assertNull(guard.check(alice, "gamemode survival"));
        assertEquals(CommandGuard.NO_PERMISSION, guard.check(alice, "gamemode s Bob"));
    }

    @Test
    void missingRootIsLeftToCraftBukkit() {
        nodes.remove("minecraft.command.gamemode");
        nodes.put("minecraft.command.gamemode.creative", false);
        assertNull(guard.check(alice, "gamemode creative"), "CraftBukkit refuses with its own message");
    }

    @Test
    void pluginCommandsAndUnknownLabelsAreIgnored() {
        server.commands.put("gm", new Command("gm") {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                return true;
            }
        });
        nodes.put("minecraft.command.gamemode.creative", false);
        assertNull(guard.check(alice, "gm creative"));
        assertNull(guard.check(alice, "nosuch creative"));
        assertNull(guard.check(alice, ""));
    }

    @Test
    void modernRootsDenyTellAndXp() {
        assertNull(guard.check(alice, "tell Bob hi"));
        nodes.put("minecraft.command.msg", false);
        assertEquals(CommandGuard.NO_PERMISSION, guard.check(alice, "tell Bob hi"));
        assertEquals(CommandGuard.NO_PERMISSION, guard.check(alice, "msg Bob hi"));
        assertEquals(CommandGuard.NO_PERMISSION, guard.check(alice, "w Bob hi"));
        assertNull(guard.check(alice, "xp 5 Bob"));
        nodes.put("minecraft.command.experience", false);
        assertEquals(CommandGuard.NO_PERMISSION, guard.check(alice, "xp 5 Bob"));
    }

    @Test
    void selectorSyntaxNeedsTheSelectorNodeOrVanillaPermission() {
        server.targets.put("@a", List.of(alice, bob));
        assertEquals(CommandGuard.SELECTOR_DENIED, guard.check(alice, "gamemode c @a"));
        nodes.put("minecraft.command.selector", true); // CraftBukkit's node: vanilla's answer when minecraft.selector is unset
        assertNull(guard.check(alice, "gamemode c @a"));
        nodes.put("minecraft.selector", false);
        assertEquals(CommandGuard.SELECTOR_DENIED, guard.check(alice, "gamemode c @a"));
        nodes.remove("minecraft.command.selector");
        nodes.put("minecraft.selector", true);
        assertNull(guard.check(alice, "gamemode c @a"));
    }

    @Test
    void selectorScopeUsesTheModernSelectorPath() {
        nodes.put("minecraft.selector", true);
        server.targets.put("@a", List.of(alice, bob));
        server.targets.put("@p", List.of(alice));
        nodes.put("minecraft.selector.player.gamemode.target.creative.target", false);
        assertEquals("You can't target other players here.", guard.check(alice, "gamemode c @a"));
        assertEquals("You can't target other players here.", guard.check(alice, "gamemode creative Bob"));
        assertNull(guard.check(alice, "gamemode c @p"));
        assertNull(guard.check(alice, "gamemode adventure @a"), "another path");
        nodes.put("minecraft.selector.entity.kill.targets.targets", false);
        server.targets.put("@e[type=Pig]", List.of(pig));
        assertEquals("You can't target non-player entities here.", guard.check(alice, "kill @e[type=Pig]"));
    }

    @Test
    void randomSelectorsAreCheckedAgainstEveryPlayerTheyCouldPick() {
        nodes.put("minecraft.selector", true);
        server.targets.put("@r", List.of(alice));
        server.targets.put("@a", List.of(alice, bob));
        assertNull(guard.check(alice, "kill @r"));
        nodes.put("minecraft.selector.player.kill.targets.targets", false);
        // This resolution picked Alice herself, but vanilla resolves @r again and may pick Bob.
        assertEquals("You can't target other players here.", guard.check(alice, "kill @r"));
        assertEquals(List.of("@r players=false from Alice", "@a players=false from Alice"),
            server.resolved.subList(server.resolved.size() - 2, server.resolved.size()));

        // The count limit still applies to what is picked, not to the pool.
        nodes.remove("minecraft.selector.player.kill.targets.targets");
        meta.put("minecraft.selector.limit.kill.targets", 1);
        assertNull(guard.check(alice, "kill @r"));
        // Unresolvable pools fail closed like any selector.
        server.targets.put("@r[team=red]", List.of(alice));
        assertEquals(CommandGuard.UNRESOLVED, guard.check(alice, "kill @r[team=red]"));
    }

    @Test
    void unresolvableSelectorFailsClosed() {
        nodes.put("minecraft.selector", true);
        assertEquals(CommandGuard.UNRESOLVED, guard.check(alice, "kill @e[broken"));
    }

    @Test
    void playersOnlyArgumentsResolveAsPlayers() {
        nodes.put("minecraft.selector", true);
        server.targets.put("@e", List.of(alice, bob));
        guard.check(alice, "gamemode c @e");
        assertEquals(List.of("@e players=true from Alice"), server.resolved);
        server.resolved.clear();
        guard.check(alice, "kill @e");
        assertEquals(List.of("@e players=false from Alice"), server.resolved);
    }

    @Test
    void executeChecksItsTargetsAndTheNestedCommandForTheCaller() {
        nodes.put("minecraft.selector", true);
        server.targets.put("@e[type=Pig]", List.of(pig));
        server.targets.put("@p", List.of(alice));
        nodes.put("minecraft.command.gamemode.creative", false);
        assertEquals(CommandGuard.NO_PERMISSION, guard.check(alice, "execute Bob ~ ~ ~ gamemode creative"));
        assertEquals(CommandGuard.NO_PERMISSION, guard.check(alice, "execute Bob ~ ~ ~ detect ~ ~-1 ~ stone 0 minecraft:gamemode c"));
        assertNull(guard.check(alice, "execute Bob ~ ~ ~ gamemode survival"));

        server.resolved.clear();
        assertNull(guard.check(alice, "execute @e[type=Pig] ~ ~ ~ kill @p"));
        assertEquals(List.of("@e[type=Pig] players=false from Alice", "@p players=false from Pig"), server.resolved,
            "nested selectors resolve from each /execute target");

        nodes.put("minecraft.selector.player.execute.targets.as.targets", false);
        assertEquals("You can't target other players here.", guard.check(alice, "execute Bob ~ ~ ~ say hi"));
        nodes.put("minecraft.command.execute.run", false);
        assertEquals(CommandGuard.NO_PERMISSION, guard.check(alice, "execute @p ~ ~ ~ say hi"));
    }

    @Test
    void executeStripsALeadingSlashFromItsInnerCommand() {
        nodes.put("minecraft.selector", true);
        server.targets.put("@s", List.of(alice));
        nodes.put("minecraft.command.gamemode.creative", false);
        assertEquals(CommandGuard.NO_PERMISSION, guard.check(alice, "execute @s ~ ~ ~ /gamemode creative"));
    }

    @Test
    void executeHonoursCommandBlockOverridesWithoutChangingTypedCommands() {
        Command vanilla = server.commands.get("gamemode");
        server.commands.put("gamemode", new Command("gamemode") {
            @Override public boolean execute(CommandSender sender, String label, String[] args) { return true; }
        });
        server.override = true;
        nodes.put("minecraft.command.gamemode.creative", false);
        assertNull(guard.check(alice, "gamemode creative"));
        assertEquals(CommandGuard.NO_PERMISSION, guard.check(alice, "execute Alice ~ ~ ~ gamemode creative"));
        assertEquals(vanilla, server.commands.get("minecraft:gamemode"));
        server.override = false;
        assertNull(guard.check(alice, "execute Alice ~ ~ ~ gamemode creative"));
    }

    @Test
    void executeOverrideStillChecksNamespacedCommandsAndAliases() {
        server.override = true; // commands.yml command-block-overrides: ['*']
        nodes.put("minecraft.command.gamemode.creative", false);
        assertEquals(CommandGuard.NO_PERMISSION, guard.check(alice, "execute Alice ~ ~ ~ minecraft:gamemode creative"));

        Command alias = new Command("gmc") {
            @Override public boolean execute(CommandSender sender, String label, String[] args) { return true; }
        };
        server.commands.put("gmc", alias);
        server.aliases.put(alias, List.of("gamemode creative"));
        assertEquals(CommandGuard.NO_PERMISSION, guard.check(alice, "execute Alice ~ ~ ~ gmc"));

        // A namespaced plugin command is not a vanilla override candidate either.
        server.commands.put("minecraft:gmc", new Command("gmc") {
            @Override public boolean execute(CommandSender sender, String label, String[] args) { return true; }
        });
        assertEquals(CommandGuard.NO_PERMISSION, guard.check(alice, "execute Alice ~ ~ ~ gmc"));
    }

    @Test
    void teleportRootDenialAppliesToTpUnlessExplicitlyGranted() {
        nodes.remove("minecraft.command.tp");
        nodes.put("minecraft.command.teleport", false);
        server.commands.get("tp").setPermission(null); // Isolate our extra root check from CraftBukkit's check.
        assertEquals(CommandGuard.NO_PERMISSION, guard.check(alice, "tp Bob"));
        assertEquals(List.of(), guard.filterCompletions(alice, "/tp ", List.of("Bob")));
        nodes.put("minecraft.command.tp", true);
        assertNull(guard.check(alice, "tp Bob"));
    }

    @Test
    void executeRefusesOffsetSelectorsButKeepsNonSelectorCommands() {
        nodes.put("minecraft.selector", true);
        server.targets.put("@e", List.of());
        assertEquals(CommandGuard.NO_PERMISSION, guard.check(alice, "execute Alice ~ ~ ~1000 kill @e"));
        assertEquals(CommandGuard.NO_PERMISSION, guard.check(alice, "execute Alice 0 ~ ~ kill @e"));
        assertNull(guard.check(alice, "execute Alice ~ ~ ~ kill @e"));
        assertNull(guard.check(alice, "execute Alice ~ ~ ~1000 gamemode survival"));
    }

    @Test
    void executeOffsetCannotHideASelectorInsideAnAliasOrNestedExecute() {
        nodes.put("minecraft.selector", true);
        nodes.put("minecraft.selector.entity.kill.targets.targets", false);
        Command alias = new Command("nearby") {
            @Override public boolean execute(CommandSender sender, String label, String[] args) { return true; }
        };
        server.commands.put("nearby", alias);
        server.aliases.put(alias, List.of("kill @e[type=pig,r=5]"));
        server.targets.put("@e[type=pig,r=5]", List.of(pig));
        assertEquals("You can't target non-player entities here.", guard.check(alice, "nearby"));

        server.targets.put("@e[type=pig,r=5]", List.of());
        assertNull(guard.check(alice, "execute Alice ~ ~ ~ nearby"));
        assertEquals(CommandGuard.NO_PERMISSION, guard.check(alice, "execute Alice ~ ~ ~1000 nearby"));
        assertEquals(CommandGuard.NO_PERMISSION, guard.check(alice,
                "execute Alice ~ ~ ~1000 execute Alice ~ ~ ~ nearby"));
    }

    @Test
    void wildcardScoreHoldersCountEveryTrackedEntryForTheLimit() {
        nodes.put("minecraft.command.scoreboard", true);
        server.holders = List.of("Alice", "Bob", "#fake");
        meta.put("minecraft.selector.limit.scoreboard.targets.players.set.targets.objective.score", 2);
        assertEquals("You can target at most 2 players or entities here.", guard.check(alice, "scoreboard players set * obj 0"));
        server.holders = List.of("Alice", "Bob");
        assertNull(guard.check(alice, "scoreboard players set * obj 0"));
        server.holders = List.of();
        assertNull(guard.check(alice, "scoreboard players set * obj 0"));
    }

    @Test
    void wildcardTagTargetsEveryTrackedEntity() {
        nodes.put("minecraft.command.scoreboard", true);
        nodes.put("minecraft.selector.player.tag.targets.targets.add.name", false);
        server.holders = List.of("Alice");
        assertNull(guard.check(alice, "scoreboard players tag * add vip"));
        server.holders = List.of("Alice", "Bob");
        assertEquals("You can't target other players here.",
            guard.check(alice, "scoreboard players tag * add vip"));
        server.targets.put("*", List.of()); // a plain name elsewhere, which matches nobody
        assertNull(guard.check(alice, "kill *"), "only /scoreboard has the wildcard");
    }

    @Test
    void scoreboardPlayersTestChecksItsSelectorPosition() {
        nodes.put("minecraft.command.scoreboard", true);
        nodes.put("minecraft.selector", false);
        assertEquals(CommandGuard.SELECTOR_DENIED, guard.check(alice, "scoreboard players test @a obj 0"));
        assertNull(guard.check(alice, "scoreboard players test Alice obj 0"));
    }

    @Test
    void literalAtSignArgumentsAreNotSelectorsEvenInsideExecute() {
        nodes.put("minecraft.command.scoreboard", true);
        nodes.put("minecraft.selector", false);
        assertNull(guard.check(alice, "scoreboard objectives add @example dummy"));
        assertNull(guard.check(alice, "execute Alice ~ ~ ~ scoreboard objectives add @example dummy"));
        assertNull(guard.check(alice, "execute Alice ~ ~ ~1000 scoreboard objectives add @example dummy"));
        assertEquals(CommandGuard.SELECTOR_DENIED,
                guard.check(alice, "execute Alice ~ ~ ~ scoreboard players test @a obj 1"));
    }

    @Test
    void commandLookupPreservesTheRawTokenForTheServerLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            nodes.put("minecraft.command.time.set.day", false);
            assertEquals(CommandGuard.NO_PERMISSION, guard.check(alice, "TİME set day"));
            assertEquals(List.of(), guard.filterCompletions(alice, "/TİME set ", List.of("day")));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void nestingDeeperThanTheLimitIsRefused() {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i <= CommandGuard.MAX_DEPTH; i++) line.append("execute Alice ~ ~ ~ ");
        line.append("say hi");
        assertEquals(CommandGuard.NO_PERMISSION, guard.check(alice, line.toString()));
    }

    @Test
    void commandsYmlAliasesAreExpandedAndChecked() {
        Command alias = new Command("gmc") {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                return true;
            }
        };
        server.commands.put("gmc", alias);
        server.aliases.put(alias, List.of("/say switching", "gamemode creative"));
        assertNull(guard.check(alice, "gmc"));
        nodes.put("minecraft.command.gamemode.creative", false);
        assertEquals(CommandGuard.NO_PERMISSION, guard.check(alice, "gmc"));
    }

    @Test
    void tabCompletionDropsDeniedLiterals() {
        nodes.put("minecraft.command.gamemode.creative", false);
        List<String> modes = List.of("survival", "creative", "adventure", "spectator");
        assertEquals(List.of("survival", "adventure", "spectator"), guard.filterCompletions(alice, "/gamemode ", modes));
        assertEquals(List.of(), guard.filterCompletions(alice, "/minecraft:gamemode c", List.of("creative")));
        assertEquals(modes, guard.filterCompletions(alice, "/gm ", modes), "not a vanilla command");
        nodes.put("minecraft.command.time.set.time", false);
        assertEquals(List.of("day", "night"), guard.filterCompletions(alice, "/time set ", List.of("day", "night", "1000")));
    }

    @Test
    void tabCompletionFiltersTheCommandExecuteRuns() {
        nodes.put("minecraft.command.gamemode.creative", false);
        List<String> modes = List.of("survival", "creative");
        assertEquals(List.of("survival"), guard.filterCompletions(alice, "/execute @s ~ ~ ~ gamemode ", modes));
        assertEquals(List.of("survival"), guard.filterCompletions(alice, "/execute @s ~ ~ ~ execute @p ~ ~ ~ gamemode ", modes));
        nodes.put("minecraft.command.execute.run", false);
        assertEquals(List.of(), guard.filterCompletions(alice, "/execute @s ~ ~ ~ gamemode ", modes));
    }

    @Test
    void tabCompletionLooksEachNodeUpOncePerEvent() {
        Map<String, Integer> lookups = new HashMap<>();
        CommandGuard counting = new CommandGuard(server, new FakePermissions() {
            @Override
            public boolean hasPermission(CommandSender sender, String node, boolean fallback) {
                lookups.merge(node, 1, Integer::sum);
                return super.hasPermission(sender, node, fallback);
            }
        });
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 200; i++) names.add("Player" + i);
        assertEquals(names, counting.filterCompletions(alice, "/tp ", names));
        assertTrue(lookups.values().stream().allMatch(count -> count == 1), "Repeated lookups: " + lookups);
    }

    @Test
    void splitMatchesCraftBukkit() {
        assertEquals(List.of("gamemode", "", "c"), Arrays.asList(Tokens.split("gamemode  c ")));
        assertEquals(List.of(), Arrays.asList(Tokens.split("")));
    }

    private Player player(String name, UUID id) {
        return proxy(Player.class,
            (proxy, method, args) -> switch (method.getName()) {
                case "getName", "toString" -> name;
                case "getUniqueId" -> id;
                case "hasPermission" -> Boolean.TRUE.equals(nodes.get(String.valueOf(args[0])));
                case "isPermissionSet" -> nodes.containsKey(String.valueOf(args[0]));
                case "hashCode" -> id.hashCode();
                case "equals" -> proxy == args[0];
                default -> null;
            });
    }

    private class FakePermissions implements SelectorRules.Permissions {
        @Override
        public boolean hasPermission(CommandSender sender, String node, boolean fallback) {
            Boolean value = nodes.get(node);
            return value == null ? fallback : value;
        }

        @Override
        public OptionalInt getIntegerMeta(UUID subjectId, CommandSender onlineSubject, String node) {
            Integer value = meta.get(node);
            return value == null ? OptionalInt.empty() : OptionalInt.of(value);
        }
    }

    private final class FakeServer implements CommandGuard.Server {
        boolean override;
        List<String> holders = List.of();
        final Map<String, Command> commands = new HashMap<>();
        final List<Command> vanilla = new ArrayList<>();
        final Map<Command, List<String>> aliases = new HashMap<>();
        final Map<String, List<Entity>> targets = new HashMap<>();
        final List<String> resolved = new ArrayList<>();

        void vanilla(String name) {
            Command command = new Command(name) {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    return true;
                }
            };
            command.setPermission("minecraft.command." + name);
            vanilla.add(command);
            commands.put(name, command);
            commands.put("minecraft:" + name, command);
        }

        @Override
        public Command command(String label) {
            return commands.get(label.toLowerCase(Locale.getDefault()));
        }

        @Override public boolean commandBlockOverride(String token) { return override; }

        @Override public List<String> scoreHolders() { return holders; }

        @Override
        public boolean isVanilla(Command command) {
            return vanilla.contains(command);
        }

        @Override
        public List<String> expandAlias(Command command, CommandSender sender, String[] args) {
            return aliases.get(command);
        }

        @Override
        public List<Entity> resolve(Entity origin, String token, boolean playersOnly) {
            if (token.startsWith("@")) resolved.add(token + " players=" + playersOnly + " from " + origin);
            if (token.equals("Alice")) return List.of(alice);
            if (token.equals("Bob")) return List.of(bob);
            return targets.get(token);
        }
    }
}
