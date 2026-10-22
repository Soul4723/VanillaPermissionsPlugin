package io.github.soul4723.vanillapermissions.legacy.grammar;

/**
 * A pre-1.13 Minecraft version, for the small per-version switches inside a grammar (for example {@code time query
 * day} exists from 1.12, {@code /teleport} from 1.10).
 */
public final class LegacyVersion {

    public static final LegacyVersion V1_8_8 = new LegacyVersion(8, 8);
    public static final LegacyVersion V1_9_4 = new LegacyVersion(9, 4);
    public static final LegacyVersion V1_10_2 = new LegacyVersion(10, 2);
    public static final LegacyVersion V1_11_2 = new LegacyVersion(11, 2);
    public static final LegacyVersion V1_12_2 = new LegacyVersion(12, 2);

    private final int minor;
    private final int patch;

    private LegacyVersion(int minor, int patch) {
        this.minor = minor;
        this.patch = patch;
    }

    /**
     * Parses {@code 1.12.2}, {@code 1.8}, or a Bukkit version such as {@code 1.8.8-R0.1-SNAPSHOT}. Anything it cannot
     * read is treated as 1.12.2, the newest version this adapter covers.
     */
    public static LegacyVersion parse(String version) {
        if (version == null) return V1_12_2;
        int dash = version.indexOf('-');
        String[] parts = (dash < 0 ? version : version.substring(0, dash)).split("\\.");
        try {
            if (parts.length < 2 || !parts[0].equals("1")) return V1_12_2;
            return new LegacyVersion(Integer.parseInt(parts[1]), parts.length > 2 ? Integer.parseInt(parts[2]) : 0);
        } catch (NumberFormatException e) {
            return V1_12_2;
        }
    }

    /** Whether this is Minecraft {@code 1.<minor>} or newer. */
    public boolean atLeast(int minor) {
        return this.minor >= minor;
    }

    @Override
    public String toString() {
        return "1." + minor + "." + patch;
    }
}
