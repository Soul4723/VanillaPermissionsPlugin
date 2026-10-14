import io.github.soul4723.vanillapermissions.command.EffectCommandHandler;
import io.github.soul4723.vanillapermissions.util.SelectorPathBuilder;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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

        Method createEffect = EffectCommandHandler.class.getDeclaredMethod(
                "createPotionEffect", PotionEffectType.class, Integer.class, Integer.class, Boolean.class);
        createEffect.setAccessible(true);
        PotionEffect defaults = (PotionEffect) createEffect.invoke(null, PotionEffectType.SPEED, null, null, null);
        assert defaults.getDuration() == 600;
        assert defaults.getAmplifier() == 0;
        assert !defaults.isAmbient() && defaults.hasParticles() && defaults.hasIcon();

        PotionEffect configured = (PotionEffect) createEffect.invoke(null, PotionEffectType.SPEED, 12, 3, true);
        assert configured.getDuration() == 240;
        assert configured.getAmplifier() == 3;
        assert !configured.hasParticles() && !configured.hasIcon();

        Method clearEffects = EffectCommandHandler.class.getDeclaredMethod(
                "clearPotionEffects", Collection.class, PotionEffectType.class);
        clearEffects.setAccessible(true);
        LivingState active = new LivingState(true), empty = new LivingState(false);
        assert (int) clearEffects.invoke(null, List.of(active.entity, empty.entity), null) == 1;
        assert !active.hasEffect && !empty.hasEffect;
        active.hasEffect = true;
        assert (int) clearEffects.invoke(null, List.of(active.entity, empty.entity), PotionEffectType.SPEED) == 1;
        assert !active.hasEffect && !empty.hasEffect;
        System.out.println("Effect defaults, optional values, multi-target conversion and selector scope checks passed");
    }

    private static final class LivingState {
        boolean hasEffect;
        final LivingEntity entity;

        LivingState(boolean hasEffect) {
            this.hasEffect = hasEffect;
            entity = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                    new Class<?>[]{LivingEntity.class}, (proxy, method, values) -> switch (method.getName()) {
                        case "getType" -> EntityType.PLAYER;
                        case "getActivePotionEffects" -> hasEffect
                                ? List.of(new PotionEffect(PotionEffectType.SPEED, 20, 0)) : List.of();
                        case "hasPotionEffect" -> hasEffect && values[0] == PotionEffectType.SPEED;
                        case "clearActivePotionEffects" -> { boolean previous = this.hasEffect; this.hasEffect = false; yield previous; }
                        case "removePotionEffect" -> { this.hasEffect = false; yield null; }
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
