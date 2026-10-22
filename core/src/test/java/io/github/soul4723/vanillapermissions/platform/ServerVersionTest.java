package io.github.soul4723.vanillapermissions.platform;

import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ServerVersionTest {

    /** Stands in for Paper's Bukkit class, which has {@code getMinecraftVersion()}. */
    public static final class PaperBukkit {
        public static String getMinecraftVersion() {
            return "1.21.4";
        }
    }

    @Test
    void usesPapersGetterWhereItExists() throws Throwable {
        MethodHandle getter = ServerVersion.minecraftVersionGetter(PaperBukkit.class);
        assertNotNull(getter);
        assertEquals("1.21.4", (String) getter.invokeExact());
    }

    @Test
    void spigotApiHasNoPaperGetter() {
        // core compiles and tests against the Spigot 1.8.8 API, whose Bukkit class lacks getMinecraftVersion().
        assertNull(ServerVersion.minecraftVersionGetter(org.bukkit.Bukkit.class));
    }

    @Test
    void bukkitVersionIsCutAtTheFirstDash() {
        assertEquals("1.8.8", ServerVersion.fromBukkitVersion("1.8.8-R0.1-SNAPSHOT"));
        assertEquals("1.21.4", ServerVersion.fromBukkitVersion("1.21.4-R0.1-SNAPSHOT"));
        assertEquals("26.1", ServerVersion.fromBukkitVersion("26.1"));
    }
}
