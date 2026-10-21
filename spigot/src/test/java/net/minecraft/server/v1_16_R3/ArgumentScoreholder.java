package net.minecraft.server.v1_16_R3;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.Collection;
import java.util.Collections;

public class ArgumentScoreholder implements ArgumentType<ArgumentScoreholder.Result> {

    /** The selector sits in a superclass field, which the bridge's lookup walks up to. */
    public static class Base {
        private final EntitySelector selector;

        Base(EntitySelector selector) {
            this.selector = selector;
        }

        public EntitySelector selector() {
            return selector;
        }
    }

    public static class Result extends Base {
        Result(EntitySelector selector) {
            super(selector);
        }
    }

    public static Collection<String> b(CommandContext<?> context, String name) {
        return Collections.emptyList();
    }

    public static Collection<String> c(CommandContext<?> context, String name) {
        return context.getInput().contains("*") ? java.util.Arrays.asList("Steve", "Alex", "FakeHolder") : b(context, name);
    }

    @Override
    public Result parse(StringReader reader) throws CommandSyntaxException {
        return new Result(ArgumentEntity.multipleEntities().readSelector(reader));
    }
}
