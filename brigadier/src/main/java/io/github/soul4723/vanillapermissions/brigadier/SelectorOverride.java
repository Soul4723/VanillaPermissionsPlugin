package io.github.soul4723.vanillapermissions.brigadier;

/** Undoes {@link BrigadierBridge#bypassSelectorCheck}; called in a {@code finally} block on the same thread. */
@FunctionalInterface
public interface SelectorOverride {

    void restore() throws ReflectiveOperationException;
}
