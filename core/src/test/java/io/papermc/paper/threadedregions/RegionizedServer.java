package io.papermc.paper.threadedregions;

/** Marker for the Folia class probe; initialisation is forbidden. */
public final class RegionizedServer {
    static {
        if (true) throw new AssertionError("The Folia probe must not initialise server classes");
    }
}
