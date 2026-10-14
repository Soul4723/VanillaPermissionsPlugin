package io.github.soul4723.vanillapermissions.agent;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

/** Calls bridge classes loaded by the bootstrap loader, shared with transformed server classes. */
public final class NativeAgentBridge {
    private static final String PREFIX = "io.github.soul4723.vanillapermissions.agent.";

    private NativeAgentBridge() {}

    public static boolean isActive() {
        try {
            return (boolean) bridge("PermissionBridge").getMethod("isAgentActive").invoke(null);
        } catch (ClassNotFoundException absent) {
            return false;
        } catch (ReflectiveOperationException error) {
            throw bridgeFailure(error);
        }
    }

    public static void registerPermissionCheck(PermissionCheck check) {
        register("PermissionBridge", "PermissionCheck", "register", (proxy, method, args) ->
            check.check(args[0], (String) args[1], (boolean) args[2]));
    }

    public static void clearPermissionCheck() {
        invoke("PermissionBridge", "clearPermissionCheck");
    }

    public static void registerSelectorCheck(SelectorCheck check) {
        register("SelectorBridge", "SelectorCheck", "register", (proxy, method, args) -> {
            check.validate(args[0], (String) args[1], args[2]);
            return null;
        });
    }

    public static void clearSelectorCheck() {
        invoke("SelectorBridge", "clearSelectorCheck");
    }

    private static void register(String bridgeName, String callbackName, String methodName,
                                 InvocationHandler callback) {
        try {
            Class<?> callbackType = Class.forName(PREFIX + bridgeName + "$" + callbackName, true, null);
            Object proxy = Proxy.newProxyInstance(null, new Class<?>[]{callbackType}, callback);
            bridge(bridgeName).getMethod(methodName, callbackType).invoke(null, proxy);
        } catch (ReflectiveOperationException error) {
            throw bridgeFailure(error);
        }
    }

    private static void invoke(String bridgeName, String methodName) {
        try {
            bridge(bridgeName).getMethod(methodName).invoke(null);
        } catch (ClassNotFoundException absent) {
            return;
        } catch (ReflectiveOperationException error) {
            throw bridgeFailure(error);
        }
    }

    private static Class<?> bridge(String name) throws ClassNotFoundException {
        return Class.forName(PREFIX + name, true, null);
    }

    private static IllegalStateException bridgeFailure(ReflectiveOperationException error) {
        return new IllegalStateException("Native permission agent bridge is unavailable", error);
    }

    @FunctionalInterface
    public interface PermissionCheck {
        boolean check(Object subject, String permission, boolean original) throws Throwable;
    }

    @FunctionalInterface
    public interface SelectorCheck {
        void validate(Object context, String argumentName, Object result) throws Throwable;
    }
}
