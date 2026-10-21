package io.github.soul4723.vanillapermissions.brigadier;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Finding vanilla commands in a Brigadier tree and editing its child maps; no server classes. */
public final class VanillaTree {

    static final String VANILLA_NAMESPACE = "minecraft:";
    /**
     * Brigadier 1.0.14 (Minecraft 1.13.x) returns {@code getNodes()} as a {@code Map<CommandNode, StringRange>} and has
     * no {@code getRootNode()}; later versions return a {@code List<ParsedCommandNode>}. Checked once, so the newer
     * call sites below are only linked on servers that have them.
     */
    private static final Method MAP_NODES = mapNodes();
    private static final boolean HAS_ROOT_NODE = hasRootNode();

    private VanillaTree() {}

    /** One vanilla command node to check, with its permission path ({@code minecraft.command.<path>}). */
    public static final class VanillaRoot {
        private final CommandNode<?> node;
        private final String path;

        public VanillaRoot(CommandNode<?> node, String path) {
            this.node = node;
            this.path = path;
        }

        public CommandNode<?> node() {
            return node;
        }

        public String path() {
            return path;
        }
    }

    /**
     * The vanilla command nodes to check in a dispatcher that also holds Bukkit and plugin commands (Paper). They are
     * found through the {@code minecraft:<name>} alias registered for each of them, which still reaches the vanilla
     * command when a plugin has taken over the plain name. Up to 1.21.3 the alias redirects to the command's node; from
     * 1.21.4 it is a copy of that node sharing its children. Either way it gets the command's path, so it cannot bypass
     * {@code minecraft.command.<name>}.
     */
    public static List<VanillaRoot> fromMinecraftAliases(CommandNode<?> dispatcherRoot) {
        List<VanillaRoot> roots = new ArrayList<>();
        // Identity, not equals: Brigadier nodes compare by structure.
        Set<CommandNode<?>> seen = Collections.newSetFromMap(new IdentityHashMap<CommandNode<?>, Boolean>());
        for (CommandNode<?> alias : dispatcherRoot.getChildren()) {
            if (!alias.getName().startsWith(VANILLA_NAMESPACE)) continue;
            String name = alias.getName().substring(VANILLA_NAMESPACE.length());
            CommandNode<?> target = alias.getRedirect();
            CommandNode<?> plain = dispatcherRoot.getChild(name);
            if (seen.add(alias)) roots.add(new VanillaRoot(alias, name));
            if (target == null) {
                if (plain != null && isCopy(alias, plain) && seen.add(plain)) roots.add(new VanillaRoot(plain, name));
                continue;
            }
            if (seen.add(target)) roots.add(new VanillaRoot(target, target.getName()));
            if (plain != null && flatten(plain) == target && seen.add(plain)) roots.add(new VanillaRoot(plain, name));
        }
        return roots;
    }

    /**
     * Every root command of a dispatcher that holds only vanilla commands (CraftBukkit's
     * {@code vanillaCommandDispatcher}), each under its own name.
     */
    public static List<VanillaRoot> allCommands(CommandNode<?> dispatcherRoot) {
        List<VanillaRoot> roots = new ArrayList<>();
        for (CommandNode<?> command : dispatcherRoot.getChildren()) roots.add(new VanillaRoot(command, command.getName()));
        return roots;
    }

    /**
     * The root nodes of vanilla command {@code name}, even if a plugin command now owns the plain name: the target
     * of a redirecting {@code minecraft:} alias, or a copied alias and the plain node it was copied from. In a
     * dispatcher without aliases this is the plain node.
     */
    public static List<CommandNode<?>> vanillaCommands(CommandDispatcher<?> dispatcher, String name) {
        CommandNode<?> alias = dispatcher.getRoot().getChild(VANILLA_NAMESPACE + name);
        CommandNode<?> plain = dispatcher.getRoot().getChild(name);
        if (alias == null) return plain == null ? Collections.<CommandNode<?>>emptyList() : Collections.<CommandNode<?>>singletonList(plain);
        if (alias.getRedirect() != null) return Collections.<CommandNode<?>>singletonList(alias.getRedirect());
        return plain != null && isCopy(alias, plain)
                ? Arrays.<CommandNode<?>>asList(plain, alias) : Collections.<CommandNode<?>>singletonList(alias);
    }

    /** Whether {@code plain} is the vanilla node Paper copied {@code alias} from, rather than a plugin command. */
    static boolean isCopy(CommandNode<?> alias, CommandNode<?> plain) {
        if (plain == alias || plain.getRedirect() != null
                || unwrap(alias.getRequirement()) != unwrap(plain.getRequirement())
                || unwrap(alias.getCommand()) != unwrap(plain.getCommand())
                || alias.getChildren().size() != plain.getChildren().size()) return false;
        for (CommandNode<?> child : alias.getChildren()) {
            if (plain.getChild(child.getName()) != child) return false;
        }
        return true;
    }

    /** The vanilla requirement behind this plugin's permission check, if any. */
    static Object unwrap(Predicate<?> requirement) {
        return requirement instanceof BrigadierPermissions.PermissionPredicate
                ? ((BrigadierPermissions.PermissionPredicate) requirement).fallback : requirement;
    }

    /** The vanilla executor behind this plugin's selector check, if any. */
    static Object unwrap(Command<?> command) {
        return command instanceof BrigadierPermissions.SelectorCommand
                ? ((BrigadierPermissions.SelectorCommand) command).fallback : command;
    }

    private static CommandNode<?> flatten(CommandNode<?> node) {
        while (node.getRedirect() != null) node = node.getRedirect();
        return node;
    }

    /**
     * Node names of the command being executed. After a redirect (a {@code minecraft:<name>} or {@code /tp}
     * alias, {@code /execute as …}) the executing context is a child that starts below the redirect target, so
     * its own nodes lack the command name; the target is its root node and is prepended here. A copied
     * {@code minecraft:<name>} alias (Paper 1.21.4+) is named without its namespace, like the command.
     */
    public static List<String> parsedPath(CommandContext<?> context) {
        List<String> path = new ArrayList<>();
        if (HAS_ROOT_NODE && !(context.getRootNode() instanceof RootCommandNode<?>)) path.add(context.getRootNode().getName());
        for (ParsedNode parsed : parsedNodes(context)) path.add(parsed.node().getName());
        if (!path.isEmpty() && path.get(0).startsWith(VANILLA_NAMESPACE)) {
            path.set(0, path.get(0).substring(VANILLA_NAMESPACE.length()));
        }
        return path;
    }

    /** One node of a parsed command and the part of the input it matched. */
    public static final class ParsedNode {
        private final CommandNode<?> node;
        private final StringRange range;

        ParsedNode(CommandNode<?> node, StringRange range) {
            this.node = node;
            this.range = range;
        }

        public CommandNode<?> node() {
            return node;
        }

        public StringRange range() {
            return range;
        }
    }

    /** The nodes {@code context} parsed, in input order, on every Brigadier version servers ship (1.0.14+). */
    public static List<ParsedNode> parsedNodes(CommandContext<?> context) {
        if (MAP_NODES == null) {
            List<ParsedNode> nodes = new ArrayList<>();
            for (ParsedCommandNode<?> parsed : context.getNodes()) nodes.add(new ParsedNode(parsed.getNode(), parsed.getRange()));
            return nodes;
        }
        Object value;
        try {
            value = MAP_NODES.invoke(context);
        } catch (InvocationTargetException error) {
            throw new IllegalStateException("Could not read the parsed command nodes", error.getCause());
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Could not read the parsed command nodes", error);
        }
        return fromMap((Map<?, ?>) value);
    }

    /** Brigadier 1.0.14's node map, which keeps input order (its CommandContextBuilder fills a LinkedHashMap). */
    static List<ParsedNode> fromMap(Map<?, ?> map) {
        List<ParsedNode> nodes = new ArrayList<>(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            nodes.add(new ParsedNode((CommandNode<?>) entry.getKey(), (StringRange) entry.getValue()));
        }
        return nodes;
    }

    private static Method mapNodes() {
        try {
            Method getNodes = CommandContext.class.getMethod("getNodes");
            return Map.class.isAssignableFrom(getNodes.getReturnType()) ? getNodes : null;
        } catch (NoSuchMethodException missing) {
            throw new IllegalStateException("Brigadier CommandContext has no getNodes()", missing);
        }
    }

    private static boolean hasRootNode() {
        try {
            CommandContext.class.getMethod("getRootNode");
            return true;
        } catch (NoSuchMethodException missing) {
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void addChild(CommandNode<?> parent, CommandNode<?> child) {
        ((CommandNode) parent).addChild(child);
    }

    /** Removes {@code child} from {@code parent}'s maps if it is still there; Brigadier has no public remove. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean removeNode(CommandNode<?> parent, CommandNode<?> child) throws ReflectiveOperationException {
        String name = child.getName();
        if (parent.getChild(name) != child) return false;
        for (String fieldName : Arrays.asList("children", "literals", "arguments")) {
            Field field = accessible(CommandNode.class.getDeclaredField(fieldName), "Brigadier node map is inaccessible: " + fieldName);
            Object value = field.get(parent);
            if (!(value instanceof Map<?, ?>)) throw new IllegalStateException("Unexpected Brigadier node map: " + fieldName);
            ((Map) value).remove(name, child);
        }
        return parent.getChild(name) != child;
    }

    /** {@code field}, made accessible; Java 8 has no {@code trySetAccessible}. */
    static Field accessible(Field field, String failure) throws IllegalAccessException {
        try {
            field.setAccessible(true);
            return field;
        } catch (RuntimeException denied) {
            IllegalAccessException error = new IllegalAccessException(failure);
            error.initCause(denied);
            throw error;
        }
    }
}
