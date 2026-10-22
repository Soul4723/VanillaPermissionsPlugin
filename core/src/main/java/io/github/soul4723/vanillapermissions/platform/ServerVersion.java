package io.github.soul4723.vanillapermissions.platform;

import org.bukkit.Bukkit;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** The running Minecraft version, e.g. {@code 1.21.4}, on servers with and without Paper's API. */
public final class ServerVersion {

    /** Paper's {@code Bukkit.getMinecraftVersion()}, or {@code null} on servers without it. */
    private static final MethodHandle MINECRAFT_VERSION = minecraftVersionGetter(Bukkit.class);

    private ServerVersion() {}

    /** Paper's {@code Bukkit.getMinecraftVersion()} where it exists, else the Bukkit version up to its first '-'. */
    public static String minecraft() {
        if (MINECRAFT_VERSION == null) return fromBukkitVersion(Bukkit.getBukkitVersion());
        try {
            return (String) MINECRAFT_VERSION.invokeExact();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    static MethodHandle minecraftVersionGetter(Class<?> bukkit) {
        try {
            return MethodHandles.publicLookup().findStatic(bukkit, "getMinecraftVersion", MethodType.methodType(String.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            return null;
        }
    }

    /** {@code 1.8.8} for {@code 1.8.8-R0.1-SNAPSHOT}. */
    static String fromBukkitVersion(String bukkitVersion) {
        int dash = bukkitVersion.indexOf('-');
        return dash < 0 ? bukkitVersion : bukkitVersion.substring(0, dash);
    }
}
