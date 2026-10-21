package io.github.soul4723.vanillapermissions.spigot;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tells the vanilla root commands of CraftBukkit's {@code vanillaCommandDispatcher} from those other plugins add to it.
 * Vanilla nodes only hold server classes (vanilla lambdas, Brigadier's defaults) or this plugin's own wrappers. A
 * plugin command shows itself by a class from another class loader (CommandAPI's executors and predicates) or by
 * CraftBukkit's {@code BukkitCommandWrapper} as its executor, which lucko/commodore sets on the nodes it registers
 * ({@code ReflectionCommodore#setRequiredHackyFieldsRecursively}, {@code src/main/java/me/lucko/commodore/ReflectionCommodore.java}).
 */
final class PluginNodes {

    static final String BUKKIT_COMMAND_WRAPPER = "BukkitCommandWrapper";

    /**
     * Vanilla's command names from 1.13 to 26.x, so a vanilla command that another plugin changed (and that is then
     * left unchecked) can be told from a command the plugin added. Only used to pick the log level.
     */
    static final Set<String> VANILLA_COMMANDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "advancement", "attribute", "ban", "ban-ip", "banlist", "bossbar", "clear", "clone", "damage", "data", "datapack",
        "debug", "defaultgamemode", "deop", "dialog", "difficulty", "effect", "enchant", "execute", "experience", "fill",
        "fillbiome", "forceload", "function", "gamemode", "gamerule", "give", "help", "item", "jfr", "kick", "kill", "list",
        "locate", "locatebiome", "loot", "me", "msg", "op", "pardon", "pardon-ip", "particle", "perf", "place", "playsound",
        "publish", "random", "recipe", "reload", "replaceitem", "return", "ride", "rotate", "save-all", "save-off",
        "save-on", "say", "schedule", "scoreboard", "seed", "setblock", "setidletimeout", "setworldspawn", "spawnpoint",
        "spectate", "spreadplayers", "stop", "stopsound", "summon", "tag", "team", "teammsg", "teleport", "tell", "tellraw",
        "test", "tick", "time", "title", "tm", "tp", "transfer", "trigger", "version", "w", "waypoint", "weather",
        "whitelist", "worldborder", "xp")));

    private PluginNodes() {}

    /**
     * Whether {@code root} was added by another plugin: the root, its redirect target or one of its direct children has
     * a {@code BukkitCommandWrapper} executor or a class that neither {@code serverLoader} (and its parents) nor
     * {@code ownLoader} defined.
     */
    static boolean isPluginCommand(CommandNode<?> root, ClassLoader serverLoader, ClassLoader ownLoader) {
        List<CommandNode<?>> nodes = new ArrayList<>();
        nodes.add(root);
        if (root.getRedirect() != null) nodes.add(root.getRedirect());
        nodes.addAll(root.getChildren());
        for (CommandNode<?> node : nodes) {
            Object command = node.getCommand();
            if (command != null && command.getClass().getSimpleName().equals(BUKKIT_COMMAND_WRAPPER)) return true;
            List<Object> parts = new ArrayList<>();
            parts.add(command);
            parts.add(node.getRequirement());
            parts.add(node.getRedirectModifier());
            if (node instanceof ArgumentCommandNode<?, ?>) {
                parts.add(((ArgumentCommandNode<?, ?>) node).getType());
                parts.add(((ArgumentCommandNode<?, ?>) node).getCustomSuggestions());
            }
            for (Object part : parts) {
                if (part != null && isForeign(part.getClass().getClassLoader(), serverLoader, ownLoader)) return true;
            }
        }
        return false;
    }

    /** Whether {@code loader} is neither {@code ownLoader} nor {@code serverLoader} or one of its parents. */
    static boolean isForeign(ClassLoader loader, ClassLoader serverLoader, ClassLoader ownLoader) {
        if (loader == null || loader == ownLoader) return false;
        for (ClassLoader current = serverLoader; current != null; current = current.getParent()) {
            if (current == loader) return false;
        }
        return true;
    }
}
