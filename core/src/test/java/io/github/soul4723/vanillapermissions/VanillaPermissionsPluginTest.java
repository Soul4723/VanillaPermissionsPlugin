package io.github.soul4723.vanillapermissions;

import io.github.soul4723.vanillapermissions.platform.VersionAdapter;
import io.github.soul4723.vanillapermissions.util.PermissionProvider;
import io.github.soul4723.vanillapermissions.util.PermissionValue;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VanillaPermissionsPluginTest {

    @Test
    void adapterIsDisabledBeforeProviderCloseThrows() {
        List<String> calls = new ArrayList<>();
        VersionAdapter adapter = new VersionAdapter() {
            @Override public String id() { return "test"; }
            @Override public void enable(JavaPlugin plugin) {}
            @Override public void disable() { calls.add("adapter"); }
            @Override public int checkedNodeCount() { return 0; }
        };
        PermissionProvider provider = new PermissionProvider() {
            @Override public PermissionValue getPermissionValue(Player player, String node) { return PermissionValue.UNDEFINED; }
            @Override public boolean hasPermission(Player player, String node) { return false; }
            @Override public OptionalInt getIntegerMeta(UUID subjectId, CommandSender onlineSubject, String key) {
                return OptionalInt.empty();
            }
            @Override public String describePermissions() { return "test"; }
            @Override public String describeMeta() { return "test"; }
            @Override public void close() {
                calls.add("provider");
                throw new IllegalStateException("close failed");
            }
        };

        assertThrows(IllegalStateException.class, () -> VanillaPermissionsPlugin.disable(adapter, provider));
        assertEquals(List.of("adapter", "provider"), calls);
    }
}
