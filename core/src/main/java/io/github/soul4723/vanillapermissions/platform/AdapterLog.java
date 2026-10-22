package io.github.soul4723.vanillapermissions.platform;

/**
 * Log text every version adapter writes, so operators and scripts/compat-smoke.sh see the same lines on every
 * server. CompatSmokeLogPatternsTest keeps the script's patterns in step with these constants.
 */
public final class AdapterLog {

    /** Followed by the number of checked nodes and {@link #ADDED_SUFFIX} once the checks are installed. */
    public static final String ADDED_PREFIX = "Added permission checks to ";
    public static final String ADDED_SUFFIX = " vanilla command nodes";
    /** Starts the SEVERE line logged when installing the checks fails and the plugin disables itself. */
    public static final String INSTALL_FAILED = "Could not add permission checks";

    /** Commands the 1.8.8 - 1.12.2 checks know sub-commands of; LegacyAdapterTest keeps it equal to the grammars. */
    public static final int LEGACY_COMMANDS = 10;
    public static final String ISSUES = "https://github.com/Soul4723/VanillaPermissionsPlugin/issues";
    public static final String COMPATIBILITY = "https://github.com/Soul4723/VanillaPermissionsPlugin/blob/main/docs/compatibility.md";

    private AdapterLog() {}

    /** The name admins see for how the checks are added: {@code Paper mode}, {@code Spigot mode} or {@code legacy mode}. */
    public static String mode(String adapterId) {
        switch (adapterId) {
            case "paper": return "Paper mode";
            case "spigot": return "Spigot mode";
            case "legacy": return "legacy mode";
            default: return adapterId + " mode";
        }
    }

    /** What that mode checks, for the startup line and {@code /vanillapermissions debug}. */
    public static String scope(String adapterId) {
        if (adapterId.equals("legacy")) {
            return "sub-command checks for " + LEGACY_COMMANDS + " commands only; other vanilla commands keep their root node";
        }
        return "checks on every vanilla command and sub-command";
    }

    /** {@code Using Spigot mode on Paper 1.16.5 (checks on every vanilla command and sub-command)}. */
    public static String using(String adapterId, String software, String minecraftVersion) {
        return "Using " + mode(adapterId) + " on " + software + " " + minecraftVersion + " (" + scope(adapterId) + ")";
    }

    /** The SEVERE line every mode logs when it cannot add the checks and disables the plugin. */
    public static String installFailed(String software, String minecraftVersion, String tested) {
        return INSTALL_FAILED + " to vanilla commands on " + software + " " + minecraftVersion + " (tested: " + tested
            + "), so VanillaPermissionsPlugin is disabling itself and vanilla commands work as they would without it. "
            + "Supported servers: " + COMPATIBILITY + " - report problems at " + ISSUES;
    }

    /** {@code Added permission checks to <count> vanilla command nodes}. */
    public static String added(int checkedNodes) {
        return ADDED_PREFIX + checkedNodes + ADDED_SUFFIX;
    }
}
