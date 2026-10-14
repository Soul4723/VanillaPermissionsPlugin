package io.github.soul4723.vanillapermissions.agent;

import java.util.Objects;

/** Shared entry point between the startup agent and the Bukkit plugin classloader. */
public final class PermissionBridge {
    private static volatile PermissionCheck permissionCheck;
    private static volatile boolean agentActive;

    private PermissionBridge() {}

    public static boolean check(Object subject, String permission, boolean original) {
        PermissionCheck check = permissionCheck;
        if (check == null) return original;
        try {
            return check.check(subject, permission, original);
        } catch (Throwable error) {
            return original;
        }
    }

    public static void register(PermissionCheck check) {
        permissionCheck = Objects.requireNonNull(check, "check");
    }

    public static void clearPermissionCheck() {
        permissionCheck = null;
    }

    public static boolean isAgentActive() {
        return agentActive;
    }

    static void setAgentActive() {
        agentActive = true;
    }

    @FunctionalInterface
    public interface PermissionCheck {
        boolean check(Object subject, String permission, boolean original) throws Throwable;
    }
}
