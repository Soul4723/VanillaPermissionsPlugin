package io.github.soul4723.vanillapermissions.platform;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Adapter choice from fake candidates; the real Paper probe is covered by the compat smoke boots. */
class AdaptersTest {
    private final List<LogRecord> records = new ArrayList<>();
    private final Logger logger = Logger.getAnonymousLogger();
    private final List<String> probed = new ArrayList<>();
    private final ClassLoader loader = AdaptersTest.class.getClassLoader();

    AdaptersTest() {
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() {}
            @Override public void close() {}
        });
    }

    public static class First extends Fake {}

    public static class Second extends Fake {}

    public static class Failing extends Fake {
        public Failing() {
            throw new IllegalStateException("adapter needs missing API");
        }
    }

    public abstract static class Fake implements VersionAdapter {
        @Override public String id() { return getClass().getSimpleName(); }
        @Override public void enable(JavaPlugin plugin) {}
        @Override public void disable() {}
        @Override public int checkedNodeCount() { return 0; }
    }

    private AdapterCandidate candidate(String id, int minimumJava, Class<?> type, boolean matches) {
        return candidate(id, minimumJava, type.getName(), matches);
    }

    private AdapterCandidate candidate(String id, int minimumJava, String className, boolean matches) {
        return new AdapterCandidate(id, minimumJava, className, () -> {
            probed.add(id);
            return matches;
        });
    }

    @Test
    void firstMatchingCandidateWinsAndLaterOnesAreNotProbed() {
        VersionAdapter adapter = Adapters.select(List.of(
            candidate("no-match", 8, First.class, false),
            candidate("second", 8, Second.class, true),
            candidate("first", 8, First.class, true)), 21, loader, logger);
        assertInstanceOf(Second.class, adapter);
        assertEquals(List.of("no-match", "second"), probed);
        assertTrue(records.isEmpty(), "Selection logged " + records);
    }

    @Test
    void candidateNeedingNewerJavaIsSkippedWithoutProbing() {
        VersionAdapter adapter = Adapters.select(List.of(
            candidate("modern", 21, First.class, true),
            candidate("old", 8, Second.class, true)), 17, loader, logger);
        assertInstanceOf(Second.class, adapter);
        assertEquals(List.of("old"), probed, "The Java gate must come before the probe");
    }

    @Test
    void matchingAdapterThatFailsToLoadStopsSelection() {
        VersionAdapter adapter = Adapters.select(List.of(
            candidate("missing", 8, "io.github.soul4723.vanillapermissions.platform.NoSuchAdapter", true),
            candidate("failing", 8, Failing.class, true),
            candidate("not-an-adapter", 8, String.class, true),
            candidate("working", 8, First.class, true)), 21, loader, logger);
        assertNull(adapter);
        assertEquals(List.of("missing"), probed, "A matching candidate that fails to load must stop selection");
        assertEquals(1, records.size(), "The failed matching candidate should log one warning");
        assertTrue(records.get(0).getLevel() == Level.WARNING);
        assertTrue(records.get(0).getMessage().contains("missing"), records.get(0).getMessage());
    }

    @Test
    void probeThatThrowsCountsAsNoMatch() {
        VersionAdapter adapter = Adapters.select(List.of(
            new AdapterCandidate("broken", 8, First.class.getName(), () -> { throw new NoClassDefFoundError("Probe"); }),
            candidate("working", 8, Second.class, true)), 21, loader, logger);
        assertInstanceOf(Second.class, adapter);
    }

    @Test
    void noMatchingCandidateGivesNull() {
        assertNull(Adapters.select(List.of(candidate("modern", 25, First.class, true),
            candidate("no-match", 8, Second.class, false)), 21, loader, logger));
        assertNull(Adapters.select(List.of(), 21, loader, logger));
    }

    @Test
    void javaVersionParsesBothSchemes() {
        assertEquals(8, Adapters.parseJavaVersion("1.8"));
        assertEquals(17, Adapters.parseJavaVersion("17"));
        assertEquals(21, Adapters.parseJavaVersion("21"));
        assertEquals(25, Adapters.parseJavaVersion("25.0"));
        assertEquals(0, Adapters.parseJavaVersion(null));
        assertEquals(0, Adapters.parseJavaVersion("unknown"));
        assertEquals(Runtime.version().feature(), Adapters.javaVersion());
    }

    @Test
    void paperIsTriedFirstThenSpigotThenLegacy() {
        List<AdapterCandidate> candidates = Adapters.candidates();
        assertEquals(3, candidates.size());
        assertEquals(List.of("paper", "spigot", "legacy"), candidates.stream().map(AdapterCandidate::id).toList());
        assertEquals(List.of(21, 8, 8), candidates.stream().map(AdapterCandidate::minimumJava).toList());
    }

    @Test
    void legacyProbeNeedsARunningServer() {
        // No Bukkit server in unit tests: the probe must say no rather than throw.
        assertFalse(Adapters.isLegacyCraftBukkit());
        assertTrue(Adapters.classExists("java.lang.String", loader));
        assertFalse(Adapters.classExists("com.mojang.brigadier.CommandDispatcher", ClassLoader.getPlatformClassLoader()));
    }

    @Test
    void spigotProbeNeedsACraftBukkitServer() {
        // Brigadier is not on core's test classpath and there is no server in a unit test.
        assertFalse(Adapters.candidates().get(1).probe().matches());
        assertFalse(Adapters.isCraftBukkit(null));
        assertFalse(Adapters.isCraftBukkit(new Object()));
    }

    @Test
    void foliaNeverFallsBackToSpigotOrLegacy() {
        List<AdapterCandidate> candidates = Adapters.candidates(true);
        assertEquals("paper", candidates.get(0).id());
        assertFalse(candidates.get(1).probe().matches());
        assertFalse(candidates.get(2).probe().matches());
        assertNull(Adapters.select(candidates, 17, loader, logger), "Old Folia must be refused");
        assertNull(Adapters.select(candidates, 21, loader, logger), "Folia without COMMANDS must be refused");
    }

    @Test
    void foliaProbeUsesTheSuppliedServerLoaderWithoutInitialisingTheClass() {
        assertTrue(Adapters.isFolia(loader));
        assertFalse(Adapters.isFolia(ClassLoader.getPlatformClassLoader()));
        ClassLoader broken = new ClassLoader(loader) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals("io.papermc.paper.threadedregions.RegionizedServer")) {
                    throw new NoClassDefFoundError("Missing Folia dependency");
                }
                return super.loadClass(name, resolve);
            }
        };
        assertFalse(Adapters.isFolia(broken));
    }

    @Test
    void staticFieldProbeNeedsBothClassAndStaticField() {
        assertTrue(Adapters.hasStaticField("java.lang.Integer", "MAX_VALUE", Adapters.serverLoader()));
        assertFalse(Adapters.hasStaticField("java.lang.Integer", "NO_SUCH_FIELD", Adapters.serverLoader()));
        assertFalse(Adapters.hasStaticField("io.github.soul4723.vanillapermissions.platform.NoSuchClass", "COMMANDS", Adapters.serverLoader()));
    }

    @Test
    void staticFieldProbeOnlySeesTheGivenLoader() {
        // A loader that cannot see this test class stands in for the server's, which does not see plugin classes.
        ClassLoader platformOnly = ClassLoader.getPlatformClassLoader();
        assertTrue(Adapters.hasStaticField(AdaptersTest.class.getName() + "$Marker", "COMMANDS", AdaptersTest.class.getClassLoader()));
        assertFalse(Adapters.hasStaticField(AdaptersTest.class.getName() + "$Marker", "COMMANDS", platformOnly));
    }

    static final class Marker {
        public static final Object COMMANDS = new Object();
    }
}
