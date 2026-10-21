package io.github.soul4723.vanillapermissions.spigot;

import io.github.soul4723.vanillapermissions.platform.Adapters;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which set of runtime names the server uses: a CraftBukkit package token such as {@code v1_20_R3} (Spigot names,
 * Spigot and Paper 1.13 - 1.20.4, Spigot up to 1.21.10), or {@link #MOJANG} (Mojang names: Paper 1.20.5, and Spigot
 * builds that run the unobfuscated server). Decided once per install; every lookup in {@link SpigotNames} reads it.
 *
 * <p>The token comes from {@code Bukkit.getServer().getClass().getPackage().getName()}, as NBT-API's
 * {@code MinecraftVersion} reads it ({@code item-nbt-api/src/main/java/de/tr7zw/changeme/nbtapi/utils/MinecraftVersion.java});
 * Mojang names are detected by a class probe, as ProtocolLib's {@code MinecraftReflection.isMojangMapped()}
 * ({@code src/main/java/com/comphenix/protocol/utility/MinecraftReflection.java}) and PacketEvents' {@code IS_OBFUSCATED}
 * ({@code spigot/src/main/java/io/github/retrooper/packetevents/util/SpigotReflectionUtil.java}) do, because a version string alone does not say which names a server runs.
 */
final class MappingGeneration implements Comparable<MappingGeneration> {

    static final MappingGeneration MOJANG = new MappingGeneration("mojang", Integer.MAX_VALUE, 0, 0);
    /** A class that only exists on servers running Mojang names (Spigot calls it {@code CommandListenerWrapper}). */
    static final String MOJANG_PROBE = "net.minecraft.commands.CommandSourceStack";

    private static final Pattern TOKEN = Pattern.compile("v(\\d+)_(\\d+)_R(\\d+)");

    private final String token;
    private final int major;
    private final int minor;
    private final int revision;

    private MappingGeneration(String token, int major, int minor, int revision) {
        this.token = token;
        this.major = major;
        this.minor = minor;
        this.revision = revision;
    }

    /** A CraftBukkit package token, e.g. {@code v1_13_R2}. */
    static MappingGeneration of(String token) {
        Matcher matcher = TOKEN.matcher(token);
        if (!matcher.matches()) throw new IllegalArgumentException("Not a CraftBukkit package token: " + token);
        return new MappingGeneration(token, Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)));
    }

    /**
     * The generation of a server: {@link #MOJANG} if {@link #MOJANG_PROBE} loads through {@code loader}, otherwise the
     * token at the end of the CraftBukkit server class's package.
     */
    static MappingGeneration detect(Class<?> craftServer, ClassLoader loader) {
        if (Adapters.classExists(MOJANG_PROBE, loader)) return MOJANG;
        String name = craftServer.getName();
        String packageName = name.substring(0, Math.max(0, name.lastIndexOf('.')));
        String token = packageName.substring(packageName.lastIndexOf('.') + 1);
        if (!TOKEN.matcher(token).matches()) {
            throw new IllegalStateException("The server class " + name + " has no CraftBukkit version package and the server"
                    + " does not run Mojang names, so its native names are unknown");
        }
        return of(token);
    }

    boolean isMojang() {
        return this == MOJANG;
    }

    /** The NMS package of this generation before 1.17 ({@code net.minecraft.server.v1_16_R3}). */
    String nmsPackage() {
        return "net.minecraft.server." + token;
    }

    @Override
    public int compareTo(MappingGeneration other) {
        if (major != other.major) return Integer.compare(major, other.major);
        if (minor != other.minor) return Integer.compare(minor, other.minor);
        return Integer.compare(revision, other.revision);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MappingGeneration && token.equals(((MappingGeneration) other).token);
    }

    @Override
    public int hashCode() {
        return token.hashCode();
    }

    @Override
    public String toString() {
        return token;
    }
}
