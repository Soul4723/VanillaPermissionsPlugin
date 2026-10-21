package io.github.soul4723.vanillapermissions.brigadier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;

/** A bridge for unit tests: tests override what they use; everything else fails loudly. */
class TestBridge implements BrigadierBridge {

    @Override
    public void prepare(CommandDispatcher<?> dispatcher) {}

    @Override
    public List<VanillaTree.VanillaRoot> vanillaRoots(CommandDispatcher<?> dispatcher) {
        return VanillaTree.fromMinecraftAliases(dispatcher.getRoot());
    }

    @Override
    public CommandSender sender(Object source) throws ReflectiveOperationException {
        throw new UnsupportedOperationException("sender");
    }

    @Override
    public boolean allowsSelectors(Object source) {
        throw new UnsupportedOperationException("allowsSelectors");
    }

    @Override
    public int operatorLevel(Object sourceOrEntity) {
        throw new UnsupportedOperationException("operatorLevel");
    }

    @Override
    public Object withOperatorLevelAtMost(Object source, int level) {
        throw new UnsupportedOperationException("withOperatorLevelAtMost");
    }

    @Override
    public ArgumentResolver selectorResolver(ArgumentType<?> type) {
        return null;
    }

    @Override
    public boolean selectorCheckBypassed(Object source) {
        throw new UnsupportedOperationException("selectorCheckBypassed");
    }

    @Override
    public SelectorOverride bypassSelectorCheck(CommandContext<?> context, String argumentName) {
        throw new UnsupportedOperationException("bypassSelectorCheck");
    }

    @Override
    public ArgumentType<?> entityArgument() {
        throw new UnsupportedOperationException("entityArgument");
    }

    @Override
    public ArgumentType<?> entitiesArgument() {
        throw new UnsupportedOperationException("entitiesArgument");
    }

    @Override
    public ArgumentResolver entityResolver() {
        throw new UnsupportedOperationException("entityResolver");
    }

    @Override
    public ArgumentResolver optionalEntitiesResolver() {
        throw new UnsupportedOperationException("optionalEntitiesResolver");
    }

    @Override
    public Object withSuppressedOutput(Object source) {
        throw new UnsupportedOperationException("withSuppressedOutput");
    }

    @Override
    public boolean redirectsFeedback() {
        return false;
    }

    @Override
    public Object feedbackToServer(Object source) {
        throw new UnsupportedOperationException("feedbackToServer");
    }

    @Override
    public List<Object> feedbackToEntities(Object source, Collection<?> entities) {
        throw new UnsupportedOperationException("feedbackToEntities");
    }
}
