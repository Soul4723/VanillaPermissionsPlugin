package io.github.soul4723.vanillapermissions.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The verified version list, the compat smoke matrix and the plugin.yml floor must agree. */
class SupportedVersionsTest {

    @Test
    void compatMatrixBootsEveryVerifiedVersion() throws IOException {
        String workflow = Files.readString(Path.of(".github/workflows/compat.yml"));
        Matcher entry = Pattern.compile("minecraft: '([^']+)'").matcher(workflow);
        List<String> matrix = new ArrayList<>();
        while (entry.find()) matrix.add(entry.group(1));
        assertEquals(SupportedVersions.VERIFIED, matrix);
    }

    @Test
    void apiVersionIsTheOldestVerifiedVersion() throws IOException {
        String descriptor = Files.readString(Path.of("src/main/resources/plugin.yml"));
        assertTrue(descriptor.contains("api-version: '" + SupportedVersions.VERIFIED.get(0) + "'"), descriptor);
    }

    @Test
    void onlyListedVersionsAreVerified() {
        assertTrue(SupportedVersions.isVerified("1.21.4"));
        assertFalse(SupportedVersions.isVerified("1.20.5"));
        assertFalse(SupportedVersions.isVerified("1.21.2"));
        assertEquals("1.20.6 to 26.3", SupportedVersions.range());
    }
}
