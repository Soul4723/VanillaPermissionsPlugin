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

    private AdapterLog() {}

    /** {@code Added permission checks to <count> vanilla command nodes}. */
    public static String added(int checkedNodes) {
        return ADDED_PREFIX + checkedNodes + ADDED_SUFFIX;
    }
}
