package io.github.soul4723.vanillapermissions.platform;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** scripts/compat-smoke.sh greps the server log, so its patterns must match what the plugin really logs. */
class CompatSmokeLogPatternsTest {

    @Test
    void scriptGrepsTheLogLinesThePluginWrites() throws IOException {
        String script = Files.readString(Path.of(System.getProperty("repo.root", "..")).resolve("scripts/compat-smoke.sh"));
        String success = AdapterLog.ADDED_PREFIX + "\\([0-9]*\\)" + AdapterLog.ADDED_SUFFIX;
        assertTrue(script.contains("s/.*" + success + ".*/"), "success pattern out of sync with the plugin's log");
        assertTrue(script.contains("|" + AdapterLog.INSTALL_FAILED + "|"), "failure pattern out of sync with the plugin's log");
        assertEquals(AdapterLog.ADDED_PREFIX + 1867 + AdapterLog.ADDED_SUFFIX, AdapterLog.added(1867));
    }
}
