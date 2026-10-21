package io.github.soul4723.vanillapermissions.spigot;

import com.mojang.brigadier.context.CommandContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The server classes and members the {@code spigot} adapter reaches by name, with their runtime name per
 * {@link MappingGeneration}. Each entry lists the generations where its name changes, oldest first; a server uses the
 * newest row at or below its own generation, so a token newer than every row (an untested newer Spigot) uses the
 * newest row, and install-time validation in {@link NativeAccess} fails closed if that name is wrong. {@link #ABSENT}
 * marks a generation where the member does not exist.
 *
 * <p>The style follows NBT-API's {@code ReflectionMethod} enum ({@code new Since(MinecraftVersion.MC1_18_R1, "...")},
 * {@code item-nbt-api/src/main/java/de/tr7zw/changeme/nbtapi/utils/nmsmappings/ReflectionMethod.java}) and CommandAPI's
 * {@code SafeVarHandle.ofOrNull(class, spigotName, mojangName, type)}
 * ({@code commandapi-core/src/main/java/dev/jorel/commandapi/SafeVarHandle.java}):
 * one name per mapping range, a Mojang name beside the Spigot ones, and a declared type that is checked at lookup.
 *
 * <p>Sources: the names of every
 * Spigot release from 1.13 to 26.3, taken by joining Mojang's server mappings with Spigot BuildData's class and member
 * mappings, and checked with {@code javap} against the Spigot 1.13.2, 1.16.5, 1.17.1 and 1.20.4 jars. Live-tested
 * generations are listed in {@code docs/compatibility.md}; the others are mapping-derived.
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
        PERMISSION_LEVEL_HOLDER("net.minecraft.world.entity.Entity",
                since("v1_13_R1", NMS + ".Entity"),
                since("v1_17_R1", "net.minecraft.world.entity.Entity"),
                since("v1_21_R2", "net.minecraft.world.entity.player.EntityHuman"));

        final String mojang;
        final List<Since> rows;

        NativeClass(String mojang, Since... rows) {
            this.mojang = mojang;
            this.rows = Collections.unmodifiableList(Arrays.asList(rows));
        }

        String name(MappingGeneration generation) {
            return nameFor(rows, mojang, generation, "class " + name());
        }
    }

    enum Kind { FIELD, METHOD, STATIC_METHOD }

    /**
     * Server members, by runtime name. {@code type} is a field's type or a method's return type; {@code parameters}
     * are a method's parameter types. Both are a {@link Class} or a {@link NativeClass}.
     */
    enum NativeMember {
        /** {@code CommandSourceStack.permissionLevel}, the source's operator level (0 - 4). */
        SOURCE_PERMISSION_LEVEL(Kind.FIELD, NativeClass.SOURCE, int.class, params(), "permissionLevel",
                since("v1_13_R1", "f"),
                since("v1_21_R3", "g")),
        /** {@code CommandSourceStack.silent}: output suppressed. */
        SOURCE_SILENT(Kind.FIELD, NativeClass.SOURCE, boolean.class, params(), "silent",
                since("v1_13_R1", "j"),
                since("v1_21_R3", "k")),
        /** {@code CommandSourceStack.withPermission(int)}. */
        SOURCE_WITH_PERMISSION(Kind.METHOD, NativeClass.SOURCE, NativeClass.SOURCE, params(int.class), "withPermission",
                since("v1_13_R1", "a")),
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
        /** {@code EntityArgument.getEntities(CommandContext, String)}; fails when nothing matches. */
        ENTITY_ARGUMENT_GET_ENTITIES(Kind.STATIC_METHOD, NativeClass.ENTITY_ARGUMENT, Collection.class, params(CommandContext.class, String.class),
                "getEntities",
                since("v1_13_R1", "b")),
        /** {@code EntityArgument.getOptionalEntities(CommandContext, String)}. */
        ENTITY_ARGUMENT_GET_OPTIONAL_ENTITIES(Kind.STATIC_METHOD, NativeClass.ENTITY_ARGUMENT, Collection.class,
                params(CommandContext.class, String.class), "getOptionalEntities",
                since("v1_13_R1", "c")),
        /** {@code GameProfileArgument.getGameProfiles(CommandContext, String)}. */
        GAME_PROFILE_ARGUMENT_GET(Kind.STATIC_METHOD, NativeClass.GAME_PROFILE_ARGUMENT, Collection.class,
                params(CommandContext.class, String.class), "getGameProfiles",
                since("v1_13_R1", "a")),
        /** {@code ScoreHolderArgument.getNames(CommandContext, String)}. */
        SCORE_HOLDER_ARGUMENT_GET(Kind.STATIC_METHOD, NativeClass.SCORE_HOLDER_ARGUMENT, Collection.class,
                params(CommandContext.class, String.class), "getNames",
                since("v1_13_R1", "b")),
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
        ENTITY_PERMISSION_LEVEL(Kind.METHOD, NativeClass.PERMISSION_LEVEL_HOLDER, int.class, params(), "getPermissionLevel",
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

        final Kind kind;
        final NativeClass owner;
        final Object type;
        final List<Object> parameters;
        final String mojang;
        final List<Since> rows;

        NativeMember(Kind kind, NativeClass owner, Object type, Object[] parameters, String mojang, Since... rows) {
            this.kind = kind;
            this.owner = owner;
            this.type = type;
            this.parameters = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(parameters)));
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
