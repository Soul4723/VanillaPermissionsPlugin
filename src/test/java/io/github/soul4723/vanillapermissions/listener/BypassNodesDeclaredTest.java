package io.github.soul4723.vanillapermissions.listener;

import org.bukkit.permissions.Permission;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Keeps the nodes the bypass listeners check in step with the permissions declared in plugin.yml. */
class BypassNodesDeclaredTest {
    @Test
    void listenerNodesAreDeclaredInPluginYml() throws Exception {
        Set<String> declared;
        try (InputStream in = Files.newInputStream(Path.of("target/classes/plugin.yml"))) {
            declared = new PluginDescriptionFile(in).getPermissions().stream()
                .map(Permission::getName).collect(Collectors.toSet());
        }
        for (String node : Set.of(WhitelistBypassListener.WHITELIST_PERMISSION, WhitelistBypassListener.PLAYER_LIMIT_PERMISSION,
                ForceGamemodeBypassListener.BYPASS_PERMISSION, MoveSpeedBypassListener.PLAYER_SPEED_PERMISSION)) {
            assertTrue(declared.contains(node), node + " is checked by a listener but not declared in plugin.yml");
        }
    }
}
