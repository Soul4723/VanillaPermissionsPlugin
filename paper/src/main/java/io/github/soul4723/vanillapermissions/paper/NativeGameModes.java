package io.github.soul4723.vanillapermissions.paper;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import io.github.soul4723.vanillapermissions.brigadier.GameModeLiterals;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Paper's literal {@code /gamemode} modes, built on the native {@code GameModeCommand.setMode} by Mojang name. */
final class NativeGameModes {

    private NativeGameModes() {}

    static List<CommandNode<Object>> literalModes(CommandDispatcher<?> dispatcher) throws ReflectiveOperationException {
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

        List<CommandNode<Object>> replacement = new ArrayList<>(gameTypes.length);
        for (Object gameType : gameTypes) {
            String mode = (String) getName.invoke(gameType);
            if (!GameModeLiterals.MODES.contains(mode)) throw new IllegalStateException("Unexpected native gamemode: " + mode);
            LiteralArgumentBuilder<Object> modeBuilder = LiteralArgumentBuilder.literal(mode);
            modeBuilder.executes(context -> invokeSetMode(setMode, context, List.of(invokePlayer(context)), gameType));

            @SuppressWarnings("unchecked")
            ArgumentType<Object> playerArgument = (ArgumentType<Object>) players;
            RequiredArgumentBuilder<Object, Object> target = RequiredArgumentBuilder.argument("target", playerArgument);
            target.executes(context -> invokeSetMode(setMode, context, invokePlayers(getPlayers, context, "target"), gameType));
            modeBuilder.then(target);
            replacement.add(modeBuilder.build());
        }
        return replacement;
    }

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

    /** Resolves gamemode targets, keeping vanilla's syntax errors (e.g. "No player was found") intact. */
    @SuppressWarnings("unchecked")
    static Collection<Object> invokePlayers(Method getPlayers, CommandContext<?> context, String name)
            throws CommandSyntaxException {
        try {
            return (Collection<Object>) getPlayers.invoke(null, context, name);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CommandSyntaxException syntax) throw syntax;
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not resolve native gamemode targets", e);
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
}
