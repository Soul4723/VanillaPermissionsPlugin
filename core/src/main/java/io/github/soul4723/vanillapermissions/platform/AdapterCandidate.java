package io.github.soul4723.vanillapermissions.platform;

import java.util.function.BooleanSupplier;

/**
 * A {@link VersionAdapter} that may suit the running server, described without loading its class, because an
 * adapter compiled for a newer Java or against API this server lacks would fail to load.
 */
public final class AdapterCandidate {

    private final String id;
    private final int minimumJava;
    private final String className;
    private final BooleanSupplier probe;

    /**
     * @param id short name for logs, e.g. {@code paper}
     * @param minimumJava lowest Java feature version the adapter's classes run on, e.g. {@code 21}
     * @param className fully qualified name of the {@link VersionAdapter} implementation
     * @param probe whether the running server has the API the adapter needs, using only class and field lookups
     */
    public AdapterCandidate(String id, int minimumJava, String className, BooleanSupplier probe) {
        this.id = id;
        this.minimumJava = minimumJava;
        this.className = className;
        this.probe = probe;
    }

    public String id() {
        return id;
    }

    public int minimumJava() {
        return minimumJava;
    }

    public String className() {
        return className;
    }

    public BooleanSupplier probe() {
        return probe;
    }
}
