package io.github.soul4723.vanillapermissions.selector;

import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Upstream-style selector rules (scope self/player/entity, limit meta, weight meta) on already-resolved
 * command targets. Platform-free: every adapter resolves its own targets and turns a {@link SelectorDenial}
 * into its own error type, so the Brigadier adapters and the pre-1.13 adapter share one rule set.
 */
public final class SelectorRules {

    /** Looks permissions and meta up through {@link PermissionManager}, as every adapter does at runtime. */
    public static final Permissions PERMISSION_MANAGER = new Permissions() {
        @Override
        public boolean hasPermission(CommandSender sender, String node, boolean fallback) {
            return PermissionManager.hasPermissionOrFallback(sender, node, fallback);
        }

        @Override
        public OptionalInt getIntegerMeta(UUID subjectId, CommandSender onlineSubject, String node) {
            return PermissionManager.getIntegerMeta(subjectId, onlineSubject, node);
        }
    };

    public static final String LIMIT_PREFIX = "minecraft.selector.limit.";
    public static final String WEIGHT_PREFIX = "minecraft.selector.weight.";

    private SelectorRules() {}

    /** One resolved target. Profile targets use PLAYER with an optional online sender for meta lookups. */
    public static final class Target {
        public enum Kind { PLAYER, ENTITY, SCORE_HOLDER, UNSUPPORTED }

        private static final Target SCORE_HOLDER = new Target(Kind.SCORE_HOLDER, null, null);
        private static final Target UNSUPPORTED = new Target(Kind.UNSUPPORTED, null, null);

        private final Kind kind;
        private final UUID id;
        private final CommandSender metadataSubject;

        private Target(Kind kind, UUID id, CommandSender metadataSubject) {
            this.kind = kind;
            this.id = id;
            this.metadataSubject = metadataSubject;
        }

        /** A Bukkit entity; players become PLAYER targets with themselves as metadata subject. */
        public static Target entity(Entity entity) {
            if (entity instanceof Player) {
                Player player = (Player) entity;
                return player(player.getUniqueId(), player);
            }
            return new Target(Kind.ENTITY, entity.getUniqueId(), null);
        }

        /** A player by id, e.g. from a game profile; uses the online player, if any, for meta lookups. */
        public static Target player(UUID id) {
            return player(id, Bukkit.getPlayer(id));
        }

        public static Target player(UUID id, Player onlinePlayer) {
            return new Target(Kind.PLAYER, id, onlinePlayer);
        }

        /** A scoreboard name with no entity behind it; ignores scopes and weight. */
        public static Target scoreHolder() {
            return SCORE_HOLDER;
        }

        /** A value the adapter cannot classify; always denied. */
        public static Target unsupported() {
            return UNSUPPORTED;
        }

        public Kind kind() {
            return kind;
        }

        public UUID id() {
            return id;
        }

        public CommandSender metadataSubject() {
            return metadataSubject;
        }
    }

    /** Permission lookup seam; keeps target resolution and permission providers outside these rules. */
    public interface Permissions {
        boolean hasPermission(CommandSender sender, String node, boolean fallback);

        OptionalInt getIntegerMeta(UUID subjectId, CommandSender onlineSubject, String node);
    }

    /** Whether {@code sender} may use selector syntax: {@code minecraft.selector}, else vanilla's answer. */
    public static boolean allowSelectorSyntax(CommandSender sender, boolean vanillaAllowed, Permissions permissions) {
        return permissions.hasPermission(sender, "minecraft.selector", vanillaAllowed);
    }

    /**
     * Checks {@code selected} against the selector nodes under {@link #selectorPath}. Non-player senders are never
     * restricted. Limit and weight meta fall back to shorter keys, see {@link #fallbackKeys}. {@code candidates} is
     * {@code selected} unless the server resolves the selector again when the command runs and may then pick other
     * targets ({@link RandomSelectors}): the limit applies to {@code selected}, the scope and weight rules to every
     * target in {@code selected} and in {@code candidates}.
     *
     * @return the first rule the selection breaks, or empty when it is allowed
     */
    public static Optional<SelectorDenial> check(
            CommandSender sender,
            List<String> parsedNodePath,
            String argumentName,
            Collection<Target> selected,
            Collection<Target> candidates,
            Permissions permissions) {
        if (!(sender instanceof Player)) return Optional.empty();
        Player player = (Player) sender;

        String path = selectorPath(parsedNodePath, argumentName);
        OptionalInt limit = meta(permissions, player.getUniqueId(), sender, fallbackKeys(LIMIT_PREFIX, path));
        if (limit.isPresent() && limit.getAsInt() < selected.size()) return Optional.of(SelectorDenial.tooMany(limit.getAsInt()));

        boolean entitiesAllowed = permissions.hasPermission(sender, "minecraft.selector.entity." + path, true);
        boolean playersAllowed = permissions.hasPermission(sender, "minecraft.selector.player." + path, true);
        boolean selfAllowed = permissions.hasPermission(sender, "minecraft.selector.self." + path, true);
        List<String> weightKeys = fallbackKeys(WEIGHT_PREFIX, path);
        OptionalInt sourceWeight = meta(permissions, player.getUniqueId(), sender, weightKeys);
        List<Target> checked = new ArrayList<>(selected);
        if (candidates != selected) checked.addAll(candidates);
        for (Target target : checked) {
            if (target.kind() == Target.Kind.UNSUPPORTED) return denial(SelectorDenial.Reason.UNSUPPORTED);
            if (target.kind() == Target.Kind.SCORE_HOLDER) continue;
            if (target.kind() == Target.Kind.ENTITY) {
                if (!entitiesAllowed) return denial(SelectorDenial.Reason.ENTITY);
                continue;
            }

            if (target.id().equals(player.getUniqueId())) {
                if (!selfAllowed) return denial(SelectorDenial.Reason.SELF);
                continue;
            }
            if (!playersAllowed) return denial(SelectorDenial.Reason.PLAYER);

            if (sourceWeight.isPresent()) {
                OptionalInt targetWeight = meta(permissions, target.id(), target.metadataSubject(), weightKeys);
                if (targetWeight.isPresent() && targetWeight.getAsInt() > sourceWeight.getAsInt()) {
                    return denial(SelectorDenial.Reason.WEIGHT);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The meta keys tried for {@code prefix + path}, most specific first: the full key, then the key with its last
     * segment dropped, down to {@code prefix + <command>.<argument>}. LuckPerms meta has no wildcards, so without this
     * every way of typing a command would need its own line; exact keys still win, so upstream configs keep working.
     */
    public static List<String> fallbackKeys(String prefix, String path) {
        List<String> keys = new ArrayList<>();
        String key = path;
        while (true) {
            keys.add(prefix + key);
            int dot = key.lastIndexOf('.');
            // Stop at <command>.<argument>: a bare command name says nothing about which argument is limited.
            if (dot < 0 || key.indexOf('.') == dot) break;
            key = key.substring(0, dot);
        }
        return keys;
    }

    /** The value of the first of {@code keys} set for the subject, or empty if none is. */
    public static OptionalInt meta(Permissions permissions, UUID subjectId, CommandSender onlineSubject, List<String> keys) {
        for (String key : keys) {
            OptionalInt value = permissions.getIntegerMeta(subjectId, onlineSubject, key);
            if (value.isPresent()) return value;
        }
        return OptionalInt.empty();
    }

    /** {@code <command>.<argument>.<rest of the parsed path>}, matching upstream's selector node layout. */
    public static String selectorPath(List<String> parsedNodePath, String argumentName) {
        StringBuilder path = new StringBuilder(parsedNodePath.get(0)).append('.').append(argumentName);
        for (int i = 1; i < parsedNodePath.size(); i++) path.append('.').append(parsedNodePath.get(i));
        return path.toString();
    }

    private static Optional<SelectorDenial> denial(SelectorDenial.Reason reason) {
        return Optional.of(SelectorDenial.of(reason));
    }
}
