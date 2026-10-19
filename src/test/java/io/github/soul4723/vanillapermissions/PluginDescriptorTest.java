package io.github.soul4723.vanillapermissions;

import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void onlyTheBypassNodesAreDeclaredUnderMinecraft() {
        Set<String> expected = Set.of("minecraft.bypass.whitelist", "minecraft.bypass.player-limit",
            "minecraft.bypass.force-gamemode", "minecraft.bypass.move-speed.player");
        Set<String> declared = new HashSet<>();
        for (Permission permission : descriptor.getPermissions()) {
            if (!permission.getName().startsWith("minecraft.")) continue;
            declared.add(permission.getName());
            assertEquals(PermissionDefault.FALSE, permission.getDefault(), permission.getName() + " must default to false");
            assertFalse(permission.getDescription().isBlank(), permission.getName() + " has no description");
        }
        assertEquals(expected, declared, "Plugin must declare only the bypass nodes under minecraft.");
    }

    @Test
    void commandsDeclareDeclaredPermissions() {
        Set<String> declared = descriptor.getPermissions().stream().map(Permission::getName).collect(Collectors.toSet());
        Map<String, Map<String, Object>> commands = descriptor.getCommands();
        assertTrue(commands.containsKey("vanillapermissions"), "Admin command is not declared");
        commands.forEach((name, command) -> {
            assertNotNull(command.get("description"), name + " has no description");
            assertNotNull(command.get("usage"), name + " has no usage");
            Object permission = command.get("permission");
            assertNotNull(permission, name + " has no permission");
            assertTrue(declared.contains(permission), name + " uses undeclared permission " + permission);
        });
    }

    @Test
    void removedAdminBroadcastListenerStaysRemoved() throws Exception {
        assertFalse(Files.exists(CLASSES.resolve("io/github/soul4723/vanillapermissions/listener/AdminBroadcastListener.class")));
        byte[] main = Files.readAllBytes(CLASSES.resolve("io/github/soul4723/vanillapermissions/VanillaPermissionsPlugin.class"));
        assertFalse(new String(main, StandardCharsets.ISO_8859_1).contains("AdminBroadcastListener"));
    }
}
