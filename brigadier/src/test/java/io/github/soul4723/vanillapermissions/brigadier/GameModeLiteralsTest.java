package io.github.soul4723.vanillapermissions.brigadier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The name-free /gamemode literals the Spigot adapter will use, on a dispatcher shaped like vanilla 1.19.4+. */
class GameModeLiteralsTest {

    enum Mode { SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR }

    /** Stands in for vanilla's GameModeArgument: parses a mode name to an enum constant. */
    static final class ModeArgument implements ArgumentType<Mode> {
        private static final SimpleCommandExceptionType UNKNOWN = new SimpleCommandExceptionType(new LiteralMessage("unknown mode"));

        @Override
        public Mode parse(StringReader reader) throws CommandSyntaxException {
            String name = reader.readUnquotedString();
            for (Mode mode : Mode.values()) {
                if (mode.name().toLowerCase(Locale.ROOT).equals(name)) return mode;
            }
            throw UNKNOWN.createWithContext(reader);
        }
    }

    private final List<String> runs = new ArrayList<>();

    private CommandDispatcher<Object> vanilla() {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        LiteralCommandNode<Object> gamemode = dispatcher.register(literal("gamemode")
            .then(argument("gamemode", new ModeArgument())
                .executes(context -> run(context.getArgument("gamemode", Mode.class), "self"))
                .then(argument("target", word())
                    .executes(context -> run(context.getArgument("gamemode", Mode.class), StringArgumentType.getString(context, "target"))))));
        dispatcher.register(literal("minecraft:gamemode").redirect(gamemode));
        return dispatcher;
    }

    private int run(Mode mode, String target) {
        runs.add(mode + " " + target);
        return 1;
    }

    @Test
    void recognisesTheModeArgumentByWhatItParses() {
        assertTrue(GameModeLiterals.isGameModeArgument(new ModeArgument()));
        assertFalse(GameModeLiterals.isGameModeArgument(word()));
    }

    @Test
    void nameFreeLiteralsRunTheOriginalExecutors() throws Exception {
        CommandDispatcher<Object> dispatcher = vanilla();
        CommandNode<Object> gamemode = dispatcher.getRoot().getChild("gamemode");
        CommandNode<Object> original = gamemode.getChild("gamemode");
        GameModeLiterals literals = new GameModeLiterals(new TestBridge());
        literals.install(dispatcher);

        assertNull(gamemode.getChild("gamemode"), "GameModeArgument node was kept next to the literals");
        for (String mode : GameModeLiterals.MODES) {
            assertInstanceOf(LiteralCommandNode.class, gamemode.getChild(mode), "Missing literal " + mode);
            assertNotNull(gamemode.getChild(mode).getChild("target"), "Missing target under " + mode);
        }
        dispatcher.execute("gamemode creative", new Object());
        dispatcher.execute("gamemode adventure Bob", new Object());
        dispatcher.execute("minecraft:gamemode spectator Alice", new Object());
        assertEquals(List.of("CREATIVE self", "ADVENTURE Bob", "SPECTATOR Alice"), runs);

        literals.uninstall();
        assertSame(original, gamemode.getChild("gamemode"), "Uninstall did not restore the GameModeArgument node");
        for (String mode : GameModeLiterals.MODES) assertNull(gamemode.getChild(mode), "Literal left behind: " + mode);
    }

    @Test
    void literalTreesBefore1194AreLeftAlone() throws Exception {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        for (String mode : GameModeLiterals.MODES) dispatcher.register(literal("gamemode").then(literal(mode).executes(c -> 1)));
        new GameModeLiterals(new TestBridge()).install(dispatcher);
        assertEquals(4, dispatcher.getRoot().getChild("gamemode").getChildren().size());

        CommandDispatcher<Object> unknown = new CommandDispatcher<>();
        unknown.register(literal("gamemode").then(literal("creative")));
        assertThrows(IllegalStateException.class, () -> new GameModeLiterals(new TestBridge()).install(unknown));
    }
}
