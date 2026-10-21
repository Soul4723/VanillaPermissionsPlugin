package io.github.soul4723.vanillapermissions.brigadier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
