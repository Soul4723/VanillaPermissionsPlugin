package io.github.soul4723.vanillapermissions.brigadier;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Restores upstream's literal {@code /gamemode} mode branches where vanilla has a single {@code GameModeArgument}
 * (Minecraft 1.19.4+), so each mode gets its own {@code minecraft.command.gamemode.<mode>} node. The literal nodes
 * come from {@link BrigadierBridge#gameModeLiterals}; this class finds the argument and swaps it out, and back.
 */
public final class GameModeLiterals {

    /** Upstream's mode names, which are also the node names. */
    public static final List<String> MODES =
            Collections.unmodifiableList(Arrays.asList("survival", "creative", "adventure", "spectator"));

    private final List<Installation> installed = new ArrayList<>();
    private final BrigadierBridge bridge;

    GameModeLiterals(BrigadierBridge bridge) {
        this.bridge = bridge;
    }

    void uninstall() {
        RuntimeException failure = null;
        for (int i = installed.size() - 1; i >= 0; i--) {
            Installation installation = installed.get(i);
            if (installation.root.getChild(installation.original.getName()) != null) continue;
            for (CommandNode<?> node : installation.replacements) {
                if (installation.root.getChild(node.getName()) != node) continue;
                try {
                    VanillaTree.removeNode(installation.root, node);
                } catch (ReflectiveOperationException | RuntimeException error) {
                    if (failure == null) failure = new IllegalStateException("Could not restore native gamemode command tree");
                    failure.addSuppressed(error);
                }
            }
            try {
                VanillaTree.addChild(installation.root, installation.original);
            } catch (RuntimeException error) {
                if (failure == null) failure = new IllegalStateException("Could not restore native gamemode command tree");
                failure.addSuppressed(error);
            }
        }
        installed.clear();
        if (failure != null) throw failure;
    }

    void install(CommandDispatcher<?> dispatcher) throws ReflectiveOperationException {
        List<CommandNode<?>> roots = VanillaTree.vanillaCommands(dispatcher, "gamemode");
        if (roots.isEmpty()) throw new IllegalStateException("Native gamemode command is missing");

        // A copied minecraft:gamemode alias (Paper 1.21.4+) has its own child map, so each root gets the same literals.
        List<CommandNode<Object>> replacement = null;
        for (CommandNode<?> root : roots) {
            ArgumentCommandNode<Object, ?> original = modeArgument(root);
            if (original == null) continue;
            if (replacement == null) {
                try {
                    replacement = bridge.gameModeLiterals(dispatcher, original);
                } catch (CommandSyntaxException error) {
                    throw new IllegalStateException("Could not parse the native gamemode names", error);
                }
            }
            replace(root, original, replacement);
        }
    }

    /** The GameModeArgument node to replace, or null if the root already has the literal modes (before 1.19.4). */
    @SuppressWarnings("unchecked")
    private static ArgumentCommandNode<Object, ?> modeArgument(CommandNode<?> root) {
        List<ArgumentCommandNode<Object, ?>> modeArguments = new ArrayList<>();
        for (CommandNode<?> child : root.getChildren()) {
            if (child instanceof ArgumentCommandNode<?, ?> && isGameModeArgument(((ArgumentCommandNode<?, ?>) child).getType())) {
                modeArguments.add((ArgumentCommandNode<Object, ?>) child);
            }
        }

        if (modeArguments.isEmpty()) {
            for (String mode : MODES) {
                if (root.getChild(mode) == null) {
                    throw new IllegalStateException("Unrecognized native gamemode tree; missing mode node " + mode);
                }
            }
            return null;
        }
        if (modeArguments.size() != 1) {
            throw new IllegalStateException("Unrecognized native gamemode tree; expected one GameModeArgument node");
        }
        return modeArguments.get(0);
    }

    /**
     * Mojang's {@code GameModeArgument} by name, or any argument type that parses {@code creative} to an enum
     * constant (Spigot's {@code GameType}), so the check needs no Spigot class names.
     */
    static boolean isGameModeArgument(ArgumentType<?> type) {
        if (type.getClass().getSimpleName().equals("GameModeArgument")) return true;
        try {
            return type.parse(new StringReader("creative")) instanceof Enum;
        } catch (CommandSyntaxException | RuntimeException notMode) {
            return false;
        }
    }

    private void replace(CommandNode<?> root, CommandNode<?> original, List<CommandNode<Object>> replacement)
            throws ReflectiveOperationException {
        try {
            VanillaTree.removeNode(root, original);
            for (CommandNode<?> node : replacement) {
                VanillaTree.addChild(root, node);
                if (root.getChild(node.getName()) != node) {
                    throw new IllegalStateException("Gamemode mode node already exists: " + node.getName());
                }
            }
            if (root.getChild(original.getName()) != null || root.getChild("creative") == null) {
                throw new IllegalStateException("Could not replace native gamemode argument with literal modes");
            }
            installed.add(new Installation(root, original, new ArrayList<CommandNode<?>>(replacement)));
        } catch (ReflectiveOperationException | RuntimeException error) {
            for (CommandNode<?> node : replacement) {
                try {
                    VanillaTree.removeNode(root, node);
                } catch (ReflectiveOperationException | RuntimeException rollbackError) {
                    error.addSuppressed(rollbackError);
                }
            }
            try {
                if (root.getChild(original.getName()) == null) VanillaTree.addChild(root, original);
            } catch (RuntimeException rollbackError) {
                error.addSuppressed(rollbackError);
            }
            throw error;
        }
    }

    private static final class Installation {
        final CommandNode<?> root;
        final CommandNode<?> original;
        final List<CommandNode<?>> replacements;

        Installation(CommandNode<?> root, CommandNode<?> original, List<CommandNode<?>> replacements) {
            this.root = root;
            this.original = original;
            this.replacements = replacements;
        }
    }

    /**
     * Literal modes built without any server names: each mode's value comes from {@code modeArgument}'s own
     * {@link ArgumentType#parse}, and each literal (and its copied player argument) runs the original node's
     * executor with that value put in the context under the argument's name.
     */
    @SuppressWarnings("unchecked")
    public static List<CommandNode<Object>> nameFree(ArgumentCommandNode<Object, ?> modeArgument)
            throws ReflectiveOperationException, CommandSyntaxException {
        Field arguments = VanillaTree.accessible(CommandContext.class.getDeclaredField("arguments"),
                "Brigadier command context arguments are inaccessible");
        String argumentName = modeArgument.getName();
        Command<Object> selfCommand = unwrapCommand(modeArgument.getCommand());
        List<CommandNode<Object>> literals = new ArrayList<>(MODES.size());
        for (String mode : MODES) {
            Object value = modeArgument.getType().parse(new StringReader(mode));
            LiteralArgumentBuilder<Object> literal = LiteralArgumentBuilder.literal(mode);
            if (selfCommand != null) {
                literal.executes(context -> selfCommand.run(withArgument(arguments, context, argumentName, value)));
            }
            for (CommandNode<Object> child : modeArgument.getChildren()) {
                if (!(child instanceof ArgumentCommandNode<?, ?>)) {
                    throw new IllegalStateException("Unrecognized native gamemode tree; unexpected node " + child.getName());
                }
                ArgumentCommandNode<Object, Object> target = (ArgumentCommandNode<Object, Object>) child;
                Command<Object> targetCommand = unwrapCommand(target.getCommand());
                RequiredArgumentBuilder<Object, Object> copy = RequiredArgumentBuilder.argument(target.getName(), target.getType());
                copy.requires((Predicate<Object>) unwrapRequirement(target.getRequirement()));
                if (target.getCustomSuggestions() != null) copy.suggests(target.getCustomSuggestions());
                if (targetCommand != null) {
                    copy.executes(context -> targetCommand.run(withArgument(arguments, context, argumentName, value)));
                }
                literal.then(copy);
            }
            literal.requires((Predicate<Object>) unwrapRequirement(modeArgument.getRequirement()));
            literals.add(literal.build());
        }
        return literals;
    }

    @SuppressWarnings("unchecked")
    private static Command<Object> unwrapCommand(Command<Object> command) {
        return (Command<Object>) VanillaTree.unwrap(command);
    }

    private static Object unwrapRequirement(Predicate<?> requirement) {
        return VanillaTree.unwrap(requirement);
    }

    @SuppressWarnings("unchecked")
    private static CommandContext<Object> withArgument(Field arguments, CommandContext<Object> context, String name, Object value) {
        Map<String, ParsedArgument<Object, ?>> copy;
        try {
            copy = new LinkedHashMap<>((Map<String, ParsedArgument<Object, ?>>) arguments.get(context));
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(error);
        }
        copy.put(name, new ParsedArgument<>(0, 0, value));
        return new CommandContext<>(context.getSource(), context.getInput(), copy, context.getCommand(), context.getRootNode(),
                context.getNodes(), context.getRange(), context.getChild(), context.getRedirectModifier(), context.isForked());
    }
}
