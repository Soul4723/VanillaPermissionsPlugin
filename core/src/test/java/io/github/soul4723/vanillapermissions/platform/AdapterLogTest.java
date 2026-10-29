package io.github.soul4723.vanillapermissions.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdapterLogTest {

    @Test
    void startupLineNamesTheModeInUserWords() {
        assertEquals("Using Paper mode on Paper 1.21.11 (checks on every vanilla command and sub-command)",
            AdapterLog.using("paper", "Paper", "1.21.11"));
        assertEquals("Using Spigot mode on Paper 1.16.5 (checks on every vanilla command and sub-command)",
            AdapterLog.using("spigot", "Paper", "1.16.5"));
        assertEquals("legacy mode", AdapterLog.mode("legacy"));
    }

    /** One failure line for every mode, starting with the text scripts/compat-smoke.sh looks for. */
    @Test
    void installFailureSharesOneTemplate() {
        String line = AdapterLog.installFailed("Spigot", "1.21.11", "Spigot 1.8.8, 1.20.4");
        assertTrue(line.startsWith(AdapterLog.INSTALL_FAILED + " to vanilla commands on Spigot 1.21.11 (tested: Spigot 1.8.8, 1.20.4)"), line);
        assertTrue(line.contains(AdapterLog.COMPATIBILITY) && line.endsWith(AdapterLog.ISSUES), line);
    }
}
