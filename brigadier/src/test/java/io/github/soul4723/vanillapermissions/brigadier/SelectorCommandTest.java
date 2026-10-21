package io.github.soul4723.vanillapermissions.brigadier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.soul4723.vanillapermissions.util.TestLuckPerms;
import net.luckperms.api.util.Tristate;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SelectorCommandTest {
    @Test
    void nonPlayersNeverResolveSelectorArguments() throws Exception {
        CommandSender console = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class}, (proxy, method, args) -> { throw new AssertionError(method); });
        BrigadierPermissions permissions = new BrigadierPermissions(null, new TestBridge() {
            @Override public CommandSender sender(Object source) { return console; }
            @Override public ArgumentResolver selectorResolver(ArgumentType<?> type) {
                throw new AssertionError("Non-player commands must not even look up a resolver");
            }
        }, null);
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        dispatcher.register(literal("test").then(argument("targets", StringArgumentType.greedyString()).executes(c -> 1)));
        for (String input : new String[]{"test *", "test @e[type=zombie]", "test Steve"}) {
            CommandContext<Object> context = dispatcher.parse(input, console).getContext().build(input);
            assertEquals(7, permissions.new SelectorCommand(c -> 7).run(context));
        }
    }

    /** {@code @r} is resolved again when the command runs, so every player it could pick is checked, not just one. */
    @Test
    void randomSelectorsAreCheckedAgainstEveryCandidate() throws Exception {
        Player self = player(UUID.randomUUID());
        Player other = player(UUID.randomUUID());
        BrigadierPermissions permissions = new BrigadierPermissions(null, new TestBridge() {
            @Override public CommandSender sender(Object source) { return self; }
            @Override public boolean selectorCheckBypassed(Object source) { return true; }
            @Override public SelectorOverride bypassSelectorCheck(CommandContext<?> context, String name) { return () -> {}; }
            @Override public ArgumentResolver selectorResolver(ArgumentType<?> type) {
                return type instanceof PickArgument ? (context, name) -> context.getArgument(name, Pick.class).resolve(self, other) : null;
            }
        }, null);
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        dispatcher.register(literal("kill").then(argument("targets", new PickArgument()).executes(c -> 1)));
        BrigadierPermissions.SelectorCommand command = permissions.new SelectorCommand(
                c -> c.getArgument("targets", Pick.class).text.length());

        try (TestLuckPerms luckPerms = new TestLuckPerms()) {
            assertEquals(2, command.run(context(dispatcher, "kill @r")), "Allowed before any rule is set");
            luckPerms.nodes.put("minecraft.selector.player.kill.targets.targets", Tristate.FALSE);
            // The first pick is the sender, which is allowed, but the next resolution may pick the other player.
            CommandSyntaxException error = assertThrows(CommandSyntaxException.class,
                    () -> command.run(context(dispatcher, "kill @r")));
            assertEquals("You do not have permission to target other players here", error.getMessage());
            assertThrows(CommandSyntaxException.class, () -> command.run(context(dispatcher, "kill @e[sort=random,limit=1]")));
            // A deterministic selector is checked against its own result only; the swapped argument was put back.
            assertEquals(2, command.run(context(dispatcher, "kill @s")));
        }
    }

    private static CommandContext<Object> context(CommandDispatcher<Object> dispatcher, String input) {
        return dispatcher.parse(input, new Object()).getContext().build(input);
    }

    private static Player player(UUID id) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> null;
                });
    }

    /** A selector token; {@code @r} "randomly" picks the sender, {@code @a} and pools pick everyone. */
    static final class Pick {
        final String text;

        Pick(String text) { this.text = text; }

        List<Player> resolve(Player self, Player other) {
            return text.startsWith("@a") || text.startsWith("@e") && !text.contains("limit") ? List.of(self, other) : List.of(self);
        }
    }

    static final class PickArgument implements ArgumentType<Pick> {
        @Override
        public Pick parse(StringReader reader) {
            int start = reader.getCursor();
            while (reader.canRead() && reader.peek() != ' ') reader.skip();
            return new Pick(reader.getString().substring(start, reader.getCursor()));
        }
    }
}
