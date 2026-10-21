package io.github.soul4723.vanillapermissions.paper;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NativeGameModesTest {
    private static final CommandSyntaxException TARGET_SYNTAX_ERROR =
        CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
            .createWithContext(new StringReader("missing"));

    @Test
    void targetSyntaxErrorsReachThePlayerUnchanged() throws Exception {
        Method resolver = NativeGameModesTest.class.getMethod("missingTargets", CommandContext.class, String.class);
        CommandSyntaxException error = assertThrows(CommandSyntaxException.class,
            () -> NativeGameModes.invokePlayers(resolver, null, "target"));
        assertSame(TARGET_SYNTAX_ERROR, error, "Native target syntax error was replaced");
    }

    public static Collection<Object> missingTargets(CommandContext<?> context, String name) throws CommandSyntaxException {
        throw TARGET_SYNTAX_ERROR;
    }
}
