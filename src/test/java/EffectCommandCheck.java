import io.github.soul4723.vanillapermissions.command.EffectCommandHandler;
import io.github.soul4723.vanillapermissions.util.SelectorPathBuilder;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Set;

// Run with assertions enabled and Maven's test classpath; no server required.
public class EffectCommandCheck {
    public static void main(String[] args) throws Exception {
        Method asEntities = EffectCommandHandler.class.getDeclaredMethod("asEntities", Object.class);
        asEntities.setAccessible(true);

        Entity player = entity(EntityType.PLAYER);
        Entity zombie = entity(EntityType.ZOMBIE);
        Collection<?> targets = (Collection<?>) asEntities.invoke(null, List.of(player, zombie));
        assert targets.size() == 2 : targets;
        assert ((Collection<?>) asEntities.invoke(null, null)).isEmpty() : "Missing targets accepted";

        assert new SelectorPathBuilder("EFFECT", "TARGETS", Set.of(player)).build()
                .equals("minecraft.selector.player.effect.targets");
        assert new SelectorPathBuilder("effect", "targets", Set.of(player, zombie)).build()
                .equals("minecraft.selector.entity.effect.targets");
        System.out.println("Effect multi-target conversion and selector scope checks passed");
    }

    private static Entity entity(EntityType type) {
        return (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(), new Class<?>[]{Entity.class},
                (proxy, method, values) -> method.getName().equals("getType")
                        ? type
                        : throwUnexpected(method));
    }

    private static Object throwUnexpected(java.lang.reflect.Method method) {
        throw new AssertionError("Unexpected Entity call: " + method);
    }
}
