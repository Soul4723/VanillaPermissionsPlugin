package io.github.soul4723.vanillapermissions.brigadier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import io.github.soul4723.vanillapermissions.selector.SelectorRules;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;

/**
 * Everything {@link BrigadierPermissions} needs from one family of servers: the server-specific half of the
 * Brigadier adapters. The shared half (walking the tree, the requirement and executor wrappers, the {@code /gamemode}
 * literals, the {@code /execute} extensions, selector rules, install and rollback) lives in this module and only
 * uses Brigadier and the Bukkit 1.13 API.
 *
 * <p>Native objects (command sources, entities) are plain {@code Object}s, so this module never links a server
 * class. Implementations: {@code paper.PaperBridge} (Mojang names, Paper 1.20.6+) and {@code spigot.SpigotBridge}
 * (Spigot names, Spigot 1.13+ and Paper 1.13 - 1.20.5).
 *
 * <p>The shape follows CommandAPI's {@code NMS<CommandListenerWrapper>} interface
 * ({@code commandapi-bukkit-core/src/main/java/dev/jorel/commandapi/nms/NMS.java}): shared command code calls one
 * per-server interface for sender lookup and argument resolution. Unlike CommandAPI it is not generic over the
 * source type, because nothing here is compiled against a server.
 *
 * <p>Methods may be called off the main thread: Paper 1.16.5+ builds the client command tree on worker threads, so
 * {@link #sender} runs there through the requirement wrapper.
 */
public interface BrigadierBridge {

    // ---- Install ----------------------------------------------------------------------------------------------

    /**
     * Looks up whatever the other methods need, before the tree in {@code dispatcher} is changed. Called at the start
     * of every {@link BrigadierPermissions#install}; throw to refuse the install, which then changes nothing.
     */
    void prepare(CommandDispatcher<?> dispatcher) throws ReflectiveOperationException;

    /**
     * The vanilla command nodes in {@code dispatcher} with their permission paths. Use
     * {@link VanillaTree#fromMinecraftAliases} for a dispatcher that also holds plugin commands (Paper's) and
     * {@link VanillaTree#allCommands} for one that holds only vanilla commands (CraftBukkit's
     * {@code vanillaCommandDispatcher}). An empty list fails the install.
     */
    List<VanillaTree.VanillaRoot> vanillaRoots(CommandDispatcher<?> dispatcher);

    // ---- Command sources --------------------------------------------------------------------------------------

    /**
     * The Bukkit sender behind a native command source (CraftBukkit's {@code getBukkitSender()} on every version).
     * Throws if the lookup fails; callers then fall back to vanilla's own requirement.
     */
    CommandSender sender(Object source) throws ReflectiveOperationException;

    // ---- Operator levels --------------------------------------------------------------------------------------

    /** Vanilla's own selector check for {@code source}: level 2 or the Bukkit node {@code minecraft.command.selector}. */
    boolean allowsSelectors(Object source) throws ReflectiveOperationException;

    /** Operator level (0 - 4) of a native command source or entity; entities without one are level 0. */
    int operatorLevel(Object sourceOrEntity) throws ReflectiveOperationException;

    /** A copy of {@code source} whose operator level is at most {@code level}; never raises it. */
    Object withOperatorLevelAtMost(Object source, int level) throws ReflectiveOperationException;

    // ---- Selectors --------------------------------------------------------------------------------------------

    /**
     * How to resolve an argument of {@code type} through vanilla if it can hold {@code @} selectors (entities, game
     * profiles, score holders), or {@code null} for every other type. Throw if {@code type} is a selector type whose
     * resolver cannot be found.
     */
    ArgumentResolver selectorResolver(ArgumentType<?> type);

    /**
     * Whether vanilla already skips its selector check for {@code source} (Paper: {@code bypassSelectorPermissions}
     * is set). Return {@code false} where there is no such state.
     */
    boolean selectorCheckBypassed(Object source) throws ReflectiveOperationException;

    /**
     * Makes vanilla skip its own selector permission check while this plugin's checks and the command run with
     * {@code context}, until {@link SelectorOverride#restore()}. {@code argumentName} limits it to that argument;
     * {@code null} means every argument. Paper sets {@code bypassSelectorPermissions} on the source; Spigot and
     * Paper 1.13 - 1.20.4 must instead clear the parsed {@code EntitySelector}s' "uses selector" flag, because
     * {@code bypassSelectorPermissions = true} forces the refusal on Paper 1.19.4 - 1.20.4.
     */
    SelectorOverride bypassSelectorCheck(CommandContext<?> context, String argumentName) throws ReflectiveOperationException;

    /** What one resolved value is for the selector rules. The default handles every value 1.0.0 handles on Paper. */
    default SelectorRules.Target selectorTarget(Object resolved) {
        return SelectorTargets.target(resolved);
    }

    // ---- /gamemode --------------------------------------------------------------------------------------------

    /**
     * Literal nodes {@code survival}, {@code creative}, {@code adventure}, {@code spectator}, each with a player
     * argument, that replace {@code modeArgument} (a {@code GameModeArgument} node, Minecraft 1.19.4+). Built once
     * per install and added to every vanilla {@code /gamemode} root. The default is name-free: it parses each mode
     * with the argument's own type and re-runs the original executors (see {@link GameModeLiterals#nameFree}).
     */
    default List<CommandNode<Object>> gameModeLiterals(CommandDispatcher<?> dispatcher, ArgumentCommandNode<Object, ?> modeArgument)
            throws ReflectiveOperationException, CommandSyntaxException {
        return GameModeLiterals.nameFree(modeArgument);
    }

    // ---- /execute extensions ----------------------------------------------------------------------------------

    /** Argument type for exactly one entity (vanilla {@code EntityArgument.entity()}). */
    ArgumentType<?> entityArgument() throws ReflectiveOperationException;

    /** Argument type for any number of entities (vanilla {@code EntityArgument.entities()}). */
    ArgumentType<?> entitiesArgument() throws ReflectiveOperationException;

    /** Resolves an {@link #entityArgument()} argument to one native entity (vanilla {@code EntityArgument.getEntity}). */
    ArgumentResolver entityResolver();

    /** Resolves an {@link #entitiesArgument()} argument to a possibly empty collection of native entities. */
    ArgumentResolver optionalEntitiesResolver();

    /** {@code source} with its command output suppressed ({@code execute ... feedback silent}). */
    Object withSuppressedOutput(Object source);

    /**
     * Whether {@code feedback console} and {@code feedback entity} can be offered. When {@code false} only
     * {@code feedback silent} is added and the two methods below are never called.
     */
    boolean redirectsFeedback();

    /**
     * {@code source} with output sent to the server console, while permission and selector checks keep using the
     * original sender ({@code feedback console}).
     */
    Object feedbackToServer(Object source);

    /** One copy of {@code source} per entity, each sending output to that entity ({@code feedback entity}). */
    List<Object> feedbackToEntities(Object source, Collection<?> entities);
}
