package io.github.soul4723.vanillapermissions.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectorPolicyTest {
    private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final List<String> COMMAND_PATH = List.of("teleport", "targets", "destination");
    private static final String PERMISSION_PATH = "teleport.targets.targets.destination";

    private final Player source = player(SOURCE_ID);
    private final Player other = player(OTHER_ID);
    private final TestPermissions permissions = new TestPermissions();

    @Test
    void classifiesResolvedTargets() throws Exception {
        assertTrue(SelectorNativePermissions.targets(Optional.empty()).isEmpty());
        assertEquals(SelectorPolicy.Target.Kind.SCORE_HOLDER,
                SelectorNativePermissions.targets(List.of("score-holder")).iterator().next().kind());
        assertEquals(SelectorPolicy.Target.Kind.SCORE_HOLDER, SelectorNativePermissions.target(new TestScoreHolder()).kind());
        assertEquals(SelectorPolicy.Target.Kind.UNSUPPORTED, SelectorNativePermissions.target(null).kind());
        assertEquals(SelectorPolicy.Target.Kind.PLAYER, SelectorNativePermissions.target(player(SOURCE_ID)).kind());
        assertEquals(SelectorPolicy.Target.Kind.ENTITY, SelectorNativePermissions.target(entity(OTHER_ID)).kind());
        SelectorPolicy.Target nativePlayer = SelectorNativePermissions.target(new NativeEntityLike(player(SOURCE_ID)));
        assertEquals(SelectorPolicy.Target.Kind.PLAYER, nativePlayer.kind(), "Native entity classified as score holder");
        assertEquals(SOURCE_ID, nativePlayer.id());
    }

    @Test
    void permissionPathPutsArgumentAfterCommand() {
        assertEquals(PERMISSION_PATH, SelectorPolicy.selectorPath(COMMAND_PATH, "targets"));
    }

    @Test
    void selectorSyntaxUsesLuckPermsThenVanilla() {
        assertFalse(SelectorPolicy.allowSelectorSyntax(source, false, permissions));
        assertTrue(SelectorPolicy.allowSelectorSyntax(source, true, permissions));
        permissions.nodes.put("minecraft.selector", true);
        assertTrue(SelectorPolicy.allowSelectorSyntax(source, false, permissions));
        permissions.nodes.put("minecraft.selector", false);
        assertFalse(SelectorPolicy.allowSelectorSyntax(source, true, permissions));
    }

    @Test
    void scopesDefaultToAllowed() {
        assertAllowed(List.of(targetPlayer(SOURCE_ID, source)));
    }

    @Test
    void playerScopeDenialStillAllowsSelf() {
        permissions.nodes.put("minecraft.selector.player." + PERMISSION_PATH, false);
        assertAllowed(List.of(targetPlayer(SOURCE_ID, source)));
        assertDenied(List.of(targetPlayer(OTHER_ID, other)));
    }

    @Test
    void entityScopeDenial() {
        permissions.nodes.put("minecraft.selector.entity." + PERMISSION_PATH, false);
        assertDenied(List.of(SelectorNativePermissions.target(entity(OTHER_ID))));
    }

    @Test
    void selfScopeDenial() {
        permissions.nodes.put("minecraft.selector.self." + PERMISSION_PATH, false);
        assertDenied(List.of(targetPlayer(SOURCE_ID, source)));
    }

    @Test
    void scoreHoldersIgnoreScopesAndWeight() {
        permissions.nodes.put("minecraft.selector.self." + PERMISSION_PATH, false);
        permissions.nodes.put("minecraft.selector.player." + PERMISSION_PATH, false);
        permissions.meta.put(SOURCE_ID + ":minecraft.selector.weight." + PERMISSION_PATH, 2);
        assertAllowed(List.of(SelectorNativePermissions.target("score-holder")));
    }

    @Test
    void unsupportedTargetsAreAlwaysDenied() {
        assertDenied(List.of(SelectorNativePermissions.target(new Object())));
    }

    @Test
    void limitCountsWholeSelection() {
        permissions.meta.put(SOURCE_ID + ":minecraft.selector.limit." + PERMISSION_PATH, 1);
        assertDenied(List.of(targetPlayer(SOURCE_ID, source), targetPlayer(OTHER_ID, other)));
        assertDenied(List.of(SelectorNativePermissions.target("one"), SelectorNativePermissions.target("two")));
    }

    @Test
    void heavierTargetsAreDenied() {
        permissions.meta.put(SOURCE_ID + ":minecraft.selector.weight." + PERMISSION_PATH, 2);
        permissions.meta.put(OTHER_ID + ":minecraft.selector.weight." + PERMISSION_PATH, 3);
        assertDenied(List.of(targetPlayer(OTHER_ID, other)));
        permissions.meta.put(OTHER_ID + ":minecraft.selector.weight." + PERMISSION_PATH, 2);
        assertAllowed(List.of(targetPlayer(OTHER_ID, other)));
    }

    private void assertAllowed(List<SelectorPolicy.Target> targets) {
        assertDoesNotThrow(validation(targets));
    }

    private void assertDenied(List<SelectorPolicy.Target> targets) {
        assertThrows(CommandSyntaxException.class, validation(targets), "Expected selector denial");
    }

    private Executable validation(List<SelectorPolicy.Target> targets) {
        return () -> SelectorPolicy.validate(source, COMMAND_PATH, "targets", targets, permissions);
    }

    private static SelectorPolicy.Target targetPlayer(UUID id, CommandSender metadataSubject) {
        return new SelectorPolicy.Target(SelectorPolicy.Target.Kind.PLAYER, id, metadataSubject);
    }

    private static Player player(UUID id) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> method.getName().equals("getUniqueId") ? id : null);
    }

    private static Entity entity(UUID id) {
        return (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(), new Class<?>[]{Entity.class},
                (proxy, method, args) -> method.getName().equals("getUniqueId") ? id : null);
    }

    /** Native score holder with no Bukkit entity, e.g. a fake player name on the scoreboard. */
    public static final class TestScoreHolder {
        public String getScoreboardName() { return "score-holder"; }
    }

    /** Native entity that is also a score holder; must classify by its Bukkit entity. */
    public static final class NativeEntityLike {
        private final Entity bukkitEntity;

        public NativeEntityLike(Entity bukkitEntity) { this.bukkitEntity = bukkitEntity; }

        public Entity getBukkitEntity() { return bukkitEntity; }

        public String getScoreboardName() { return "native-player"; }
    }

    private static final class TestPermissions implements SelectorPolicy.Permissions {
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
