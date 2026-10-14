import io.github.soul4723.vanillapermissions.agent.NativeAgentBridge;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

public final class NativeAgentBridgeCheck {
    private NativeAgentBridgeCheck() {}

    public static void main(String[] args) throws Exception {
        require(NativeAgentBridge.isActive(), "agent was not marked active");

        NativeAgentBridge.registerPermissionCheck((subject, permission, original) -> !original);
        Class<?> permissionBridge = Class.forName(
            "io.github.soul4723.vanillapermissions.agent.PermissionBridge", true, null);
        Method check = permissionBridge.getMethod("check", Object.class, String.class, boolean.class);
        require((boolean) check.invoke(null, null, "test.permission", false), "permission callback not shared");
        require(!(boolean) check.invoke(null, null, "test.permission", true), "permission callback lost deny");
        NativeAgentBridge.clearPermissionCheck();

        AtomicReference<Object> received = new AtomicReference<>();
        NativeAgentBridge.registerSelectorCheck((context, argumentName, result) -> received.set(result));
        Class<?> selectorBridge = Class.forName(
            "io.github.soul4723.vanillapermissions.agent.SelectorBridge", true, null);
        Object expected = new Object();
        selectorBridge.getMethod("validate", Object.class, String.class, Object.class)
            .invoke(null, null, "targets", expected);
        require(received.get() == expected, "selector callback not shared");
        NativeAgentBridge.clearSelectorCheck();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
