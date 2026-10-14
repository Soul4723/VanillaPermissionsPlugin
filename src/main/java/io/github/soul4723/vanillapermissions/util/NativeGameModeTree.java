package io.github.soul4723.vanillapermissions.util;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Restores upstream's literal mode branches while retaining the native setMode implementation. */
final class NativeGameModeTree {

    private static final Set<String> MODES = Set.of("survival", "creative", "adventure", "spectator");
    private static final List<Installation> INSTALLED = new ArrayList<>();

    private NativeGameModeTree() {}

    static void uninstall() {
        for (int i = INSTALLED.size() - 1; i >= 0; i--) {
            Installation installation = INSTALLED.get(i);
            boolean unchanged = installation.replacements().stream()
                .allMatch(node -> installation.root().getChild(node.getName()) == node);
            if (!unchanged || installation.root().getChild(installation.original().getName()) != null) continue;
            for (CommandNode<?> node : installation.replacements()) {
                try {
                    removeNode(installation.root(), node);
                } catch (ReflectiveOperationException error) {
                    throw new IllegalStateException("Could not restore native gamemode command tree", error);
                }
            }
            addChild(installation.root(), installation.original());
        }
        INSTALLED.clear();
    }

    static void install(CommandDispatcher<?> dispatcher) throws ReflectiveOperationException {
        CommandNode<?> root = dispatcher.getRoot().getChild("gamemode");
        if (root == null) throw new IllegalStateException("Native gamemode command is missing");

        List<CommandNode<?>> modeArguments = new ArrayList<>();
        for (CommandNode<?> child : root.getChildren()) {
            if (child instanceof ArgumentCommandNode<?, ?> argument
                && argument.getType().getClass().getSimpleName().equals("GameModeArgument")) {
                modeArguments.add(child);
            }
        }

        if (modeArguments.isEmpty()) {
            for (String mode : MODES) {
                if (root.getChild(mode) == null) {
                    throw new IllegalStateException("Unrecognized native gamemode tree; missing mode node " + mode);
                }
            }
            return;
        }
        if (modeArguments.size() != 1) {
            throw new IllegalStateException("Unrecognized native gamemode tree; expected one GameModeArgument node");
        }

        ClassLoader loader = dispatcher.getClass().getClassLoader();
        Class<?> gameTypeClass = Class.forName("net.minecraft.world.level.GameType", false, loader);
        Object[] gameTypes = (Object[]) gameTypeClass.getMethod("values").invoke(null);
        Method getName = gameTypeClass.getMethod("getName");
        Method setMode = Class.forName("net.minecraft.server.commands.GameModeCommand", false, loader)
            .getDeclaredMethod("setMode", CommandContext.class, Collection.class, gameTypeClass);
        if (!setMode.trySetAccessible()) throw new IllegalAccessException("GameModeCommand.setMode is inaccessible");

        Class<?> entityArgumentClass = Class.forName("net.minecraft.commands.arguments.EntityArgument", false, loader);
        ArgumentType<?> players = (ArgumentType<?>) entityArgumentClass.getMethod("players").invoke(null);
        Method getPlayers = entityArgumentClass.getMethod("getPlayers", CommandContext.class, String.class);

        List<CommandNode<?>> replacement = new ArrayList<>(gameTypes.length);
        for (Object gameType : gameTypes) {
            String mode = (String) getName.invoke(gameType);
            if (!MODES.contains(mode)) throw new IllegalStateException("Unexpected native gamemode: " + mode);
            LiteralArgumentBuilder<Object> modeBuilder = LiteralArgumentBuilder.literal(mode);
            modeBuilder.executes(context -> invokeSetMode(setMode, context, List.of(invokePlayer(context)), gameType));

            @SuppressWarnings("unchecked")
            ArgumentType<Object> playerArgument = (ArgumentType<Object>) players;
            RequiredArgumentBuilder<Object, Object> target = RequiredArgumentBuilder.argument("target", playerArgument);
            target.executes(context -> {
                @SuppressWarnings("unchecked")
                Collection<Object> selected = (Collection<Object>) invoke(getPlayers, null, context, "target");
                return invokeSetMode(setMode, context, selected, gameType);
            });
            modeBuilder.then(target);
            replacement.add(modeBuilder.build());
        }

        CommandNode<?> original = modeArguments.get(0);
        try {
            removeNode(root, original);
            for (CommandNode<?> node : replacement) addChild(root, node);
            if (root.getChild(original.getName()) != null || root.getChild("creative") == null) {
                throw new IllegalStateException("Could not replace native gamemode argument with literal modes");
            }
            INSTALLED.add(new Installation(root, original, List.copyOf(replacement)));
        } catch (ReflectiveOperationException | RuntimeException error) {
            for (CommandNode<?> node : replacement) removeNode(root, node);
            addChild(root, original);
            throw error;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addChild(CommandNode<?> parent, CommandNode<?> child) {
        ((CommandNode) parent).addChild(child);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static boolean removeNode(CommandNode<?> parent, CommandNode<?> child) throws ReflectiveOperationException {
        String name = child.getName();
        if (parent.getChild(name) != child) return false;
        for (String fieldName : List.of("children", "literals", "arguments")) {
            var field = CommandNode.class.getDeclaredField(fieldName);
            if (!field.trySetAccessible()) throw new IllegalAccessException("Brigadier node map is inaccessible: " + fieldName);
            Object value = field.get(parent);
            if (!(value instanceof Map<?, ?> map)) throw new IllegalStateException("Unexpected Brigadier node map: " + fieldName);
            ((Map) map).remove(name, child);
        }
        return parent.getChild(name) != child;
    }

    private record Installation(CommandNode<?> root, CommandNode<?> original, List<CommandNode<?>> replacements) {}

    private static Object invokePlayer(CommandContext<Object> context) throws CommandSyntaxException {
        try {
            return context.getSource().getClass().getMethod("getPlayerOrException").invoke(context.getSource());
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CommandSyntaxException syntax) throw syntax;
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not resolve native command sender", e);
        }
    }

    private static int invokeSetMode(Method setMode, CommandContext<Object> context, Collection<?> targets, Object gameType)
        throws CommandSyntaxException {
        try {
            return (int) setMode.invoke(null, context, targets, gameType);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CommandSyntaxException syntax) throw syntax;
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not run native gamemode command", e);
        }
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
