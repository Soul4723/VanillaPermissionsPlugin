package io.github.soul4723.vanillapermissions.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.LiteralMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

/** Enforces upstream-style scope, target-count, and weight rules on resolved command arguments. */
public final class SelectorPolicy {

    private static final SimpleCommandExceptionType DENIED =
            new SimpleCommandExceptionType(new LiteralMessage("Entity selection is not allowed"));

    private SelectorPolicy() {}

    /** Result of resolving one native argument value. Profile targets use PLAYER with an optional online sender. */
    public record Target(Kind kind, UUID id, CommandSender metadataSubject) {
        public Target {
            if (kind == null) throw new IllegalArgumentException("kind is required");
            if ((kind == Kind.PLAYER || kind == Kind.ENTITY) && id == null) {
                throw new IllegalArgumentException("target id is required");
            }
            if (metadataSubject != null && !(metadataSubject instanceof Player)) {
                throw new IllegalArgumentException("target metadata subject must be a player");
            }
        }

        public enum Kind { PLAYER, ENTITY, SCORE_HOLDER, UNSUPPORTED }
    }

    /** LuckPerms lookup seam keeps NMS target resolution outside this policy. */
    public interface Permissions {
        boolean hasPermission(CommandSender sender, String node, boolean fallback);
        OptionalInt getIntegerMeta(UUID subjectId, CommandSender onlineSubject, String node);
    }

    public static boolean allowSelectorSyntax(CommandSender sender, boolean vanillaAllowed, Permissions permissions) {
        return permissions.hasPermission(sender, "minecraft.selector", vanillaAllowed);
    }

    public static void validate(
            CommandSender sender,
            List<String> parsedNodePath,
            String argumentName,
            Collection<Target> selected,
            Permissions permissions) throws CommandSyntaxException {
        if (!(sender instanceof Player player)) return;
        if (selected == null || permissions == null) throw new IllegalArgumentException("selection and permissions are required");

        String path = selectorPath(parsedNodePath, argumentName);
        OptionalInt limit = permissions.getIntegerMeta(player.getUniqueId(), sender, "minecraft.selector.limit." + path);
        if (limit.isPresent() && limit.getAsInt() < selected.size()) throw DENIED.create();

        boolean entitiesAllowed = permissions.hasPermission(sender, "minecraft.selector.entity." + path, true);
        boolean playersAllowed = permissions.hasPermission(sender, "minecraft.selector.player." + path, true);
        boolean selfAllowed = permissions.hasPermission(sender, "minecraft.selector.self." + path, true);
        String weightNode = "minecraft.selector.weight." + path;
        OptionalInt sourceWeight = permissions.getIntegerMeta(player.getUniqueId(), sender, weightNode);
        for (Target target : selected) {
            if (target.kind() == Target.Kind.UNSUPPORTED) throw DENIED.create();
            if (target.kind() == Target.Kind.SCORE_HOLDER) continue;
            if (target.kind() == Target.Kind.ENTITY) {
                if (!entitiesAllowed) throw DENIED.create();
                continue;
            }

            if (target.id().equals(player.getUniqueId())) {
                if (!selfAllowed) throw DENIED.create();
                continue;
            }
            if (!playersAllowed) throw DENIED.create();

            if (sourceWeight.isPresent()) {
                OptionalInt targetWeight = permissions.getIntegerMeta(target.id(), target.metadataSubject(), weightNode);
                if (targetWeight.isPresent() && targetWeight.getAsInt() > sourceWeight.getAsInt()) {
                    throw DENIED.create();
                }
            }
        }
    }

    static String selectorPath(List<String> parsedNodePath, String argumentName) {
        if (parsedNodePath == null || parsedNodePath.isEmpty()) {
            throw new IllegalArgumentException("parsed command path is required");
        }
        if (argumentName == null || argumentName.isBlank()) {
            throw new IllegalArgumentException("argument name is required");
        }

        StringBuilder path = new StringBuilder(parsedNodePath.get(0)).append('.').append(argumentName);
        for (int i = 1; i < parsedNodePath.size(); i++) path.append('.').append(parsedNodePath.get(i));
        return path.toString();
    }
}
