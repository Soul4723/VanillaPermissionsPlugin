package io.github.soul4723.vanillapermissions.spigot;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import io.github.soul4723.vanillapermissions.brigadier.ArgumentResolver;
import io.github.soul4723.vanillapermissions.brigadier.BrigadierBridge;
import io.github.soul4723.vanillapermissions.brigadier.Reflection;
import io.github.soul4723.vanillapermissions.brigadier.SelectorOverride;
import io.github.soul4723.vanillapermissions.brigadier.SelectorTargets;
import io.github.soul4723.vanillapermissions.brigadier.VanillaTree;
import io.github.soul4723.vanillapermissions.selector.SelectorRules;
import io.github.soul4723.vanillapermissions.spigot.SpigotNames.NativeClass;
import io.github.soul4723.vanillapermissions.spigot.SpigotNames.NativeMember;
import org.bukkit.command.CommandSender;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spigot 1.13+ and Paper 1.13 - 1.20.4 (and Paper 1.20.5, which runs Mojang names): the server-specific half of the
 * Brigadier adapter. Obfuscated members come from {@link SpigotNames}, resolved and checked by {@link NativeAccess};
 * the Bukkit sender comes from CraftBukkit's {@code getBukkitSender()}, which every version has under that name.
 * Operator levels, int or PermissionSet, go through {@link OperatorLevels}.
 *
 * <p>Vanilla's own selector check is skipped by clearing {@code EntitySelector}'s "uses selector" flag on the
 * selectors of the running command and restoring it afterwards, as CommandAPI does on 1.14 - 1.20.4
 * ({@code SafeVarHandle} on {@code checkPermissions} / {@code p}). Paper's {@code bypassSelectorPermissions} is never
 * touched: on Paper 1.19.4 - 1.20.4 setting it forces the refusal.
 */
final class SpigotBridge implements BrigadierBridge {

    private final ClassLoader loader;
    private final Object minecraftServer;
    private volatile NativeAccess access;
    private volatile Method getBukkitSender;
    private volatile OperatorLevels operatorLevels;
    /** For each result type that wraps an {@code EntitySelector} (profile and score holder results), that field. */
    private final Map<Class<?>, Field> wrappedSelectors = new HashMap<>();
    private volatile List<String> lastSkipped = Collections.emptyList();

    SpigotBridge(ClassLoader loader, Object minecraftServer) {
        this.loader = loader;
        this.minecraftServer = minecraftServer;
    }

    /** Resolves every name before anything is patched; {@link SpigotAdapter} calls it once, before the install. */
    void resolve(MappingGeneration generation) throws ReflectiveOperationException {
        NativeAccess resolved = NativeAccess.resolve(generation, loader);
        Class<?> source = resolved.type(NativeClass.SOURCE);
        Method sender = source.getMethod("getBukkitSender");
        if (!CommandSender.class.isAssignableFrom(sender.getReturnType())) {
            throw new IllegalStateException(source.getName() + ".getBukkitSender() does not return a Bukkit CommandSender");
        }
        OperatorLevels levels = OperatorLevels.of(resolved);
        resolved.type(NativeClass.ENTITY).getMethod("getBukkitEntity");
        if (resolved.has(NativeMember.SOURCE_WITH_SOURCE)
                && !resolved.type(NativeClass.COMMAND_SOURCE).isInstance(minecraftServer)) {
            throw new IllegalStateException("The MinecraftServer is not a " + resolved.type(NativeClass.COMMAND_SOURCE).getName());
        }
        getBukkitSender = sender;
        operatorLevels = levels;
        access = resolved;
    }

    @Override
    public void prepare(CommandDispatcher<?> dispatcher) {
        if (access == null) throw new IllegalStateException("Native names were not resolved before the install");
    }

    /**
     * Every root command of {@code vanillaCommandDispatcher} except those other plugins put there. It has no
     * {@code minecraft:} aliases (CraftBukkit adds those later), but on 1.13 - 1.16 it is also the dispatcher
     * lucko/commodore registers into (LuckPerms' {@code /lp} tree, whose executors are CraftBukkit's
     * {@code BukkitCommandWrapper}), and CommandAPI registers its Spigot commands there before CraftBukkit syncs them
     * ({@code CommandAPIBukkit}).
     */
    @Override
    public List<VanillaTree.VanillaRoot> vanillaRoots(CommandDispatcher<?> dispatcher) {
        List<VanillaTree.VanillaRoot> roots = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (VanillaTree.VanillaRoot root : VanillaTree.allCommands(dispatcher.getRoot())) {
            if (PluginNodes.isPluginCommand(root.node(), loader, SpigotBridge.class.getClassLoader())) {
                skipped.add(root.path());
            } else {
                roots.add(root);
            }
        }
        lastSkipped = Collections.unmodifiableList(skipped);
        return roots;
    }

    /** Root commands other plugins added to the vanilla dispatcher, which the last install left alone. */
    List<String> skippedPluginCommands() {
        return lastSkipped;
    }

    @Override
    public CommandSender sender(Object source) throws ReflectiveOperationException {
        return (CommandSender) getBukkitSender.invoke(source);
    }

    @Override
    public boolean allowsSelectors(Object source) throws ReflectiveOperationException {
        return operatorLevels.allowsSelectors(source);
    }

    @Override
    public int operatorLevel(Object sourceOrEntity) throws ReflectiveOperationException {
        return operatorLevels.level(sourceOrEntity);
    }

    @Override
    public Object withOperatorLevelAtMost(Object source, int level) throws ReflectiveOperationException {
        return operatorLevels.withLevelAtMost(source, level);
    }

    @Override
    public ArgumentResolver selectorResolver(ArgumentType<?> type) {
        Class<?> argument = type.getClass();
        if (argument == access.type(NativeClass.ENTITY_ARGUMENT)) {
            return ArgumentResolver.of(access.method(NativeMember.ENTITY_ARGUMENT_GET_OPTIONAL_ENTITIES));
        }
        if (argument == access.type(NativeClass.GAME_PROFILE_ARGUMENT)) {
            return ArgumentResolver.of(access.method(NativeMember.GAME_PROFILE_ARGUMENT_GET));
        }
        if (argument == access.type(NativeClass.SCORE_HOLDER_ARGUMENT)) {
            return ArgumentResolver.of(access.method(NativeMember.SCORE_HOLDER_ARGUMENT_GET_WITH_WILDCARD));
        }
        return null;
    }

    @Override
    public boolean selectorCheckBypassed(Object source) {
        return false;
    }

    /**
     * Clears the "uses selector" flag of every {@code EntitySelector} parsed for {@code argumentName} (all arguments if
     * {@code null}), including those inside game profile and score holder results, so vanilla's selector check passes.
     * Runs on the main thread with the command; {@link SelectorOverride#restore()} sets the flags back.
     */
    @Override
    public SelectorOverride bypassSelectorCheck(CommandContext<?> context, String argumentName) throws ReflectiveOperationException {
        Field flag = access.field(NativeMember.SELECTOR_USES_SELECTOR);
        final List<Object> cleared = new ArrayList<>();
        try {
            for (VanillaTree.ParsedNode parsed : VanillaTree.parsedNodes(context)) {
                if (!(parsed.node() instanceof ArgumentCommandNode<?, ?>)) continue;
                String name = parsed.node().getName();
                if (argumentName != null && !argumentName.equals(name)) continue;
                Object selector = selector(argumentValue(context, name));
                if (selector == null || !flag.getBoolean(selector)) continue;
                flag.setBoolean(selector, false);
                cleared.add(selector);
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            restore(flag, cleared);
            throw error;
        }
        return () -> restore(flag, cleared);
    }

    private static void restore(Field flag, List<Object> cleared) throws IllegalAccessException {
        for (Object selector : cleared) flag.setBoolean(selector, true);
    }

    private static Object argumentValue(CommandContext<?> context, String name) {
        try {
            return context.getArgument(name, Object.class);
        } catch (IllegalArgumentException missing) {
            return null;
        }
    }

    /** The {@code EntitySelector} itself, or the one a profile or score holder result holds; {@code null} otherwise. */
    private Object selector(Object value) throws IllegalAccessException {
        if (value == null) return null;
        Class<?> selectorType = access.type(NativeClass.ENTITY_SELECTOR);
        if (selectorType.isInstance(value)) return value;
        Class<?> type = value.getClass();
        Field field;
        synchronized (wrappedSelectors) {
            if (wrappedSelectors.containsKey(type)) {
                field = wrappedSelectors.get(type);
            } else {
                field = selectorField(type, selectorType);
                wrappedSelectors.put(type, field);
            }
        }
        return field == null ? null : field.get(value);
    }

    private static Field selectorField(Class<?> type, Class<?> selectorType) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field candidate : current.getDeclaredFields()) {
                if (candidate.getType() != selectorType || Modifier.isStatic(candidate.getModifiers())) continue;
                try {
                    candidate.setAccessible(true);
                    return candidate;
                } catch (RuntimeException inaccessible) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Also treats non-entity score holders as score holders on 1.20.3+, where {@code ScoreHolderArgument} returns
     * {@code ScoreHolder} objects whose {@code getScoreboardName()} is obfuscated on Spigot.
     */
    @Override
    public SelectorRules.Target selectorTarget(Object resolved) {
        SelectorRules.Target target = SelectorTargets.target(resolved);
        if (target.kind() == SelectorRules.Target.Kind.UNSUPPORTED && resolved != null && isScoreHolder(resolved.getClass())) {
            return SelectorRules.Target.scoreHolder();
        }
        return target;
    }

    private static boolean isScoreHolder(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Class<?> implemented : current.getInterfaces()) {
                if (implemented.getName().equals("net.minecraft.world.scores.ScoreHolder")) return true;
            }
        }
        return false;
    }

    @Override
    public ArgumentType<?> entityArgument() throws ReflectiveOperationException {
        return (ArgumentType<?>) access.method(NativeMember.ENTITY_ARGUMENT_ENTITY).invoke(null);
    }

    @Override
    public ArgumentType<?> entitiesArgument() throws ReflectiveOperationException {
        return (ArgumentType<?>) access.method(NativeMember.ENTITY_ARGUMENT_ENTITIES).invoke(null);
    }

    @Override
    public ArgumentResolver entityResolver() {
        return ArgumentResolver.of(access.method(NativeMember.ENTITY_ARGUMENT_GET_ENTITY));
    }

    @Override
    public ArgumentResolver optionalEntitiesResolver() {
        return ArgumentResolver.of(access.method(NativeMember.ENTITY_ARGUMENT_GET_OPTIONAL_ENTITIES));
    }

    @Override
    public Object withSuppressedOutput(Object source) {
        return invokeUnchecked(access.method(NativeMember.SOURCE_WITH_SUPPRESSED_OUTPUT), source);
    }

    /** {@code withSource} only exists from 1.17, so {@code feedback console|entity} is left out on 1.13 - 1.16.5. */
    @Override
    public boolean redirectsFeedback() {
        return access.has(NativeMember.SOURCE_WITH_SOURCE);
    }

    @Override
    public Object feedbackToServer(Object source) {
        return withOutput(source, minecraftServer);
    }

    @Override
    public List<Object> feedbackToEntities(Object source, Collection<?> entities) {
        List<Object> sources = new ArrayList<>();
        for (Object target : entities) sources.add(withOutput(source, target));
        return sources;
    }

    /**
     * Sends command output to {@code output} while permission and selector checks keep using the original sender, as
     * the {@code paper} adapter does: a plain {@code withSource} would also make CraftBukkit report {@code output} as the
     * Bukkit sender, letting a player run commands and selectors under the console's or another player's permissions.
     */
    private Object withOutput(Object source, Object output) {
        Class<?> commandSource = access.type(NativeClass.COMMAND_SOURCE);
        Object sender = invokeUnchecked(getBukkitSender, source);
        Object redirected = Proxy.newProxyInstance(commandSource.getClassLoader(), new Class<?>[]{commandSource},
                (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        switch (method.getName()) {
                            case "equals": return proxy == arguments[0];
                            case "hashCode": return System.identityHashCode(proxy);
                            default: return "FeedbackSource[" + output + "]";
                        }
                    }
                    if (method.getName().equals("getBukkitSender")) return sender;
                    try {
                        method.setAccessible(true);
                        return method.invoke(output, arguments);
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    }
                });
        Object copy = invokeUnchecked(access.method(NativeMember.SOURCE_WITH_SOURCE), source, redirected);
        setSilent(copy, false);
        return copy;
    }

    private void setSilent(Object source, boolean value) {
        try {
            access.field(NativeMember.SOURCE_SILENT).setBoolean(source, value);
        } catch (IllegalAccessException | RuntimeException error) {
            throw new IllegalStateException("Could not change native command feedback state", error);
        }
    }

    private static Object invokeUnchecked(Method method, Object receiver, Object... arguments) {
        try {
            return Reflection.invoke(method, receiver, arguments);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not invoke native method " + method.getName(), error);
        }
    }
}
