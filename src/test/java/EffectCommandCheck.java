import io.github.soul4723.vanillapermissions.command.EffectCommandHandler;
import io.github.soul4723.vanillapermissions.util.SelectorPathBuilder;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

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
        assert ((Collection<?>) asEntities.invoke(null, (Object) null)).isEmpty() : "Missing targets accepted";

        assert new SelectorPathBuilder("EFFECT", "TARGETS", Set.of(player)).build()
                .equals("minecraft.selector.player.effect.targets");
        assert new SelectorPathBuilder("effect", "targets", Set.of(player, zombie)).build()
                .equals("minecraft.selector.entity.effect.targets");

        Method effectOptions = EffectCommandHandler.class.getDeclaredMethod(
                "potionEffectOptions", Integer.class, Integer.class, Boolean.class);
        effectOptions.setAccessible(true);
        Object defaults = effectOptions.invoke(null, null, null, null);
        assert intOption(defaults, "durationTicks") == 600;
        assert intOption(defaults, "amplifier") == 0;
        assert booleanOption(defaults, "showParticles");

        Object configured = effectOptions.invoke(null, 12, 3, true);
        assert intOption(configured, "durationTicks") == 240;
        assert intOption(configured, "amplifier") == 3;
        assert !booleanOption(configured, "showParticles");

        Class<?> effectType = Class.forName("org.bukkit.potion.PotionEffectType", false,
                EffectCommandCheck.class.getClassLoader());
        Method clearEffects = EffectCommandHandler.class.getDeclaredMethod(
                "clearPotionEffects", Collection.class, effectType);
        clearEffects.setAccessible(true);
        LivingState active = new LivingState(true), empty = new LivingState(false);
        assert (int) clearEffects.invoke(null, List.of(active.entity, empty.entity), null) == 1;
        assert !active.hasEffect && !empty.hasEffect;
        System.out.println("Effect defaults, optional values, multi-target conversion, clear-all and selector scope checks passed");
    }

    private static int intOption(Object options, String name) throws Exception {
        Method accessor = options.getClass().getDeclaredMethod(name);
        accessor.setAccessible(true);
        return (int) accessor.invoke(options);
    }

    private static boolean booleanOption(Object options, String name) throws Exception {
        Method accessor = options.getClass().getDeclaredMethod(name);
        accessor.setAccessible(true);
        return (boolean) accessor.invoke(options);
    }

    private static final class LivingState {
        boolean hasEffect;
        final LivingEntity entity;

        LivingState(boolean hasEffect) {
            this.hasEffect = hasEffect;
            entity = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                    new Class<?>[]{LivingEntity.class}, (proxy, method, values) -> switch (method.getName()) {
                        case "getType" -> EntityType.PLAYER;
                        case "clearActivePotionEffects" -> { boolean previous = this.hasEffect; this.hasEffect = false; yield previous; }
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == values[0];
                        default -> throwUnexpected(method);
                    });
        }
    }

    private static Entity entity(EntityType type) {
        return (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(), new Class<?>[]{Entity.class},
                (proxy, method, values) -> switch (method.getName()) {
                    case "getType" -> type;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == values[0];
                    default -> throwUnexpected(method);
                });
    }

    private static Object throwUnexpected(java.lang.reflect.Method method) {
        throw new AssertionError("Unexpected Entity call: " + method);
    }
}
