package io.github.soul4723.vanillapermissions.spigot;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.soul4723.vanillapermissions.spigot.SpigotNames.Kind;
import io.github.soul4723.vanillapermissions.spigot.SpigotNames.NativeClass;
import io.github.soul4723.vanillapermissions.spigot.SpigotNames.NativeMember;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Every {@link SpigotNames} entry resolved on the running server and checked before anything is patched: each class
 * must load, each member must exist with the declared static-ness, type and parameters, and the entity arguments and
 * selector flag must behave as expected. Any mismatch throws, so the adapter fails closed instead of calling the
 * wrong obfuscated member.
 *
 * <p>Shape checks follow ProtocolLib's {@code FuzzyReflection} ({@code src/main/java/com/comphenix/protocol/reflect/FuzzyReflection.java}: find a
 * member by its types); the "uses selector" flag is found as the one boolean that is true for {@code @s}, {@code @p}
 * and {@code @e} and false for a name.
 */
final class NativeAccess {

    /** Selectors and plain targets the "uses selector" flag must tell apart. */
    private static final List<String> SELECTORS = Arrays.asList("@s", "@p", "@e");
    private static final List<String> PLAIN_TARGETS = Arrays.asList("Notch", "069a79f4-44e9-4726-a5be-fca90e38aaf5");

    private final MappingGeneration generation;
    private final Map<NativeClass, Class<?>> classes = new EnumMap<>(NativeClass.class);
    private final Map<NativeMember, Member> members = new EnumMap<>(NativeMember.class);

    private NativeAccess(MappingGeneration generation) {
        this.generation = generation;
    }

    /** Resolves and validates every entry for {@code generation}; throws on the first mismatch. */
    static NativeAccess resolve(MappingGeneration generation, ClassLoader loader) throws ReflectiveOperationException {
        NativeAccess access = new NativeAccess(generation);
        for (NativeClass type : NativeClass.values()) {
            access.classes.put(type, Class.forName(type.name(generation), false, loader));
        }
        for (NativeMember member : NativeMember.values()) {
            String name = member.name(generation);
            if (name == null) continue;
            access.members.put(member, access.lookup(member, name));
        }
        access.checkShapes();
        return access;
    }

    Class<?> type(NativeClass type) {
        return classes.get(type);
    }

    Field field(NativeMember member) {
        return (Field) required(member);
    }

    Method method(NativeMember member) {
        return (Method) required(member);
    }

    /** Whether {@code member} exists on this generation (members marked {@link SpigotNames#ABSENT} do not). */
    boolean has(NativeMember member) {
        return members.containsKey(member);
    }

    private Member required(NativeMember member) {
        Member found = members.get(member);
        if (found == null) throw new IllegalStateException(member + " does not exist on " + generation);
        return found;
    }

    private Member lookup(NativeMember member, String name) throws ReflectiveOperationException {
        Class<?> owner = classes.get(member.owner);
        Class<?> type = resolveType(member.type);
        String where = owner.getName() + "." + name + " (" + member + ", " + generation + ")";
        if (member.kind == Kind.FIELD) {
            return instanceField(owner, name, type, where);
        }
        Class<?>[] parameters = new Class<?>[member.parameters.size()];
        for (int i = 0; i < parameters.length; i++) parameters[i] = resolveType(member.parameters.get(i));
        Method method = owner.getDeclaredMethod(name, parameters);
        boolean isStatic = Modifier.isStatic(method.getModifiers());
        if (isStatic != (member.kind == Kind.STATIC_METHOD) || method.getReturnType() != type) {
            throw new IllegalStateException(where + " is not a" + (member.kind == Kind.STATIC_METHOD ? " static" : "n instance")
                    + " method returning " + type.getName());
        }
        return accessible(method, where);
    }

    private Class<?> resolveType(Object type) {
        return type instanceof NativeClass ? classes.get(type) : (Class<?>) type;
    }

    private static <T extends AccessibleObject> T accessible(T member, String where) throws IllegalAccessException {
        try {
            member.setAccessible(true);
            return member;
        } catch (RuntimeException denied) {
            IllegalAccessException error = new IllegalAccessException(where + " is inaccessible");
            error.initCause(denied);
            throw error;
        }
    }

    static Field instanceField(Class<?> owner, String name, Class<?> type, String where) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        if (Modifier.isStatic(field.getModifiers()) || field.getType() != type) {
            throw new IllegalStateException(where + " is not an instance field of type " + type.getName());
        }
        return accessible(field, where);
    }

    /** Cross-checks the entity arguments and selector flag by behaviour. */
    private void checkShapes() throws ReflectiveOperationException {
        ArgumentType<?> entities = (ArgumentType<?>) method(NativeMember.ENTITY_ARGUMENT_ENTITIES).invoke(null);
        ArgumentType<?> entity = (ArgumentType<?>) method(NativeMember.ENTITY_ARGUMENT_ENTITY).invoke(null);
        checkEntityArguments(entity, entities, generation);
        Field flag = field(NativeMember.SELECTOR_USES_SELECTOR);
        Class<?> selector = classes.get(NativeClass.ENTITY_SELECTOR);
        if (!selectorFlag(selector, entities).equals(flag)) {
            throw new IllegalStateException("The selector flag " + flag.getName() + " of " + selector.getName()
                    + " does not match the boolean set only by selectors on " + generation);
        }
    }

    static void checkEntityArguments(ArgumentType<?> entity, ArgumentType<?> entities, MappingGeneration generation) {
        // entities() accepts @e; entity() accepts a single entity (@e[limit=1]) but refuses @e.
        if (!parses(entities, "@e") || !parses(entity, "@e[limit=1]") || parses(entity, "@e")) {
            throw new IllegalStateException(NativeMember.ENTITY_ARGUMENT_ENTITY + " and " + NativeMember.ENTITY_ARGUMENT_ENTITIES
                    + " do not behave as EntityArgument.entity() and entities() on " + generation);
        }
    }

    /** The "uses selector" flag is set for every selector and clear for names and UUIDs. */
    static Field selectorFlag(Class<?> selector, ArgumentType<?> entities) throws ReflectiveOperationException {
        List<Field> flags = new ArrayList<>();
        for (Field candidate : selector.getDeclaredFields()) {
            if (candidate.getType() != boolean.class || Modifier.isStatic(candidate.getModifiers())) continue;
            accessible(candidate, selector.getName() + "." + candidate.getName());
            boolean matches = true;
            for (String input : SELECTORS) matches &= candidate.getBoolean(parse(entities, input, selector));
            for (String input : PLAIN_TARGETS) matches &= !candidate.getBoolean(parse(entities, input, selector));
            if (matches) flags.add(candidate);
        }
        if (flags.size() != 1) {
            throw new IllegalStateException("The selector flag of " + selector.getName()
                    + " does not match the boolean set only by selectors " + names(flags));
        }
        return flags.get(0);
    }

    private static boolean parses(ArgumentType<?> type, String input) {
        try {
            type.parse(new StringReader(input));
            return true;
        } catch (CommandSyntaxException refused) {
            return false;
        }
    }

    private static Object parse(ArgumentType<?> type, String input, Class<?> expected) {
        Object value;
        try {
            value = type.parse(new StringReader(input));
        } catch (CommandSyntaxException error) {
            throw new IllegalStateException("The server's entity argument refused " + input, error);
        }
        if (!expected.isInstance(value)) {
            throw new IllegalStateException("Parsing " + input + " gave " + value.getClass().getName() + ", not " + expected.getName());
        }
        return value;
    }

    private static String names(List<Field> fields) {
        List<String> names = new ArrayList<>();
        for (Field field : fields) names.add(field.getName());
        return names.toString();
    }
}
