package io.github.soul4723.vanillapermissions.brigadier;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.soul4723.vanillapermissions.selector.SelectorRules;
import io.github.soul4723.vanillapermissions.util.TestLuckPerms;
import net.luckperms.api.util.Tristate;
import net.minecraft.server.players.NameAndId;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectorTargetsTest {
    private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final List<String> COMMAND_PATH = List.of("teleport", "targets", "destination");
    private static final String PATH = "teleport.targets.targets.destination";

    @Test
    void classifiesResolvedTargets() throws Exception {
        assertTrue(SelectorTargets.targets(Optional.empty(), SelectorTargets::target).isEmpty());
        assertEquals(SelectorRules.Target.Kind.SCORE_HOLDER,
                SelectorTargets.targets(List.of("score-holder"), SelectorTargets::target).iterator().next().kind());
        assertEquals(SelectorRules.Target.Kind.SCORE_HOLDER, SelectorTargets.target(new TestScoreHolder()).kind());
        assertEquals(SelectorRules.Target.Kind.UNSUPPORTED, SelectorTargets.target(null).kind());
        assertEquals(SelectorRules.Target.Kind.PLAYER, SelectorTargets.target(player(SOURCE_ID)).kind());
        assertEquals(SelectorRules.Target.Kind.ENTITY, SelectorTargets.target(entity(OTHER_ID)).kind());
        SelectorRules.Target nativePlayer = SelectorTargets.target(new NativeEntityLike(player(SOURCE_ID)));
        assertEquals(SelectorRules.Target.Kind.PLAYER, nativePlayer.kind(), "Native entity classified as score holder");
        assertEquals(SOURCE_ID, nativePlayer.id());
    }

    /** /op, /ban, /whitelist and /pardon resolve game profiles; from 1.21.9 as NameAndId records. */
    @Test
    void classifiesGameProfilesByTheirId() {
        assertEquals(OTHER_ID, SelectorTargets.profileId(new GameProfile(OTHER_ID, "Steve")));
        assertEquals(OTHER_ID, SelectorTargets.profileId(new NameAndId(OTHER_ID, "Steve")));
        assertNull(SelectorTargets.profileId(new TestScoreHolder()));
    }

    @Test
    void deniedSelectionsFailWithTheRulesMessage() throws Exception {
        List<Entity> zombies = List.of(entity(OTHER_ID));
        SelectorTargets.validate(player(SOURCE_ID), COMMAND_PATH, "targets", zombies, zombies, SelectorTargets::target);
        try (TestLuckPerms luckPerms = new TestLuckPerms()) {
            luckPerms.nodes.put("minecraft.selector.entity." + PATH, Tristate.FALSE);
            CommandSyntaxException error = assertThrows(CommandSyntaxException.class, () -> SelectorTargets.validate(
                    player(SOURCE_ID), COMMAND_PATH, "targets", zombies, zombies, SelectorTargets::target));
            assertEquals("You do not have permission to target non-player entities here", error.getMessage());
        }
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
}
