package io.github.soul4723.extrapermissions.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.TextArgument;
import io.github.soul4723.extrapermissions.ExtraPermissions;
import io.github.soul4723.extrapermissions.util.PermissionManager;
import io.github.soul4723.extrapermissions.util.SelectorPathBuilder;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

public class EffectCommandHandler {

    private static final String BASE_PERM = "minecraft.command.effect";
    private static final String COMMAND_NAME = "effect";

    public static void registerCommands(JavaPlugin plugin) {
        boolean selectorPermissions = ((ExtraPermissions) plugin).isFeatureEnabled("selector_permissions");
        try {
            CommandAPI.unregister("effect");
        } catch (Exception e) {
            plugin.getLogger().fine("Effect command not previously registered");
        }

        CommandAPICommand effect = new CommandAPICommand("effect")
                .withSubcommand(new CommandAPICommand("give")
                        .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, BASE_PERM + ".give"))
                        .withArguments(
                                new EntitySelectorArgument.ManyEntities("targets"),
                                new TextArgument("effect"),
                                new IntegerArgument("seconds", 1, 1_000_000)
                        )
                        .executesNative((proxy, args) -> {
                            CommandSender sender = proxy.getCaller();
                            Collection<Entity> targetEntities = asEntities(args.get("targets"));
                            String effectName = (String) args.get("effect");
                            Integer secondsObj = (Integer) args.get("seconds");

                            if (targetEntities.isEmpty()) {
                                sender.sendMessage("§cTarget entity not found");
                                return;
                            }
                            if (secondsObj == null) {
                                sender.sendMessage("§cInvalid seconds specified");
                                return;
                            }
                            if (effectName == null) {
                                sender.sendMessage("§cEffect name is required");
                                return;
                            }

                            int seconds = secondsObj;
                            NamespacedKey effectKey = NamespacedKey.fromString(effectName.toLowerCase(Locale.ROOT));
                            PotionEffectType effectType = effectKey == null ? null : Registry.EFFECT.get(effectKey);
                            if (effectType == null) {
                                sender.sendMessage("§cUnknown effect: " + effectName);
                                return;
                            }

                            SelectorPathBuilder pathBuilder = new SelectorPathBuilder(COMMAND_NAME, "targets", new HashSet<>(targetEntities));
                            String selectorPath = pathBuilder.build();

                            if (selectorPermissions && !PermissionManager.hasPermissionOrParent(sender, selectorPath)) {
                                sender.sendMessage("§cYou don't have permission to use " + pathBuilder.getScope() + " selectors for this command.");
                                return;
                            }

                            int applied = 0;
                            for (Entity target : targetEntities) {
                                if (target instanceof LivingEntity
                                        && ((LivingEntity) target).addPotionEffect(new PotionEffect(effectType, seconds * 20, 0, true, true))) {
                                    applied++;
                                }
                            }
                            if (applied == 0) {
                                sender.sendMessage("§cNo target effects changed");
                            } else {
                                sender.sendMessage("§aApplied " + effectName + " to " + applied + " entit" + (applied == 1 ? "y" : "ies"));
                            }
                        })
                )
                .withSubcommand(new CommandAPICommand("clear")
                        .withRequirement(sender -> PermissionManager.hasPermissionOrParent(sender, BASE_PERM + ".clear"))
                        .withArguments(
                                new EntitySelectorArgument.ManyEntities("targets"),
                                new TextArgument("effect").setOptional(true)
                        )
                        .executesNative((proxy, args) -> {
                            CommandSender sender = proxy.getCaller();
                            Collection<Entity> targetEntities = asEntities(args.get("targets"));
                            Object effectArg = args.get("effect");
                            String effectName = effectArg != null ? (String) effectArg : null;

                            if (targetEntities.isEmpty()) {
                                sender.sendMessage("§cTarget entity not found");
                                return;
                            }

                            SelectorPathBuilder pathBuilder = new SelectorPathBuilder(COMMAND_NAME, "targets", new HashSet<>(targetEntities));
                            String selectorPath = pathBuilder.build();

                            if (selectorPermissions && !PermissionManager.hasPermissionOrParent(sender, selectorPath)) {
                                sender.sendMessage("§cYou don't have permission to use " + pathBuilder.getScope() + " selectors for this command.");
                                return;
                            }

                            PotionEffectType effectType = null;
                            if (effectName != null) {
                                NamespacedKey effectKey = NamespacedKey.fromString(effectName.toLowerCase(Locale.ROOT));
                                effectType = effectKey == null ? null : Registry.EFFECT.get(effectKey);
                                if (effectType == null) {
                                    sender.sendMessage("§cUnknown effect: " + effectName);
                                    return;
                                }
                            }

                            int affected = 0;
                            for (Entity target : targetEntities) {
                                if (target instanceof LivingEntity) {
                                    LivingEntity living = (LivingEntity) target;
                                    if (effectType == null) {
                                        living.clearActivePotionEffects();
                                        affected++;
                                    } else if (living.removePotionEffect(effectType)) {
                                        affected++;
                                    }
                                }
                            }
                            if (affected == 0) {
                                sender.sendMessage("§cNo target effects changed");
                            } else if (effectType == null) {
                                sender.sendMessage("§aCleared all effects from " + affected + " entit" + (affected == 1 ? "y" : "ies"));
                            } else {
                                sender.sendMessage("§aCleared " + effectName + " from " + affected + " entit" + (affected == 1 ? "y" : "ies"));
                            }
                        })
                );

        effect.register();
    }

    @SuppressWarnings("unchecked")
    private static Collection<Entity> asEntities(Object value) {
        return value instanceof Collection ? (Collection<Entity>) value : java.util.List.of();
    }
}
