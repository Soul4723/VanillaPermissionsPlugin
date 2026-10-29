package io.github.soul4723.vanillapermissions.paper;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import io.github.soul4723.vanillapermissions.brigadier.ArgumentResolver;
import io.github.soul4723.vanillapermissions.brigadier.BrigadierBridge;
import io.github.soul4723.vanillapermissions.brigadier.SelectorOverride;
import io.github.soul4723.vanillapermissions.brigadier.VanillaTree;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Paper 1.20.6+: the server runs with Mojang names, so classes and members are looked up by those
 * ({@code CommandSourceStack}, {@code EntityArgument}, ...), and selectors are let through with Paper's
 * {@code bypassSelectorPermissions} field.
 */
final class PaperBridge implements BrigadierBridge {

    private Method getSender;
    private Method withSuppressedOutput;
    private Method getServer;
    private Method withSource;
    private Field selectorBypass;
    private NativePermissionLevels levels;
    private Method entityArgument;
    private Method entitiesArgument;
    private Method getEntity;
    private Method getEntities;
    private Field silent;
    private Class<?> commandSource;

    @Override
    public void prepare(CommandDispatcher<?> dispatcher) throws ReflectiveOperationException {
        ClassLoader loader = Bukkit.getServer().getClass().getClassLoader();
        Class<?> sourceClass = Class.forName("net.minecraft.commands.CommandSourceStack", false, loader);
        levels = NativePermissionLevels.detect(sourceClass);

        Class<?> entityArgumentClass = Class.forName("net.minecraft.commands.arguments.EntityArgument", false, loader);
        entityArgument = entityArgumentClass.getMethod("entity");
        entitiesArgument = entityArgumentClass.getMethod("entities");
        getEntity = entityArgumentClass.getMethod("getEntity", CommandContext.class, String.class);
        getEntities = entityArgumentClass.getMethod("getOptionalEntities", CommandContext.class, String.class);

        Class<?> commandSourceClass = Class.forName("net.minecraft.commands.CommandSource", false, loader);
        commandSource = commandSourceClass;
        commandSourceClass.getMethod("getBukkitSender", sourceClass);
        Class.forName("net.minecraft.world.entity.Entity", false, loader).getMethod("getBukkitEntity");
        silent = sourceClass.getDeclaredField("silent");
        if (!silent.trySetAccessible()) throw new IllegalAccessException("CommandSourceStack.silent is inaccessible");
        withSuppressedOutput = sourceClass.getMethod("withSuppressedOutput");
        getServer = sourceClass.getMethod("getServer");
        withSource = sourceClass.getMethod("withSource", commandSourceClass);

        getSender = sourceClass.getMethod("getBukkitSender");
        selectorBypass = sourceClass.getField("bypassSelectorPermissions");
        if (!CommandSender.class.isAssignableFrom(getSender.getReturnType())) {
            throw new IllegalStateException("Native command source has unexpected Bukkit sender type");
        }
    }

    @Override
    public List<VanillaTree.VanillaRoot> vanillaRoots(CommandDispatcher<?> dispatcher) {
        List<VanillaTree.VanillaRoot> roots = VanillaTree.fromMinecraftAliases(dispatcher.getRoot());
        if (roots.isEmpty()) throw new IllegalStateException("Paper did not register any minecraft: command aliases");
        return roots;
    }

    @Override
    public CommandSender sender(Object source) throws ReflectiveOperationException {
        return (CommandSender) getSender.invoke(source);
    }

    @Override
    public boolean allowsSelectors(Object source) throws ReflectiveOperationException {
        return levels.allowsSelectors(source);
    }

    @Override
    public int operatorLevel(Object sourceOrEntity) throws ReflectiveOperationException {
        return levels.level(sourceOrEntity);
    }

    @Override
    public Object withOperatorLevelAtMost(Object source, int level) throws ReflectiveOperationException {
        return levels.withLevelAtMost(source, level);
    }

    @Override
    public ArgumentResolver selectorResolver(ArgumentType<?> type) {
        String name = type.getClass().getName();
        String methodName = switch (name) {
            case "net.minecraft.commands.arguments.EntityArgument" -> "getOptionalEntities";
            case "net.minecraft.commands.arguments.GameProfileArgument" -> "getGameProfiles";
            case "net.minecraft.commands.arguments.ScoreHolderArgument" -> "getNamesWithDefaultWildcard";
            default -> null;
        };
        if (methodName == null) return null;
        for (Method method : type.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 2
                    && Modifier.isStatic(method.getModifiers())) return ArgumentResolver.of(method);
        }
        throw new IllegalStateException("Native selector resolver is missing: " + name + "." + methodName);
    }

    @Override
    public boolean selectorCheckBypassed(Object source) throws ReflectiveOperationException {
        return selectorBypass.getBoolean(source);
    }

    /** Paper 1.20.6+ skips its selector check while {@code bypassSelectorPermissions} is set (verified live on 1.20.6). */
    @Override
    public SelectorOverride bypassSelectorCheck(CommandContext<?> context, String argumentName) throws ReflectiveOperationException {
        Object source = context.getSource();
        boolean previous = selectorBypass.getBoolean(source);
        selectorBypass.setBoolean(source, true);
        return () -> selectorBypass.setBoolean(source, previous);
    }

    /** Kept on {@code GameModeCommand.setMode} by Mojang name, exactly as in 1.0.0. */
    @Override
    public List<CommandNode<Object>> gameModeLiterals(CommandDispatcher<?> dispatcher, ArgumentCommandNode<Object, ?> modeArgument)
            throws ReflectiveOperationException {
        return NativeGameModes.literalModes(dispatcher);
    }

    @Override
    public ArgumentType<?> entityArgument() throws ReflectiveOperationException {
        return (ArgumentType<?>) entityArgument.invoke(null);
    }

    @Override
    public ArgumentType<?> entitiesArgument() throws ReflectiveOperationException {
        return (ArgumentType<?>) entitiesArgument.invoke(null);
    }

    @Override
    public ArgumentResolver entityResolver() {
        return ArgumentResolver.of(getEntity);
    }

    @Override
    public ArgumentResolver optionalEntitiesResolver() {
        return ArgumentResolver.of(getEntities);
    }

    @Override
    public Object withSuppressedOutput(Object source) {
        try {
            return withSuppressedOutput.invoke(source);
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw new IllegalStateException("Could not invoke native command source method withSuppressedOutput", error);
        }
    }

    @Override
    public boolean redirectsFeedback() {
        return true;
    }

    @Override
    public Object feedbackToServer(Object source) {
        setSilent(source, false);
        Object server;
        try {
            server = getServer.invoke(source);
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw new IllegalStateException("Could not invoke native command source method getServer", error);
        }
        return withOutput(commandSource, getSender, withSource, source, server);
    }

    @Override
    public List<Object> feedbackToEntities(Object source, Collection<?> entities) {
        setSilent(source, false);
        List<Object> sources = new ArrayList<>();
        for (Object target : entities) sources.add(withOutput(commandSource, getSender, withSource, source, target));
        return sources;
    }

    /**
     * Sends command output to {@code output} while LuckPerms and selector checks keep using the original sender.
     * A plain withSource would also make Paper report {@code output} as the Bukkit sender, letting a player run
     * commands and selectors under the console's or another player's permissions.
     */
    static Object withOutput(Class<?> commandSource, Method getSender, Method withSource, Object source, Object output) {
        Object sender;
        try {
            sender = getSender.invoke(source);
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw new IllegalStateException("Could not invoke native command source method getBukkitSender", error);
        }
        Object redirected = Proxy.newProxyInstance(commandSource.getClassLoader(), new Class<?>[]{commandSource},
                (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxy == arguments[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            default -> "FeedbackSource[" + output + "]";
                        };
                    }
                    if (method.getName().equals("getBukkitSender")) return sender;
                    try {
                        return method.invoke(output, arguments);
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    }
                });
        try {
            return withSource.invoke(source, redirected);
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw new IllegalStateException("Could not invoke native command source method withSource", error);
        }
    }

    private void setSilent(Object source, boolean value) {
        try {
            silent.setBoolean(source, value);
        } catch (IllegalAccessException | RuntimeException error) {
            throw new IllegalStateException("Could not change native command feedback state", error);
        }
    }
}
