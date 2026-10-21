package io.github.soul4723.vanillapermissions.selector;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The platform-free selector rules; the Brigadier wrapper is covered by SelectorPolicyTest. */
class SelectorRulesTest {
    private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final List<String> COMMAND_PATH = List.of("teleport", "targets", "destination");
    private static final String PERMISSION_PATH = "teleport.targets.targets.destination";

    private final Player source = player(SOURCE_ID);
    private final Player other = player(OTHER_ID);
    private final TestPermissions permissions = new TestPermissions();

    @Test
    void classifiesBukkitEntities() {
        SelectorRules.Target self = SelectorRules.Target.entity(source);
        assertEquals(SelectorRules.Target.Kind.PLAYER, self.kind());
        assertEquals(SOURCE_ID, self.id());
        assertEquals(source, self.metadataSubject());
        assertEquals(SelectorRules.Target.Kind.ENTITY, SelectorRules.Target.entity(entity(OTHER_ID)).kind());
        assertEquals(SelectorRules.Target.Kind.SCORE_HOLDER, SelectorRules.Target.scoreHolder().kind());
        assertEquals(SelectorRules.Target.Kind.UNSUPPORTED, SelectorRules.Target.unsupported().kind());
        assertThrows(IllegalArgumentException.class, () -> new SelectorRules.Target(SelectorRules.Target.Kind.PLAYER, null, null));
        assertThrows(IllegalArgumentException.class, () -> new SelectorRules.Target(SelectorRules.Target.Kind.ENTITY, null, null));
    }

    @Test
    void permissionPathPutsArgumentAfterCommand() {
        assertEquals(PERMISSION_PATH, SelectorRules.selectorPath(COMMAND_PATH, "targets"));
        assertThrows(IllegalArgumentException.class, () -> SelectorRules.selectorPath(List.of(), "targets"));
        assertThrows(IllegalArgumentException.class, () -> SelectorRules.selectorPath(COMMAND_PATH, " "));
    }

    @Test
    void selectorSyntaxUsesLuckPermsThenVanilla() {
        assertFalse(SelectorRules.allowSelectorSyntax(source, false, permissions));
        assertTrue(SelectorRules.allowSelectorSyntax(source, true, permissions));
        permissions.nodes.put("minecraft.selector", true);
        assertTrue(SelectorRules.allowSelectorSyntax(source, false, permissions));
        permissions.nodes.put("minecraft.selector", false);
        assertFalse(SelectorRules.allowSelectorSyntax(source, true, permissions));
    }

    @Test
    void nonPlayerSendersAreNeverRestricted() {
        CommandSender console = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class}, (proxy, method, args) -> null);
        permissions.nodes.put("minecraft.selector.player." + PERMISSION_PATH, false);
        assertEquals(Optional.empty(), SelectorRules.check(console, COMMAND_PATH, "targets",
                List.of(SelectorRules.Target.unsupported()), permissions));
    }

    @Test
    void scopesDefaultToAllowed() {
        assertAllowed(List.of(targetPlayer(SOURCE_ID, source)));
    }

    @Test
    void playerScopeDenialStillAllowsSelf() {
        permissions.nodes.put("minecraft.selector.player." + PERMISSION_PATH, false);
        assertAllowed(List.of(targetPlayer(SOURCE_ID, source)));
        assertDenied(List.of(targetPlayer(OTHER_ID, other)), SelectorDenial.Reason.PLAYER,
                "You do not have permission to target other players here");
    }

    @Test
    void entityScopeDenial() {
        permissions.nodes.put("minecraft.selector.entity." + PERMISSION_PATH, false);
        assertDenied(List.of(SelectorRules.Target.entity(entity(OTHER_ID))), SelectorDenial.Reason.ENTITY,
                "You do not have permission to target non-player entities here");
    }

    @Test
    void selfScopeDenial() {
        permissions.nodes.put("minecraft.selector.self." + PERMISSION_PATH, false);
        assertDenied(List.of(targetPlayer(SOURCE_ID, source)), SelectorDenial.Reason.SELF,
                "You do not have permission to target yourself here");
    }

    @Test
    void scoreHoldersIgnoreScopesAndWeight() {
        permissions.nodes.put("minecraft.selector.self." + PERMISSION_PATH, false);
        permissions.nodes.put("minecraft.selector.player." + PERMISSION_PATH, false);
        permissions.meta.put(SOURCE_ID + ":minecraft.selector.weight." + PERMISSION_PATH, 2);
        assertAllowed(List.of(SelectorRules.Target.scoreHolder()));
    }

    @Test
    void unsupportedTargetsAreAlwaysDenied() {
        assertDenied(List.of(SelectorRules.Target.unsupported()), SelectorDenial.Reason.UNSUPPORTED,
                "This target cannot be checked against selector permissions, so it is not allowed");
    }

    @Test
    void limitCountsWholeSelection() {
        permissions.meta.put(SOURCE_ID + ":minecraft.selector.limit." + PERMISSION_PATH, 1);
        assertDenied(List.of(targetPlayer(SOURCE_ID, source), targetPlayer(OTHER_ID, other)), SelectorDenial.Reason.TOO_MANY,
                "You can select at most 1 target here");
        assertDenied(List.of(SelectorRules.Target.scoreHolder(), SelectorRules.Target.scoreHolder()), SelectorDenial.Reason.TOO_MANY,
                "You can select at most 1 target here");
        permissions.meta.put(SOURCE_ID + ":minecraft.selector.limit." + PERMISSION_PATH, 0);
        assertEquals("You can select at most 0 targets here", denial(List.of(targetPlayer(SOURCE_ID, source))).message());
        assertEquals(0, denial(List.of(targetPlayer(SOURCE_ID, source))).limit());
    }

    @Test
    void heavierTargetsAreDenied() {
        permissions.meta.put(SOURCE_ID + ":minecraft.selector.weight." + PERMISSION_PATH, 2);
        permissions.meta.put(OTHER_ID + ":minecraft.selector.weight." + PERMISSION_PATH, 3);
        assertDenied(List.of(targetPlayer(OTHER_ID, other)), SelectorDenial.Reason.WEIGHT,
                "You do not have permission to target players with a higher selector weight than yours");
        permissions.meta.put(OTHER_ID + ":minecraft.selector.weight." + PERMISSION_PATH, 2);
        assertAllowed(List.of(targetPlayer(OTHER_ID, other)));
    }

    @Test
    void tooManyNeedsALimit() {
        assertThrows(IllegalArgumentException.class, () -> SelectorDenial.of(SelectorDenial.Reason.TOO_MANY));
        assertEquals("You can select at most 3 targets here", SelectorDenial.tooMany(3).message());
        assertEquals(SelectorDenial.Reason.TOO_MANY, SelectorDenial.tooMany(3).reason());
    }

    private void assertAllowed(List<SelectorRules.Target> targets) {
        assertEquals(Optional.empty(), check(targets));
    }

    private void assertDenied(List<SelectorRules.Target> targets, SelectorDenial.Reason reason, String message) {
        SelectorDenial denial = denial(targets);
        assertEquals(reason, denial.reason());
        assertEquals(message, denial.message());
    }

    private SelectorDenial denial(List<SelectorRules.Target> targets) {
        Optional<SelectorDenial> denial = check(targets);
        assertTrue(denial.isPresent(), "Expected selector denial");
        return denial.get();
    }

    private Optional<SelectorDenial> check(List<SelectorRules.Target> targets) {
        return SelectorRules.check(source, COMMAND_PATH, "targets", targets, permissions);
    }

    private static SelectorRules.Target targetPlayer(UUID id, CommandSender metadataSubject) {
        return new SelectorRules.Target(SelectorRules.Target.Kind.PLAYER, id, metadataSubject);
    }

    private static Player player(UUID id) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> null;
                });
    }

    private static Entity entity(UUID id) {
        return (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(), new Class<?>[]{Entity.class},
                (proxy, method, args) -> method.getName().equals("getUniqueId") ? id : null);
    }

    private static final class TestPermissions implements SelectorRules.Permissions {
        private final Map<String, Boolean> nodes = new HashMap<>();
        private final Map<String, Integer> meta = new HashMap<>();

        @Override
        public boolean hasPermission(CommandSender sender, String node, boolean fallback) {
            return nodes.getOrDefault(node, fallback);
        }

        @Override
        public OptionalInt getIntegerMeta(UUID subjectId, CommandSender onlineSubject, String node) {
            Integer value = meta.get(subjectId + ":" + node);
            return value == null ? OptionalInt.empty() : OptionalInt.of(value);
        }
    }
}
