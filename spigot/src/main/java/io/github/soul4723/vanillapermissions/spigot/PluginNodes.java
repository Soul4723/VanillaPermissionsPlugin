package io.github.soul4723.vanillapermissions.spigot;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Tells the vanilla root commands of CraftBukkit's {@code vanillaCommandDispatcher} from those other plugins add to it.
 * Vanilla nodes only hold server classes (vanilla lambdas, Brigadier's defaults) or this plugin's own wrappers. A
 * plugin command shows itself by a class from another class loader (CommandAPI's executors and predicates) or by
 * CraftBukkit's {@code BukkitCommandWrapper} as its executor, which lucko/commodore sets on the nodes it registers
 * ({@code ReflectionCommodore#setRequiredHackyFieldsRecursively}, {@code src/main/java/me/lucko/commodore/ReflectionCommodore.java}).
 */
final class PluginNodes {

    static final String BUKKIT_COMMAND_WRAPPER = "BukkitCommandWrapper";

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
