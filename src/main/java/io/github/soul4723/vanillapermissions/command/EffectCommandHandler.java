package io.github.soul4723.vanillapermissions.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.BooleanArgument;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.PotionEffectArgument;
import io.github.soul4723.vanillapermissions.VanillaPermissionsPlugin;
import io.github.soul4723.vanillapermissions.util.PermissionManager;
import io.github.soul4723.vanillapermissions.util.SelectorPathBuilder;
import io.github.soul4723.vanillapermissions.util.SelectorPermissionCheck;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;
import java.util.HashSet;

public class EffectCommandHandler {

    private static final String BASE_PERM = "minecraft.command.effect";

    public static void registerCommands(JavaPlugin plugin) {
        boolean selectorPermissions = ((VanillaPermissionsPlugin) plugin).isFeatureEnabled("selector_permissions");
        try {
            CommandAPI.unregister("effect");
        } catch (Exception e) {
            plugin.getLogger().fine("Effect command not previously registered");
        }

        new CommandAPICommand("effect")
                .withSubcommands(giveCommand(selectorPermissions), clearCommand(selectorPermissions))
                .register();
    }

    private static CommandAPICommand giveCommand(boolean selectorPermissions) {
        EntitySelectorArgument.ManyEntities targetsArgument = new EntitySelectorArgument.ManyEntities("targets");
        return new CommandAPICommand("give")
                .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, BASE_PERM + ".give"))
                .withArguments(targetsArgument, new PotionEffectArgument("effect"))
                .withOptionalArguments(
                        new IntegerArgument("seconds", 0, 1_000_000),
                        new IntegerArgument("amplifier", 0, 255),
                        new BooleanArgument("hideParticles")
                )
                .executesNative((proxy, args) -> {
                    CommandSender sender = proxy.getCaller();
                    if (!requirePermission(sender, "give")) {
                        return;
                    }
                    Collection<Entity> targets = asEntities(args.get("targets"));
                    if (!checkTargets(sender, args, targetsArgument, targets, selectorPermissions)) {
                        return;
                    }

                    PotionEffectType type = (PotionEffectType) args.get("effect");
                    PotionEffect effect = createPotionEffect(
                            type, (Integer) args.get("seconds"), (Integer) args.get("amplifier"), (Boolean) args.get("hideParticles"));
                    int applied = 0;
                    for (Entity target : targets) {
                        if (target instanceof LivingEntity living && living.addPotionEffect(effect)) {
                            applied++;
                        }
                    }

                    if (applied == 0) {
                        sender.sendMessage("§cNo target effects changed");
                    } else {
                        sender.sendMessage("§aApplied " + type.getKey() + " to " + applied + " entit" + (applied == 1 ? "y" : "ies"));
                    }
                });
    }

    private static CommandAPICommand clearCommand(boolean selectorPermissions) {
        EntitySelectorArgument.ManyEntities targetsArgument = new EntitySelectorArgument.ManyEntities("targets");
        PotionEffectArgument effectArgument = new PotionEffectArgument("effect");
        return new CommandAPICommand("clear")
                .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, BASE_PERM + ".clear"))
                .withArguments(targetsArgument)
                .withOptionalArguments(effectArgument)
                .executesNative((proxy, args) -> {
                    CommandSender sender = proxy.getCaller();
                    if (!requirePermission(sender, "clear")) {
                        return;
                    }
                    Collection<Entity> targets = asEntities(args.get("targets"));
                    if (!checkTargets(sender, args, targetsArgument, targets, selectorPermissions)) {
                        return;
                    }

                    PotionEffectType type = (PotionEffectType) args.get("effect");
                    int affected = clearPotionEffects(targets, type);

                    if (affected == 0) {
                        sender.sendMessage("§cNo target effects changed");
                    } else if (type == null) {
                        sender.sendMessage("§aCleared all effects from " + affected + " entit" + (affected == 1 ? "y" : "ies"));
                    } else {
                        sender.sendMessage("§aCleared " + type.getKey() + " from " + affected + " entit" + (affected == 1 ? "y" : "ies"));
                    }
                });
    }

    static PotionEffect createPotionEffect(PotionEffectType type, Integer seconds, Integer amplifier, Boolean hideParticles) {
        int durationTicks = (seconds == null ? 30 : seconds) * 20;
        int effectAmplifier = amplifier == null ? 0 : amplifier;
        boolean showParticles = !Boolean.TRUE.equals(hideParticles);
        return new PotionEffect(type, durationTicks, effectAmplifier, false, showParticles, showParticles);
    }

    static int clearPotionEffects(Collection<Entity> targets, PotionEffectType type) {
        int affected = 0;
        for (Entity target : targets) {
            if (target instanceof LivingEntity living) {
                if (type == null && living.clearActivePotionEffects()) {
                    affected++;
                } else if (type != null && living.hasPotionEffect(type)) {
                    living.removePotionEffect(type);
                    affected++;
                }
            }
        }
        return affected;
    }

    private static boolean requirePermission(CommandSender sender, String action) {
        if (PermissionManager.hasPermissionOrParent(sender, BASE_PERM + "." + action)) {
            return true;
        }
        sender.sendMessage("§cYou don't have permission to use /effect " + action + ".");
        return false;
    }

    private static boolean checkTargets(
            CommandSender sender,
            dev.jorel.commandapi.executors.CommandArguments args,
            EntitySelectorArgument.ManyEntities targetsArgument,
            Collection<Entity> targets,
            boolean selectorPermissions) {
        if (targets.isEmpty()) {
            sender.sendMessage("§cTarget entity not found");
            return false;
        }
        if (!selectorPermissions) {
            return true;
        }
        if (!SelectorPermissionCheck.hasPermission(sender, args, targetsArgument)) {
            sender.sendMessage("§cYou don't have permission to use this entity selector.");
            return false;
        }
        Object rawTargets = args.getRaw(targetsArgument.getNodeName());
        if (!(rawTargets instanceof String selector) || !selector.startsWith("@")) {
            return true;
        }

        SelectorPathBuilder pathBuilder = new SelectorPathBuilder("effect", "targets", new HashSet<>(targets));
        if (!PermissionManager.hasPermissionOrParent(sender, pathBuilder.build())) {
            sender.sendMessage("§cYou don't have permission to use " + pathBuilder.getScope() + " selectors for this command.");
            return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Collection<Entity> asEntities(Object value) {
        return value instanceof Collection ? (Collection<Entity>) value : java.util.List.of();
    }
}
