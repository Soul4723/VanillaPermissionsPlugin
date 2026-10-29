package io.github.soul4723.vanillapermissions.spigot;

import com.mojang.brigadier.tree.CommandNode;
import org.bukkit.command.Command;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Gives the redirecting vanilla aliases ({@code /tp}, {@code /tell}, {@code /w}, {@code /xp}, {@code /tm}) their own
 * node in the Bukkit permission of CraftBukkit's {@code VanillaCommandWrapper}, so they are gated as on Paper 1.20.6+.
 *
 * <p>CraftBukkit's {@code VanillaCommandWrapper.getPermission(CommandNode)} is
 * {@code "minecraft.command." + (node.getRedirect() == null ? node.getName() : node.getRedirect().getName())} on
 * Spigot 1.13.2 - 1.20.4 and Paper 1.13 - 1.20.5, and the wrapper's {@code execute} runs Bukkit's
 * {@code testPermission} before Brigadier. Every command that goes through {@code CraftServer#dispatchCommand} (player
 * chat commands before 1.19, {@code Player#performCommand}, {@code commands.yml} aliases) was therefore refused with
 * Bukkit's "I'm sorry, but you do not have permission" for {@code /tp} unless {@code minecraft.command.teleport} was
 * granted, whatever {@code minecraft.command.tp} said. Paper 1.20.6+ still computes the same string (its
 * {@code getPermission} follows the redirect chain), but dispatches every vanilla command through Brigadier, where this
 * plugin's {@code minecraft.command.tp} requirement replaces it and falls back to it only when the node is unset.
 *
 * <p>The wrapper's permission becomes {@code minecraft.command.<alias>;minecraft.command.<target>}. Bukkit's
 * {@code Command#testPermissionSilent} passes when any {@code ;}-separated node is granted and reads the field on every
 * call, so the alias's own node now opens it and the Brigadier requirement decides. Keeping the target node keeps the
 * defaults: {@code minecraft.command.tell} is not registered by Bukkit (unset means operators only), while
 * {@code minecraft.command.msg} defaults to everyone, and on Paper 1.20.6+ an unset {@code minecraft.command.tell} falls
 * back to that node too. Changing a {@code VanillaCommandWrapper}'s permission from a plugin once the server has
 * started follows CommandAPI's {@code SpigotCommandRegistration#fixPermissions}
 * ({@code commandapi-platforms/commandapi-spigot/commandapi-spigot-core/src/main/java/dev/jorel/commandapi/SpigotCommandRegistration.java}).
 */
final class AliasPermissions {

    static final String PREFIX = "minecraft.command.";
    static final String VANILLA_COMMAND_FIELD = "vanillaCommand";

    private final List<Change> changes = new ArrayList<>();

    /**
     * Rewrites the permission of every {@code wrapperType} in {@code commands} whose vanilla node is a root of
     * {@code vanillaRoot} redirecting to another root, still has CraftBukkit's permission and was not added by another
     * plugin. Each wrapper is changed once, also when it is registered under several labels ({@code tp} and
     * {@code minecraft:tp}). Returns the alias names changed.
     *
     * @throws IllegalStateException if {@code commands} holds no {@code wrapperType} at all
     */
    synchronized List<String> apply(Iterable<Command> commands, Class<?> wrapperType, CommandNode<?> vanillaRoot,
                                    ClassLoader serverLoader, ClassLoader ownLoader) throws ReflectiveOperationException {
        Field vanillaCommand = wrapperType.getField(VANILLA_COMMAND_FIELD);
        if (!CommandNode.class.isAssignableFrom(vanillaCommand.getType())) {
            throw new IllegalStateException(wrapperType.getName() + "." + VANILLA_COMMAND_FIELD + " is not a Brigadier node");
        }
        Set<Command> seen = Collections.newSetFromMap(new IdentityHashMap<Command, Boolean>());
        List<Change> pending = new ArrayList<>();
        for (Command command : commands) {
            if (!wrapperType.isInstance(command) || !seen.add(command)) continue;
            CommandNode<?> node = (CommandNode<?>) vanillaCommand.get(command);
            CommandNode<?> target = node == null ? null : node.getRedirect();
            if (target == null || vanillaRoot.getChild(node.getName()) != node || vanillaRoot.getChild(target.getName()) != target) continue;
            if (PluginNodes.isPluginCommand(node, serverLoader, ownLoader)) continue;
            String original = command.getPermission();
            if (!(PREFIX + target.getName()).equals(original)) continue;
            pending.add(new Change(command, node.getName(), original, permission(node.getName(), target.getName())));
        }
        if (seen.isEmpty()) throw new IllegalStateException("The command map has no " + wrapperType.getSimpleName());
        List<String> aliases = new ArrayList<>();
        for (Change change : pending) {
            change.command.setPermission(change.replacement);
            changes.add(change);
            aliases.add(change.alias);
        }
        Collections.sort(aliases);
        return aliases;
    }

    /** {@code minecraft.command.<alias>;minecraft.command.<target>}. */
    static String permission(String alias, String target) {
        return PREFIX + alias + ";" + PREFIX + target;
    }

    /** Puts back CraftBukkit's permission on every wrapper still carrying the one {@link #apply} set. */
    synchronized void restore() {
        for (int i = changes.size() - 1; i >= 0; i--) {
            Change change = changes.get(i);
            if (change.replacement.equals(change.command.getPermission())) change.command.setPermission(change.original);
        }
        changes.clear();
    }

    private static final class Change {
        final Command command;
        final String alias;
        final String original;
        final String replacement;

        Change(Command command, String alias, String original, String replacement) {
            this.command = command;
            this.alias = alias;
            this.original = original;
            this.replacement = replacement;
        }
    }
}
