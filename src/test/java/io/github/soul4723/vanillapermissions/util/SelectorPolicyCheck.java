package io.github.soul4723.vanillapermissions.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

public final class SelectorPolicyCheck {
    private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String PERMISSION_PATH = "teleport.targets.targets.destination";

    public static void main(String[] args) throws Exception {
        assert SelectorNativePermissions.targets(Optional.empty()).isEmpty();
        assert SelectorNativePermissions.targets(List.of("score-holder")).iterator().next().kind()
                == SelectorPolicy.Target.Kind.SCORE_HOLDER;
        assert SelectorNativePermissions.target(null).kind() == SelectorPolicy.Target.Kind.UNSUPPORTED;
        assert SelectorNativePermissions.target(player(SOURCE_ID)).kind() == SelectorPolicy.Target.Kind.PLAYER;
        assert SelectorNativePermissions.target(entity(OTHER_ID)).kind() == SelectorPolicy.Target.Kind.ENTITY;

        assert SelectorPolicy.selectorPath(List.of("teleport", "targets", "destination"), "targets")
                .equals(PERMISSION_PATH);

        Player source = player(SOURCE_ID);
        Player other = player(OTHER_ID);
        TestPermissions permissions = new TestPermissions();
        assert !SelectorPolicy.allowSelectorSyntax(source, false, permissions);
        assert SelectorPolicy.allowSelectorSyntax(source, true, permissions);
        permissions.nodes.put("minecraft.selector", true);
        assert SelectorPolicy.allowSelectorSyntax(source, false, permissions);
        permissions.nodes.put("minecraft.selector", false);
        assert !SelectorPolicy.allowSelectorSyntax(source, true, permissions);
        permissions.nodes.remove("minecraft.selector");
        SelectorPolicy.validate(source, List.of("teleport", "targets", "destination"), "targets",
                List.of(targetPlayer(SOURCE_ID, source)), permissions);

        permissions.nodes.put("minecraft.selector.player." + PERMISSION_PATH, false);
        SelectorPolicy.validate(source, List.of("teleport", "targets", "destination"), "targets",
                List.of(targetPlayer(SOURCE_ID, source)), permissions);
        expectDenied(() -> SelectorPolicy.validate(source, List.of("teleport", "targets", "destination"), "targets",
                List.of(targetPlayer(OTHER_ID, other)), permissions));

        permissions.nodes.put("minecraft.selector.player." + PERMISSION_PATH, true);
        permissions.nodes.put("minecraft.selector.entity." + PERMISSION_PATH, false);
        expectDenied(() -> SelectorPolicy.validate(source, List.of("teleport", "targets", "destination"), "targets",
                List.of(SelectorNativePermissions.target(entity(OTHER_ID))), permissions));
        permissions.nodes.put("minecraft.selector.entity." + PERMISSION_PATH, true);
        permissions.nodes.put("minecraft.selector.self." + PERMISSION_PATH, false);
        expectDenied(() -> SelectorPolicy.validate(source, List.of("teleport", "targets", "destination"), "targets",
                List.of(targetPlayer(SOURCE_ID, source)), permissions));
        permissions.nodes.put("minecraft.selector.player." + PERMISSION_PATH, false);
        permissions.meta.put(SOURCE_ID + ":minecraft.selector.weight." + PERMISSION_PATH, 2);
        SelectorPolicy.validate(source, List.of("teleport", "targets", "destination"), "targets",
                List.of(SelectorNativePermissions.target("score-holder")), permissions);
        TestPermissions unrestricted = new TestPermissions();
        expectDenied(() -> SelectorPolicy.validate(source, List.of("teleport", "targets", "destination"), "targets",
                List.of(SelectorNativePermissions.target(new Object())), unrestricted));
        permissions.nodes.put("minecraft.selector.player." + PERMISSION_PATH, true);
        permissions.nodes.put("minecraft.selector.self." + PERMISSION_PATH, true);

        permissions.meta.put(SOURCE_ID + ":minecraft.selector.limit." + PERMISSION_PATH, 1);
        expectDenied(() -> SelectorPolicy.validate(source, List.of("teleport", "targets", "destination"), "targets",
                List.of(targetPlayer(SOURCE_ID, source), targetPlayer(OTHER_ID, other)), permissions));
        expectDenied(() -> SelectorPolicy.validate(source, List.of("teleport", "targets", "destination"), "targets",
                List.of(SelectorNativePermissions.target("one"), SelectorNativePermissions.target("two")), permissions));

        permissions.meta.clear();
        permissions.meta.put(SOURCE_ID + ":minecraft.selector.weight." + PERMISSION_PATH, 2);
        permissions.meta.put(OTHER_ID + ":minecraft.selector.weight." + PERMISSION_PATH, 3);
        expectDenied(() -> SelectorPolicy.validate(source, List.of("teleport", "targets", "destination"), "targets",
                List.of(targetPlayer(OTHER_ID, other)), permissions));
        permissions.meta.put(OTHER_ID + ":minecraft.selector.weight." + PERMISSION_PATH, 2);
        SelectorPolicy.validate(source, List.of("teleport", "targets", "destination"), "targets",
                List.of(targetPlayer(OTHER_ID, other)), permissions);
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

    private static void expectDenied(ThrowingRunnable action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected selector denial");
        } catch (CommandSyntaxException expected) {
            // Expected denial.
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class TestPermissions implements SelectorPolicy.Permissions {
        private final Map<String, Boolean> nodes = new java.util.HashMap<>();
        private final Map<String, Integer> meta = new java.util.HashMap<>();

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
