package io.github.soul4723.vanillapermissions.spigot;

import com.mojang.brigadier.context.CommandContext;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The Minecraft classes and members the {@code spigot} adapter reaches by name, with their runtime name per
 * {@link MappingGeneration}, including what CraftBukkit adds to them for operator levels. Each entry lists the
 * generations where its name changes, oldest first; a server uses the newest row at or below its own generation, so a
 * token newer than every row (an untested newer Spigot) uses the newest row, and install-time validation in
 * {@link NativeAccess} fails closed if that name is wrong. {@link #ABSENT} marks a generation where the member does
 * not exist.
 *
 * <p>The style follows NBT-API's {@code ReflectionMethod} enum ({@code new Since(MinecraftVersion.MC1_18_R1, "...")},
 * {@code item-nbt-api/src/main/java/de/tr7zw/changeme/nbtapi/utils/nmsmappings/ReflectionMethod.java}) and CommandAPI's
 * {@code SafeVarHandle.ofOrNull(class, spigotName, mojangName, type)}
 * ({@code commandapi-core/src/main/java/dev/jorel/commandapi/SafeVarHandle.java}):
 * one name per mapping range, a Mojang name beside the Spigot ones, and a declared type that is checked at lookup.
 *
 * <p>Sources: the names of every
 * Spigot release from 1.13 to 26.3, taken by joining Mojang's server mappings with Spigot BuildData's class and member
 * mappings, and checked with {@code javap} against the Spigot 1.13.2, 1.16.5, 1.17.1, 1.20.4, 1.21.10, 1.21.11 and 26.3
 * jars. Live-tested generations are listed in {@code docs/compatibility.md}; the others are mapping-derived.
 *
 * <p>Not listed, because they keep their names on every version: CraftBukkit's {@code getBukkitSender()} and
 * {@code getBukkitEntity()}, which {@link SpigotBridge} looks up directly, and the CraftServer, command map and
 * MinecraftServer calls in {@link CraftServerAccess}, {@link AliasPermissions} and {@link SpigotAdapter}.
 */
final class SpigotNames {

    /** Marks a generation where a member does not exist. */
    static final String ABSENT = null;
    /** Replaced by {@link MappingGeneration#nmsPackage()} in class names before 1.17. */
    static final String NMS = "{nms}";

    private SpigotNames() {}

    /** One name and the generation it applies from. */
    static final class Since {
        final MappingGeneration from;
        final String name;

        Since(String token, String name) {
            this.from = MappingGeneration.of(token);
            this.name = name;
        }
    }

    static Since since(String token, String name) {
        return new Since(token, name);
    }

    /** The newest row at or below {@code generation}, or the Mojang name; {@link #ABSENT} if it does not exist there. */
    static String nameFor(List<Since> rows, String mojang, MappingGeneration generation, String what) {
        if (generation.isMojang()) return mojang;
        Since match = null;
        for (Since row : rows) {
            if (row.from.compareTo(generation) <= 0) match = row;
        }
        if (match == null) throw new IllegalStateException("No runtime name for " + what + " on " + generation);
        return match.name == null ? ABSENT : match.name.replace(NMS, generation.nmsPackage());
    }

    /**
     * The operator-level shape an entry belongs to. Up to 1.21.10 a command source has an int level and CraftBukkit's
     * {@code hasPermission(int, String)}; from 1.21.11 it has a {@code PermissionSet}. Mojang names cover both shapes
     * (Paper 1.20.5 has int levels, Spigot 26.x a PermissionSet), so {@link NativeAccess} picks the shape from the
     * source class and resolves only the entries of that shape. {@link #PERMISSION_SET} entries have rows from
     * {@code v1_21_R7}.
     */
    enum LevelShape { ANY, INT, PERMISSION_SET }

    /** Server classes, by runtime name. */
    enum NativeClass {
        /** Mojang {@code CommandSourceStack}. */
        SOURCE("net.minecraft.commands.CommandSourceStack",
                since("v1_13_R1", NMS + ".CommandListenerWrapper"),
                since("v1_17_R1", "net.minecraft.commands.CommandListenerWrapper")),
        /** Mojang {@code CommandSource}: what a command's output goes to. */
        COMMAND_SOURCE("net.minecraft.commands.CommandSource",
                since("v1_13_R1", NMS + ".ICommandListener"),
                since("v1_17_R1", "net.minecraft.commands.ICommandListener")),
        ENTITY_ARGUMENT("net.minecraft.commands.arguments.EntityArgument",
                since("v1_13_R1", NMS + ".ArgumentEntity"),
                since("v1_17_R1", "net.minecraft.commands.arguments.ArgumentEntity")),
        GAME_PROFILE_ARGUMENT("net.minecraft.commands.arguments.GameProfileArgument",
                since("v1_13_R1", NMS + ".ArgumentProfile"),
                since("v1_17_R1", "net.minecraft.commands.arguments.ArgumentProfile")),
        SCORE_HOLDER_ARGUMENT("net.minecraft.commands.arguments.ScoreHolderArgument",
                since("v1_13_R1", NMS + ".ArgumentScoreholder"),
                since("v1_17_R1", "net.minecraft.commands.arguments.ArgumentScoreholder")),
        ENTITY_SELECTOR("net.minecraft.commands.arguments.selector.EntitySelector",
                since("v1_13_R1", NMS + ".EntitySelector"),
                since("v1_17_R1", "net.minecraft.commands.arguments.selector.EntitySelector")),
        ENTITY("net.minecraft.world.entity.Entity",
                since("v1_13_R1", NMS + ".Entity"),
                since("v1_17_R1", "net.minecraft.world.entity.Entity")),
        /** The class declaring {@code getPermissionLevel()}: {@code Entity} up to 1.21.1, {@code Player} from 1.21.2. */
        PERMISSION_LEVEL_HOLDER(LevelShape.INT, "net.minecraft.world.entity.Entity",
                since("v1_13_R1", NMS + ".Entity"),
                since("v1_17_R1", "net.minecraft.world.entity.Entity"),
                since("v1_21_R2", "net.minecraft.world.entity.player.EntityHuman")),
        PLAYER(LevelShape.PERMISSION_SET, "net.minecraft.world.entity.player.Player",
                since("v1_21_R7", "net.minecraft.world.entity.player.EntityHuman")),
        PERMISSION_SET(LevelShape.PERMISSION_SET, "net.minecraft.server.permissions.PermissionSet",
                since("v1_21_R7", "net.minecraft.server.permissions.PermissionSet")),
        PERMISSION(LevelShape.PERMISSION_SET, "net.minecraft.server.permissions.Permission",
                since("v1_21_R7", "net.minecraft.server.permissions.Permission")),
        /** Mojang {@code Permission.HasCommandLevel}: the check for one operator level. */
        HAS_COMMAND_LEVEL(LevelShape.PERMISSION_SET, "net.minecraft.server.permissions.Permission$HasCommandLevel",
                since("v1_21_R7", "net.minecraft.server.permissions.Permission$b")),
        PERMISSION_LEVEL(LevelShape.PERMISSION_SET, "net.minecraft.server.permissions.PermissionLevel",
                since("v1_21_R7", "net.minecraft.server.permissions.PermissionLevel")),
        LEVEL_BASED_PERMISSION_SET(LevelShape.PERMISSION_SET, "net.minecraft.server.permissions.LevelBasedPermissionSet",
                since("v1_21_R7", "net.minecraft.server.permissions.LevelBasedPermissionSet")),
        PERMISSIONS(LevelShape.PERMISSION_SET, "net.minecraft.server.permissions.Permissions",
                since("v1_21_R7", "net.minecraft.server.permissions.Permissions")),
        /** CraftBukkit's {@code CommandSourceStack.DelegatePermissionSet}: the source's PermissionSet plus Bukkit nodes. */
        DELEGATE_PERMISSION_SET(LevelShape.PERMISSION_SET, "net.minecraft.commands.CommandSourceStack$DelegatePermissionSet",
                since("v1_21_R7", "net.minecraft.commands.CommandListenerWrapper$DelegatePermissionSet"));

        final LevelShape shape;
        final String mojang;
        final List<Since> rows;

        NativeClass(String mojang, Since... rows) {
            this(LevelShape.ANY, mojang, rows);
        }

        NativeClass(LevelShape shape, String mojang, Since... rows) {
            this.shape = shape;
            this.mojang = mojang;
            this.rows = Collections.unmodifiableList(Arrays.asList(rows));
        }

        String name(MappingGeneration generation) {
            return nameFor(rows, mojang, generation, "class " + name());
        }

        /** Whether this class has a name on {@code generation}, so {@link #name} does not throw. */
        boolean isNamedOn(MappingGeneration generation) {
            return generation.isMojang() || rows.get(0).from.compareTo(generation) <= 0;
        }
    }

    enum Kind { FIELD, STATIC_FIELD, METHOD, STATIC_METHOD }

    /**
     * Server members, by runtime name. {@code type} is a field's type or a method's return type; {@code parameters}
     * are a method's parameter types. Both are a {@link Class} or a {@link NativeClass}.
     */
    enum NativeMember {
        /** {@code CommandSourceStack.permissionLevel}, the source's operator level (0 - 4). */
        SOURCE_PERMISSION_LEVEL(LevelShape.INT, Kind.FIELD, NativeClass.SOURCE, int.class, params(), "permissionLevel",
                since("v1_13_R1", "f"),
                since("v1_21_R3", "g")),
        /** CraftBukkit's {@code CommandSourceStack.hasPermission(int, String)}: the level, or the Bukkit node. */
        SOURCE_HAS_PERMISSION(LevelShape.INT, Kind.METHOD, NativeClass.SOURCE, boolean.class, params(int.class, String.class),
                "hasPermission",
                since("v1_13_R1", "hasPermission")),
        /** {@code CommandSourceStack.silent}: output suppressed. */
        SOURCE_SILENT(Kind.FIELD, NativeClass.SOURCE, boolean.class, params(), "silent",
                since("v1_13_R1", "j"),
                since("v1_21_R3", "k")),
        /** {@code CommandSourceStack.withPermission(int)}. */
        SOURCE_WITH_PERMISSION(LevelShape.INT, Kind.METHOD, NativeClass.SOURCE, NativeClass.SOURCE, params(int.class), "withPermission",
                since("v1_13_R1", "a")),
        /** {@code CommandSourceStack.permissions}, the source's own PermissionSet; CraftBukkit's {@code permissions()} adds Bukkit nodes. */
        SOURCE_PERMISSIONS(LevelShape.PERMISSION_SET, Kind.FIELD, NativeClass.SOURCE, NativeClass.PERMISSION_SET, params(), "permissions",
                since("v1_21_R7", "g")),
        /** CraftBukkit's {@code CommandSourceStack.bukkitPermissions}: the {@link NativeClass#DELEGATE_PERMISSION_SET} for the source. */
        SOURCE_BUKKIT_PERMISSIONS(LevelShape.PERMISSION_SET, Kind.FIELD, NativeClass.SOURCE, NativeClass.PERMISSION_SET, params(),
                "bukkitPermissions",
                since("v1_21_R7", "bukkitPermissions")),
        /** CraftBukkit's {@code DelegatePermissionSet.hasPermission(boolean vanilla, String node)}. */
        DELEGATE_HAS_PERMISSION(LevelShape.PERMISSION_SET, Kind.METHOD, NativeClass.DELEGATE_PERMISSION_SET, boolean.class,
                params(boolean.class, String.class), "hasPermission",
                since("v1_21_R7", "hasPermission")),
        /** {@code CommandSourceStack.withPermission(PermissionSet)}. */
        SOURCE_WITH_PERMISSION_SET(LevelShape.PERMISSION_SET, Kind.METHOD, NativeClass.SOURCE, NativeClass.SOURCE,
                params(NativeClass.PERMISSION_SET), "withPermission",
                since("v1_21_R7", "a")),
        /** {@code Player.permissions()}; other entities have no operator level. */
        PLAYER_PERMISSIONS(LevelShape.PERMISSION_SET, Kind.METHOD, NativeClass.PLAYER, NativeClass.PERMISSION_SET, params(), "permissions",
                since("v1_21_R7", "I")),
        /** {@code PermissionSet.hasPermission(Permission)}. */
        SET_HAS_PERMISSION(LevelShape.PERMISSION_SET, Kind.METHOD, NativeClass.PERMISSION_SET, boolean.class, params(NativeClass.PERMISSION),
                "hasPermission",
                since("v1_21_R7", "hasPermission")),
        /** {@code PermissionLevel.id()}: 0 - 4. */
        PERMISSION_LEVEL_ID(LevelShape.PERMISSION_SET, Kind.METHOD, NativeClass.PERMISSION_LEVEL, int.class, params(), "id",
                since("v1_21_R7", "a")),
        /** {@code LevelBasedPermissionSet.forLevel(PermissionLevel)}. */
        FOR_LEVEL(LevelShape.PERMISSION_SET, Kind.STATIC_METHOD, NativeClass.LEVEL_BASED_PERMISSION_SET, NativeClass.LEVEL_BASED_PERMISSION_SET,
                params(NativeClass.PERMISSION_LEVEL), "forLevel",
                since("v1_21_R7", "a")),
        /** {@code Permissions.COMMANDS_ENTITY_SELECTORS}. */
        SELECTORS_PERMISSION(LevelShape.PERMISSION_SET, Kind.STATIC_FIELD, NativeClass.PERMISSIONS, NativeClass.PERMISSION, params(),
                "COMMANDS_ENTITY_SELECTORS",
                since("v1_21_R7", "e")),
        /** {@code CommandSourceStack.withSuppressedOutput()}. */
        SOURCE_WITH_SUPPRESSED_OUTPUT(Kind.METHOD, NativeClass.SOURCE, NativeClass.SOURCE, params(), "withSuppressedOutput",
                since("v1_13_R1", "a")),
        /** {@code CommandSourceStack.withSource(CommandSource)}; absent before 1.17. */
        SOURCE_WITH_SOURCE(Kind.METHOD, NativeClass.SOURCE, NativeClass.SOURCE, params(NativeClass.COMMAND_SOURCE), "withSource",
                since("v1_13_R1", ABSENT),
                since("v1_17_R1", "a")),
        /** {@code EntityArgument.entity()}. */
        ENTITY_ARGUMENT_ENTITY(Kind.STATIC_METHOD, NativeClass.ENTITY_ARGUMENT, NativeClass.ENTITY_ARGUMENT, params(), "entity",
                since("v1_13_R1", "a")),
        /** {@code EntityArgument.entities()}. */
        ENTITY_ARGUMENT_ENTITIES(Kind.STATIC_METHOD, NativeClass.ENTITY_ARGUMENT, NativeClass.ENTITY_ARGUMENT, params(), "entities",
                since("v1_13_R1", "b"),
                since("v1_14_R1", "multipleEntities"),
                since("v1_18_R1", "b")),
        /** {@code EntityArgument.getEntity(CommandContext, String)}. */
        ENTITY_ARGUMENT_GET_ENTITY(Kind.STATIC_METHOD, NativeClass.ENTITY_ARGUMENT, NativeClass.ENTITY, params(CommandContext.class, String.class),
                "getEntity",
                since("v1_13_R1", "a")),
        /** {@code EntityArgument.getOptionalEntities(CommandContext, String)}. */
        ENTITY_ARGUMENT_GET_OPTIONAL_ENTITIES(Kind.STATIC_METHOD, NativeClass.ENTITY_ARGUMENT, Collection.class,
                params(CommandContext.class, String.class), "getOptionalEntities",
                since("v1_13_R1", "c")),
        /** {@code GameProfileArgument.getGameProfiles(CommandContext, String)}. */
        GAME_PROFILE_ARGUMENT_GET(Kind.STATIC_METHOD, NativeClass.GAME_PROFILE_ARGUMENT, Collection.class,
                params(CommandContext.class, String.class), "getGameProfiles",
                since("v1_13_R1", "a")),
        /** {@code ScoreHolderArgument.getNamesWithDefaultWildcard(CommandContext, String)}. */
        SCORE_HOLDER_ARGUMENT_GET_WITH_WILDCARD(Kind.STATIC_METHOD, NativeClass.SCORE_HOLDER_ARGUMENT, Collection.class,
                params(CommandContext.class, String.class), "getNamesWithDefaultWildcard",
                since("v1_13_R1", "c")),
        /** {@code EntitySelector.usesSelector}: vanilla checks selector permission only when it is set. */
        SELECTOR_USES_SELECTOR(Kind.FIELD, NativeClass.ENTITY_SELECTOR, boolean.class, params(), "usesSelector",
                since("v1_13_R1", "m"),
                since("v1_14_R1", "checkPermissions"),
                since("v1_17_R1", "o"),
                since("v1_19_R2", "p")),
        /** {@code getPermissionLevel()} on {@link NativeClass#PERMISSION_LEVEL_HOLDER}; protected. */
        ENTITY_PERMISSION_LEVEL(LevelShape.INT, Kind.METHOD, NativeClass.PERMISSION_LEVEL_HOLDER, int.class, params(), "getPermissionLevel",
                since("v1_13_R1", "y"),
                since("v1_18_R1", "B"),
                since("v1_19_R2", "C"),
                since("v1_19_R3", "B"),
                since("v1_20_R2", "D"),
                since("v1_20_R3", "F"),
                since("v1_21_R1", "G"),
                since("v1_21_R4", "F"),
                since("v1_21_R5", "G"),
                since("v1_21_R6", "I"));

        final LevelShape shape;
        final Kind kind;
        final NativeClass owner;
        final Object type;
        final List<Object> parameters;
        final String mojang;
        final List<Since> rows;

        NativeMember(Kind kind, NativeClass owner, Object type, Object[] parameters, String mojang, Since... rows) {
            this(LevelShape.ANY, kind, owner, type, parameters, mojang, rows);
        }

        NativeMember(LevelShape shape, Kind kind, NativeClass owner, Object type, Object[] parameters, String mojang, Since... rows) {
            this.shape = shape;
            this.kind = kind;
            this.owner = owner;
            this.type = type;
            this.parameters = Collections.unmodifiableList(Arrays.asList(parameters));
            this.mojang = mojang;
            this.rows = Collections.unmodifiableList(Arrays.asList(rows));
        }

        String name(MappingGeneration generation) {
            return nameFor(rows, mojang, generation, owner + "." + name());
        }
    }

    private static Object[] params(Object... types) {
        return types;
    }
}
