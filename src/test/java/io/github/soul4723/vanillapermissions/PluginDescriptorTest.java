package io.github.soul4723.vanillapermissions;

import org.bukkit.permissions.Permission;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Checks the filtered plugin.yml that Maven packages; live Paper/LuckPerms resolution is not covered. */
class PluginDescriptorTest {
    private static final Path CLASSES = Path.of("target/classes");
    private static String raw;
    private static PluginDescriptionFile descriptor;

    @BeforeAll
    static void loadDescriptor() throws Exception {
        byte[] bytes = Files.readAllBytes(CLASSES.resolve("plugin.yml"));
        raw = new String(bytes, StandardCharsets.UTF_8);
        descriptor = new PluginDescriptionFile(new ByteArrayInputStream(bytes));
    }

    @Test
    void versionAndMainComeFromTheBuild() {
        assertFalse(raw.contains("${"), "plugin.yml has unfiltered Maven placeholders");
        assertEquals(System.getProperty("project.version"), descriptor.getVersion(), "plugin.yml version differs from pom.xml");
        assertEquals(VanillaPermissionsPlugin.class.getName(), descriptor.getMain());
        assertEquals("VanillaPermissionsPlugin", descriptor.getName());
    }

    @Test
    void nativePermissionsAreNotRedefined() {
        for (Permission permission : descriptor.getPermissions()) {
            assertFalse(permission.getName().startsWith("minecraft."),
                "Plugin must not redefine native permission defaults: " + permission.getName());
        }
    }

    @Test
    void removedAdminBroadcastListenerStaysRemoved() throws Exception {
        assertFalse(Files.exists(CLASSES.resolve("io/github/soul4723/vanillapermissions/listener/AdminBroadcastListener.class")));
        byte[] main = Files.readAllBytes(CLASSES.resolve("io/github/soul4723/vanillapermissions/VanillaPermissionsPlugin.class"));
        assertFalse(new String(main, StandardCharsets.ISO_8859_1).contains("AdminBroadcastListener"));
    }
}
