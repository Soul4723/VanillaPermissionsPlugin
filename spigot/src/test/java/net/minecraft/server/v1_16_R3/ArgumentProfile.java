package net.minecraft.server.v1_16_R3;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.Collection;
import java.util.Collections;

public class ArgumentProfile implements ArgumentType<ArgumentProfile.Result> {

    /** Like vanilla's profile result: wraps the parsed selector in a field. */
    public static class Result {
        private final EntitySelector selector;

        Result(EntitySelector selector) {
            this.selector = selector;
        }

        public EntitySelector selector() {
            return selector;
        }
    }

    public static Collection<Object> a(CommandContext<?> context, String name) {
        return Collections.emptyList();
    }

    @Override
    public Result parse(StringReader reader) throws CommandSyntaxException {
        return new Result(ArgumentEntity.multipleEntities().readSelector(reader));
    }
}
