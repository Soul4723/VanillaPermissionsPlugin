package net.minecraft.server.v1_16_R3;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import java.util.Collection;
import java.util.Collections;

public class ArgumentEntity implements ArgumentType<EntitySelector> {

    private static final SimpleCommandExceptionType TOO_MANY = new SimpleCommandExceptionType(new LiteralMessage("Only one entity is allowed"));

    private final boolean single;
    private final boolean playersOnly;

    public ArgumentEntity(boolean single, boolean playersOnly) {
        this.single = single;
        this.playersOnly = playersOnly;
    }

    public static ArgumentEntity a() {
        return new ArgumentEntity(true, false);
    }

    public static ArgumentEntity multipleEntities() {
        return new ArgumentEntity(false, false);
    }

    public static Entity a(CommandContext<?> context, String name) {
        return null;
    }

    public static Collection<Entity> b(CommandContext<?> context, String name) {
        throw new IllegalStateException("No entities matched");
    }

    public static Collection<Entity> c(CommandContext<?> context, String name) {
        return Collections.emptyList();
    }

    @Override
    public EntitySelector parse(StringReader reader) throws CommandSyntaxException {
        return readSelector(reader);
    }

    /** Reads one whitespace-delimited token; a single-entity argument refuses @e without limit=1. */
    EntitySelector readSelector(StringReader reader) throws CommandSyntaxException {
        StringBuilder text = new StringBuilder();
        while (reader.canRead() && reader.peek() != ' ') text.append(reader.read());
        String input = text.toString();
        if (single && input.startsWith("@e") && !input.contains("limit=1")) throw TOO_MANY.createWithContext(reader);
        return new EntitySelector(input);
    }
}
