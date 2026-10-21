package io.github.soul4723.vanillapermissions.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The verified version lists, the compat smoke matrix and the plugin.yml floor must agree. */
class SupportedVersionsTest {
    private static final Path REPO = Path.of(System.getProperty("repo.root", ".."));

    private static String workflow() throws IOException {
        return Files.readString(REPO.resolve(".github/workflows/compat.yml"));
    }

    /** The {@code minecraft:} values of every {@code software: <id>} row in compat.yml, in file order. */
    private static List<String> matrixRows(String software) throws IOException {
        Matcher entry = Pattern.compile("software: " + software + ", minecraft: '([^']+)'").matcher(workflow());
        List<String> rows = new ArrayList<>();
        while (entry.find()) rows.add(entry.group(1));
        return rows;
    }

    @Test
    void compatMatrixBootsEveryVerifiedVersionOfEverySoftware() throws IOException {
        for (ServerSoftware software : ServerSoftware.values()) {
            assertEquals(SupportedVersions.verified(software), matrixRows(software.id()), software + " rows in compat.yml");
        }
    }

    @Test
    void everyMatrixRowBelongsToAKnownSoftware() throws IOException {
        Matcher entry = Pattern.compile("\\{ software: (\\w+),").matcher(workflow());
        int rows = 0;
        while (entry.find()) {
            rows++;
            String id = entry.group(1);
            assertTrue(Arrays.stream(ServerSoftware.values()).anyMatch(s -> s.id().equals(id)), "unknown software " + id);
        }
        int listed = 0;
        for (ServerSoftware software : ServerSoftware.values()) listed += SupportedVersions.verified(software).size();
        assertEquals(listed, rows);
    }

    @Test
    void everyRowHasAJavaVersion() throws IOException {
        Matcher rows = Pattern.compile("\\{ software: [^}]*\\}").matcher(workflow());
        while (rows.find()) assertTrue(rows.group().contains("java: "), rows.group());
    }

    @Test
    void apiVersionIsTheMultiVersionFloor() throws IOException {
        String descriptor = Files.readString(REPO.resolve("plugin/src/main/resources/plugin.yml"));
        // The jar carries the 1.13+ adapters, so it declares the lowest value every 1.13+ server accepts.
        assertTrue(descriptor.contains("folia-supported: true"), descriptor);
        assertTrue(descriptor.contains("api-version: '1.13'"), descriptor);
        assertTrue(SupportedVersions.isVerified(ServerSoftware.PAPER, "1.13.2"));
        assertTrue(SupportedVersions.isVerified(ServerSoftware.PAPER, "1.20.6"));
    }

    @Test
    void versionsAreCheckedPerSoftware() {
        assertTrue(SupportedVersions.isVerified(ServerSoftware.PAPER, "1.21.4"));
        assertTrue(SupportedVersions.isVerified(ServerSoftware.PAPER, "1.20.5"));
        assertFalse(SupportedVersions.isVerified(ServerSoftware.PAPER, "1.15.2"));
        assertFalse(SupportedVersions.isVerified(ServerSoftware.PAPER, "1.21.2"));
        assertTrue(SupportedVersions.isVerified(ServerSoftware.SPIGOT, "1.8.8"));
        assertFalse(SupportedVersions.isVerified(ServerSoftware.SPIGOT, "1.21.4"));
        assertFalse(SupportedVersions.isVerified(ServerSoftware.PURPUR, "1.8.8"));
        assertEquals(List.of("1.20.6", "1.21.11", "26.1.2", "26.3"), SupportedVersions.verified(ServerSoftware.FOLIA));
        assertFalse(SupportedVersions.isVerified(ServerSoftware.FOLIA, "1.20.4"));
        assertFalse(SupportedVersions.isVerified(ServerSoftware.FOLIA, "1.21.4"));
        assertEquals("1.8.8 to 26.3", SupportedVersions.range(ServerSoftware.PAPER));
        assertEquals("1.8.8 to 1.20.4", SupportedVersions.range(ServerSoftware.SPIGOT));
    }

    @Test
    void describeNamesTheSoftwareAndListsItsVersions() {
        assertTrue(SupportedVersions.describe(ServerSoftware.PURPUR).startsWith("Purpur 1.20.6, 1.21.11"));
        assertTrue(SupportedVersions.describe(ServerSoftware.PAPER).startsWith("Paper 1.8.8, "));
    }
}
