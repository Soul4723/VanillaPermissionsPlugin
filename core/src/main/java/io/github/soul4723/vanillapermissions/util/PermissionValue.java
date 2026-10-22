package io.github.soul4723.vanillapermissions.util;

/**
 * A node's value, like fabric-permissions-api's {@code TriState} and LuckPerms' {@code Tristate}; {@link #UNDEFINED}
 * means unset, so the caller's vanilla fallback applies. Platform-free, so other platform modules can share it.
 */
public enum PermissionValue {
    TRUE, FALSE, UNDEFINED;

    public boolean orElse(boolean fallback) {
        return this == UNDEFINED ? fallback : this == TRUE;
    }
}
