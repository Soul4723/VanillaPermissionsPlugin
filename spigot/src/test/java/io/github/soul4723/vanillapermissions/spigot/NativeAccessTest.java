package io.github.soul4723.vanillapermissions.spigot;

import com.mojang.brigadier.arguments.ArgumentType;
import io.github.soul4723.vanillapermissions.spigot.SpigotNames.NativeClass;
import io.github.soul4723.vanillapermissions.spigot.SpigotNames.NativeMember;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs {@link NativeAccess} against fake server classes in {@code net.minecraft.server.v1_16_R3} (the shapes of Spigot
 * 1.16.5), with small local fixtures for the negative behavioural checks.
 */
class NativeAccessTest {

    private static final ClassLoader LOADER = NativeAccessTest.class.getClassLoader();

    private static NativeAccess resolve(String token) throws ReflectiveOperationException {
        return NativeAccess.resolve(MappingGeneration.of(token), LOADER);
    }

    @Test
    void resolvesEveryEntryOnTheFakeServer() throws Exception {
        NativeAccess access = resolve("v1_16_R3");

        assertEquals("net.minecraft.server.v1_16_R3.CommandListenerWrapper", access.type(NativeClass.SOURCE).getName());
        Field level = access.field(NativeMember.SOURCE_PERMISSION_LEVEL);
        assertEquals("f", level.getName());
        assertEquals("j", access.field(NativeMember.SOURCE_SILENT).getName());
        assertEquals("checkPermissions", access.field(NativeMember.SELECTOR_USES_SELECTOR).getName());
        Method entities = access.method(NativeMember.ENTITY_ARGUMENT_ENTITIES);
        assertEquals("multipleEntities", entities.getName());
        assertEquals("y", access.method(NativeMember.ENTITY_PERMISSION_LEVEL).getName());
        assertEquals("c", access.method(NativeMember.ENTITY_ARGUMENT_GET_OPTIONAL_ENTITIES).getName());
        assertEquals("c", access.method(NativeMember.SCORE_HOLDER_ARGUMENT_GET_WITH_WILDCARD).getName());
        for (NativeMember member : NativeMember.values()) {
            if (member == NativeMember.SOURCE_WITH_SOURCE) continue;
            assertEquals(member.levels != SpigotNames.Levels.PERMISSION_SET, access.has(member), member + " resolves only with int levels");
        }
    }

    @Test
    void withSourceIsAbsentBefore117() throws Exception {
        NativeAccess access = resolve("v1_16_R3");

        assertFalse(access.has(NativeMember.SOURCE_WITH_SOURCE));
        assertThrows(IllegalStateException.class, () -> access.method(NativeMember.SOURCE_WITH_SOURCE));
    }

    @Test
    void anUnknownPackageFailsToResolve() {
        assertThrows(ClassNotFoundException.class, () -> resolve("v1_16_R2"));
    }

    @Test
    void failsWhenTheOperatorLevelFieldHasTheWrongType() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> NativeAccess.field(LongLevel.class, "f", int.class, false, "LongLevel.f"));
        assertTrue(error.getMessage().contains("is not an instance field of type int"), error.getMessage());
    }

    @Test
    void failsWhenAnotherBooleanLooksLikeTheSelectorFlag() {
        // a second boolean is true for exactly the selectors too, so the flag is ambiguous
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> NativeAccess.selectorFlag(TwoFlags.class, reader -> new TwoFlags(reader.getString())));
        assertTrue(error.getMessage().contains("checkPermissions"), error.getMessage());
    }

    @Test
    void failsWhenTheSelectorFlagIsAlsoSetForNames() {
        // checkPermissions is true for Notch too, so no boolean tells selectors from names
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> NativeAccess.selectorFlag(AlwaysFlag.class, reader -> new AlwaysFlag()));
        assertTrue(error.getMessage().contains("does not match the boolean set only by selectors"), error.getMessage());
    }

    @Test
    void failsWhenTheSingleEntityArgumentAcceptsEverySelector() {
        // entity() parses @e, so it is not the single-entity argument
        ArgumentType<String> acceptsAll = reader -> reader.getString();
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> NativeAccess.checkEntityArguments(acceptsAll, acceptsAll, MappingGeneration.of("v1_14_R3")));
        assertTrue(error.getMessage().contains("do not behave as EntityArgument.entity() and entities()"), error.getMessage());
    }
    static class LongLevel {
        long f;
    }

    static class TwoFlags {
        boolean checkPermissions;
        boolean lookAlike;

        TwoFlags(String input) {
            checkPermissions = lookAlike = input.startsWith("@");
        }
    }

    static class AlwaysFlag {
        boolean checkPermissions = true;
    }
}
