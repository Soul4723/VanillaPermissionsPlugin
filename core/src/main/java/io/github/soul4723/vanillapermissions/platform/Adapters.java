package io.github.soul4723.vanillapermissions.platform;

import org.bukkit.Bukkit;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Picks the {@link VersionAdapter} for the running server: the first candidate whose Java floor the JVM meets and
 * whose probe matches. Candidates go from the newest API to the oldest.
 */
public final class Adapters {

    static final String PAPER_ADAPTER = "io.github.soul4723.vanillapermissions.paper.PaperAdapter";
    static final String LEGACY_ADAPTER = "io.github.soul4723.vanillapermissions.legacy.LegacyAdapter";
    static final String SPIGOT_ADAPTER = "io.github.soul4723.vanillapermissions.spigot.SpigotAdapter";

    private Adapters() {}

    /** Every adapter this build knows, in the order they are tried. */
    public static List<AdapterCandidate> candidates() {
        Object server = Bukkit.getServer();
        return candidates(server != null && isFolia(server.getClass().getClassLoader()));
    }

    static List<AdapterCandidate> candidates(boolean folia) {
        return Collections.unmodifiableList(Arrays.asList(
            // Paper 1.20.6+: Brigadier through the lifecycle COMMANDS event, which Spigot does not have.
            new AdapterCandidate("paper", 21, PAPER_ADAPTER,
                () -> hasStaticField("io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents", "COMMANDS", serverLoader())),
            // Spigot 1.13+ and Paper 1.13 - 1.20.5: Brigadier (LuckPerms' isBrigadierSupported probe) on a CraftBukkit server.
            new AdapterCandidate("spigot", 8, SPIGOT_ADAPTER,
                () -> !folia && isBrigadierCraftBukkit(Bukkit.getServer())),
            // Spigot and Paper 1.8.8 - 1.12.2: no Brigadier, vanilla commands behind CraftBukkit's VanillaCommandWrapper.
            new AdapterCandidate("legacy", 8, LEGACY_ADAPTER, () -> !folia && isLegacyCraftBukkit())
        ));
    }

    /** The adapter for this server, or {@code null} if no candidate fits. */
    public static VersionAdapter select(ClassLoader loader, Logger logger) {
        return select(candidates(), javaVersion(), loader, logger);
    }

    static VersionAdapter select(List<AdapterCandidate> candidates, int javaVersion, ClassLoader loader, Logger logger) {
        for (AdapterCandidate candidate : candidates) {
            if (candidate.minimumJava() > javaVersion) continue;
            boolean matches;
            try {
                matches = candidate.probe().matches();
            } catch (LinkageError | RuntimeException error) {
                logger.log(Level.WARNING, "Could not load the " + candidate.id() + " adapter", error);
                continue;
            }
            if (!matches) continue;
            try {
                return Class.forName(candidate.className(), true, loader).asSubclass(VersionAdapter.class)
                    .getDeclaredConstructor().newInstance();
            } catch (LinkageError | ReflectiveOperationException | RuntimeException error) {
                logger.log(Level.WARNING, "Could not load the " + candidate.id() + " adapter", error);
                return null;
            }
        }
        return null;
    }

    /** The running Java feature version: 8 for "1.8", 21 for "21". */
    static int javaVersion() {
        return parseJavaVersion(System.getProperty("java.specification.version"));
    }

    static int parseJavaVersion(String specification) {
        if (specification == null) return 0;
        String feature = specification.startsWith("1.") ? specification.substring(2) : specification;
        int dot = feature.indexOf('.');
        try {
            return Integer.parseInt(dot < 0 ? feature : feature.substring(0, dot));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Whether this is CraftBukkit before 1.13: no Brigadier, and {@code org.bukkit.craftbukkit.<v>.command.VanillaCommandWrapper}
     * exists. Both are looked up in the server's class loader, so a plugin that bundles Brigadier does not count.
     */
    static boolean isLegacyCraftBukkit() {
        try {
            Object server = Bukkit.getServer();
            if (server == null) return false;
            ClassLoader loader = server.getClass().getClassLoader();
            return !classExists("com.mojang.brigadier.CommandDispatcher", loader)
                && classExists(server.getClass().getPackage().getName() + ".command.VanillaCommandWrapper", loader);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** Folia's server marker, as used by LuckPerms and ViaVersion; never initialise it. */
    public static boolean isFolia(ClassLoader loader) {
        return classExists("io.papermc.paper.threadedregions.RegionizedServer", loader);
    }

    /** Whether {@code className} can be found through {@code loader}, without initialising it. */
    public static boolean classExists(String className, ClassLoader loader) {
        try {
            Class.forName(className, false, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /**
     * A CraftBukkit server whose own class loader has Brigadier. The server's loader, not this plugin's, because a
     * plugin's loader also sees classes other plugins bundle, and a 1.8 - 1.12 plugin may ship Brigadier unrelocated.
     */
    static boolean isBrigadierCraftBukkit(Object server) {
        return isCraftBukkit(server) && classExists("com.mojang.brigadier.CommandDispatcher", server.getClass().getClassLoader());
    }

    /** Whether {@code server} is CraftBukkit's server class (Spigot, Paper and their forks), relocated or not. */
    public static boolean isCraftBukkit(Object server) {
        return server != null && server.getClass().getName().startsWith("org.bukkit.craftbukkit.");
    }

    /**
     * The server's own class loader, or Bukkit's when there is no server yet (tests). Probes use it rather than this
     * plugin's loader, which also sees classes other plugins bundle: one shipping Paper's API unrelocated would
     * otherwise pass for Paper on Spigot.
     */
    static ClassLoader serverLoader() {
        Object server = Bukkit.getServer();
        return server != null ? server.getClass().getClassLoader() : Bukkit.class.getClassLoader();
    }

    /** Whether {@code className} exists in {@code loader} and has a public static field {@code field}; loads nothing else. */
    static boolean hasStaticField(String className, String field, ClassLoader loader) {
        try {
            Field found = Class.forName(className, false, loader).getField(field);
            return Modifier.isStatic(found.getModifiers());
        } catch (ClassNotFoundException | NoSuchFieldException | LinkageError e) {
            return false;
        }
    }
}
