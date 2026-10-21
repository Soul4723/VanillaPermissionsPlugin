package io.github.soul4723.vanillapermissions.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Minecraft versions each server software has been booted and checked against. Support is decided by feature
 * detection at install time, not by these lists; they only decide whether to warn that a (software, version) pair is
 * untested. Each list mirrors that software's rows in {@code .github/workflows/build.yml}
 * ({@code SupportedVersionsTest} checks it).
 */
public final class SupportedVersions {

    private static final Map<ServerSoftware, List<String>> VERIFIED = new EnumMap<>(ServerSoftware.class);

    static {
        VERIFIED.put(ServerSoftware.PAPER, list(
            "1.8.8", "1.12.2", "1.13.2", "1.16.5", "1.17.1", "1.18.2", "1.19.4", "1.20.4",
            "1.20.5", "1.20.6",
            "1.21", "1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11",
            "26.1.1", "26.1.2", "26.2", "26.3"));
        VERIFIED.put(ServerSoftware.FOLIA, list("1.20.6", "1.21.11", "26.1.2", "26.3"));
        VERIFIED.put(ServerSoftware.SPIGOT, list(
            "1.8.8", "1.12.2", "1.13.2", "1.16.5", "1.17.1", "1.18.2", "1.19.4", "1.20.4"));
        VERIFIED.put(ServerSoftware.PURPUR, list("1.20.6", "1.21.11", "26.1.2", "26.2", "26.3"));
        VERIFIED.put(ServerSoftware.PUFFERFISH, list("1.21.1", "1.21.10"));
    }

    private SupportedVersions() {}

    private static List<String> list(String... versions) {
        return Collections.unmodifiableList(Arrays.asList(versions));
    }

    /** The versions tested on {@code software}, oldest first. */
    public static List<String> verified(ServerSoftware software) {
        return VERIFIED.get(software);
    }

    public static boolean isVerified(ServerSoftware software, String minecraftVersion) {
        return VERIFIED.get(software).contains(minecraftVersion);
    }

    /** The warning's tail and the debug line's list: {@code "Paper 1.8.8, 1.12.2, ..."}. */
    public static String describe(ServerSoftware software) {
        return software.displayName() + " " + String.join(", ", VERIFIED.get(software));
    }
}
