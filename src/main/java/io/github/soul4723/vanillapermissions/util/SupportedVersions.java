package io.github.soul4723.vanillapermissions.util;

import java.util.List;

/**
 * Minecraft versions whose Paper builds this plugin has been booted and checked against. Support is decided by
 * feature detection at install time, not by this list; it only decides whether to warn that a version is untested.
 * Keep it in step with the matrix in {@code .github/workflows/compat.yml}.
 */
public final class SupportedVersions {

    public static final List<String> VERIFIED = List.of(
        "1.20.6",
        "1.21", "1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11",
        "26.1.1", "26.1.2", "26.2", "26.3");

    private SupportedVersions() {}

    public static boolean isVerified(String minecraftVersion) {
        return VERIFIED.contains(minecraftVersion);
    }

    /** For logs, e.g. "1.20.6 to 26.3". */
    public static String range() {
        return VERIFIED.get(0) + " to " + VERIFIED.get(VERIFIED.size() - 1);
    }
}
