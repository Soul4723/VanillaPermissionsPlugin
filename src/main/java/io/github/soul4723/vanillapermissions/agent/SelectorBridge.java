package io.github.soul4723.vanillapermissions.agent;

import java.util.Objects;

/** Selector validation callback shared with the startup agent. */
public final class SelectorBridge {
    private static volatile SelectorCheck selectorCheck;

    private SelectorBridge() {}

    public static void validate(Object context, String argumentName, Object result) throws Throwable {
        SelectorCheck check = selectorCheck;
        if (check != null) check.validate(context, argumentName, result);
    }

    public static void register(SelectorCheck check) {
        selectorCheck = Objects.requireNonNull(check, "check");
    }

    public static void clearSelectorCheck() {
        selectorCheck = null;
    }

    @FunctionalInterface
    public interface SelectorCheck {
        void validate(Object context, String argumentName, Object result) throws Throwable;
    }
}
