package io.github.soul4723.vanillapermissions.brigadier;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.soul4723.vanillapermissions.selector.SelectorDenial;
import io.github.soul4723.vanillapermissions.selector.SelectorRules;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Brigadier face of the core {@link SelectorRules}: turns a {@link SelectorDenial} into a command syntax error. */
public final class SelectorPolicy {

    private static final DynamicCommandExceptionType TOO_MANY = new DynamicCommandExceptionType(
            limit -> new LiteralMessage(SelectorDenial.tooMany((Integer) limit).message()));
    private static final Map<SelectorDenial.Reason, SimpleCommandExceptionType> DENIED =
            new EnumMap<>(SelectorDenial.Reason.class);

    static {
        for (SelectorDenial.Reason reason : SelectorDenial.Reason.values()) {
            if (reason == SelectorDenial.Reason.TOO_MANY) continue;
            DENIED.put(reason, new SimpleCommandExceptionType(new LiteralMessage(SelectorDenial.of(reason).message())));
        }
    }

    private SelectorPolicy() {}

    public static void validate(
            CommandSender sender,
            List<String> parsedNodePath,
            String argumentName,
            Collection<SelectorRules.Target> selected,
            SelectorRules.Permissions permissions) throws CommandSyntaxException {
        Optional<SelectorDenial> denial = SelectorRules.check(sender, parsedNodePath, argumentName, selected, permissions);
        if (denial.isPresent()) throw exception(denial.get());
    }

    /** {@link #validate} with every target a random selector could pick; see {@link SelectorRules#check(CommandSender, List, String, Collection, Collection, SelectorRules.Permissions)}. */
    public static void validate(
            CommandSender sender,
            List<String> parsedNodePath,
            String argumentName,
            Collection<SelectorRules.Target> selected,
            Collection<SelectorRules.Target> candidates,
            SelectorRules.Permissions permissions) throws CommandSyntaxException {
        Optional<SelectorDenial> denial = SelectorRules.check(sender, parsedNodePath, argumentName, selected, candidates, permissions);
        if (denial.isPresent()) throw exception(denial.get());
    }

    /** The Brigadier error for a core denial; its message is {@link SelectorDenial#message()}. */
    public static CommandSyntaxException exception(SelectorDenial denial) {
        if (denial.reason() == SelectorDenial.Reason.TOO_MANY) return TOO_MANY.create(denial.limit());
        return DENIED.get(denial.reason()).create();
    }
}
