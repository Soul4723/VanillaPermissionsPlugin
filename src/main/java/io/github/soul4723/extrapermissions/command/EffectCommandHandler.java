package io.github.soul4723.extrapermissions.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.TextArgument;
import io.github.soul4723.extrapermissions.util.PermissionManager;
import io.github.soul4723.extrapermissions.util.SelectorPathBuilder;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;

public class EffectCommandHandler {

    private static final String BASE_PERM = "minecraft.command.effect";
    private static final String COMMAND_NAME = "effect";

    public static void registerCommands(JavaPlugin plugin) {
        try {
            CommandAPI.unregister("effect");
        } catch (Exception e) {
            plugin.getLogger().fine("Effect command not previously registered");
        }

        CommandAPICommand effect = new CommandAPICommand("effect")
                .withSubcommand(new CommandAPICommand("give")
                        .withPermission(BASE_PERM + ".give")
                        .withArguments(
                                new EntitySelectorArgument.OneEntity("targets"),
                                new TextArgument("effect"),
                                new IntegerArgument("seconds", 0, Integer.MAX_VALUE)
                        )
                        .executesPlayer((player, args) -> {
                            Entity targetEntity = (Entity) args.get("targets");
                            String effectName = (String) args.get("effect");
                            Integer secondsObj = (Integer) args.get("seconds");
                            
                            if (targetEntity == null) {
                                player.sendMessage("§cTarget entity not found");
                                return;
                            }
                            if (secondsObj == null) {
                                player.sendMessage("§cInvalid seconds specified");
                                return;
                            }
                            if (effectName == null) {
                                player.sendMessage("§cEffect name is required");
                                return;
                            }
                            
                            int seconds = secondsObj;
                            NamespacedKey effectKey = NamespacedKey.minecraft(effectName.toLowerCase());
                            PotionEffectType effectType = Registry.EFFECT.get(effectKey);
                            if (effectType == null) {
                                player.sendMessage("§cUnknown effect: " + effectName);
                                return;
                            }

                            HashSet<Entity> targetSet = new HashSet<>();
                            targetSet.add(targetEntity);
                            SelectorPathBuilder pathBuilder = new SelectorPathBuilder(COMMAND_NAME, "targets", targetSet);
                            String selectorPath = pathBuilder.build();

                            if (!PermissionManager.hasPermission(player, selectorPath)) {
                                player.sendMessage("§cYou don't have permission to use " + pathBuilder.getScope() + " selectors for this command.");
                                return;
                            }

                            if (targetEntity instanceof LivingEntity) {
                                LivingEntity living = (LivingEntity) targetEntity;
                                living.addPotionEffect(new PotionEffect(effectType, seconds * 20, 0, true, true));
                                player.sendMessage("§aApplied " + effectName + " to the target");
                            } else {
                                player.sendMessage("§cTarget is not a living entity");
                            }
                        })
                )
                .withSubcommand(new CommandAPICommand("clear")
                        .withPermission(BASE_PERM + ".clear")
                        .withArguments(
                                new EntitySelectorArgument.OneEntity("targets"),
                                new TextArgument("effect").setOptional(true)
                        )
                        .executesPlayer((player, args) -> {
                            Entity targetEntity = (Entity) args.get("targets");
                            Object effectArg = args.get(1);
                            String effectName = effectArg != null ? (String) effectArg : null;

                            if (targetEntity == null) {
                                player.sendMessage("§cTarget entity not found");
                                return;
                            }

                            HashSet<Entity> targetSet = new HashSet<>();
                            targetSet.add(targetEntity);
                            SelectorPathBuilder pathBuilder = new SelectorPathBuilder(COMMAND_NAME, "targets", targetSet);
                            String selectorPath = pathBuilder.build();

                            if (!PermissionManager.hasPermission(player, selectorPath)) {
                                player.sendMessage("§cYou don't have permission to use " + pathBuilder.getScope() + " selectors for this command.");
                                return;
                            }

                            if (targetEntity instanceof LivingEntity) {
                                LivingEntity living = (LivingEntity) targetEntity;
                                if (effectName == null) {
                                    living.clearActivePotionEffects();
                                    player.sendMessage("§aCleared all effects from the target");
                                } else {
                                    NamespacedKey effectKey = NamespacedKey.minecraft(effectName.toLowerCase());
                                    PotionEffectType effectType = Registry.EFFECT.get(effectKey);
                                    if (effectType != null) {
                                        living.removePotionEffect(effectType);
                                        player.sendMessage("§aCleared " + effectName + " from the target");
                                    } else {
                                        player.sendMessage("§cUnknown effect: " + effectName);
                                    }
                                }
                            } else {
                                player.sendMessage("§cTarget is not a living entity");
                            }
                        })
                );

        effect.register();
    }
}
