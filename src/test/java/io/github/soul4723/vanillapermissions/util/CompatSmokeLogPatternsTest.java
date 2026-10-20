package io.github.soul4723.vanillapermissions.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** scripts/compat-smoke.sh greps the server log, so its patterns must match what the plugin really logs. */
class CompatSmokeLogPatternsTest {

    @Test
    void scriptGrepsTheLogLinesThePluginWrites() throws IOException {
        String script = Files.readString(Path.of("scripts/compat-smoke.sh"));
        String success = NativeCommandPermissions.ADDED_PREFIX + "\\([0-9]*\\)" + NativeCommandPermissions.ADDED_SUFFIX;
        assertTrue(script.contains("s/.*" + success + ".*/"), "success pattern out of sync with the plugin's log");
        assertTrue(script.contains("|" + NativeCommandPermissions.INSTALL_FAILED + "|"), "failure pattern out of sync with the plugin's log");
    }
}
