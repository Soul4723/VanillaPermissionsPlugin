package io.github.soul4723.vanillapermissions.util;

import io.github.soul4723.vanillapermissions.platform.Adapters;
import org.bukkit.Bukkit;

import java.util.Locale;

/**
 * The server software families {@code compat.yml} boots, used to look up which versions were tested on this one.
 * Seen on real boots: Paper, Purpur and Pufferfish report their own name from {@code Bukkit.getName()}
 * ("Running on Bukkit - Purpur" in LuckPerms' banner); Spigot (and CraftBukkit) report "CraftBukkit" with
 * "Spigot" in {@code Bukkit.getVersion()} ("git-Spigot-..."), so Spigot is what is left when no Paper API exists.
 */
public enum ServerSoftware {
    PAPER("Paper"),
    FOLIA("Folia"),
    SPIGOT("Spigot"),
    PURPUR("Purpur"),
    PUFFERFISH("Pufferfish");

    private final String displayName;

    ServerSoftware(String displayName) {
        this.displayName = displayName;
    }

    /** The name used in logs and in {@code compat.yml}'s {@code software:} field, capitalised. */
    public String displayName() {
        return displayName;
    }

    /** The lower-case name {@code compat.yml} uses in {@code software:}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The software this server is, from {@code Bukkit.getName()} and whether Paper's API is present. */
    public static ServerSoftware detect() {
        // By class, so Folia forks that report their own name are not taken for Paper.
        if (Adapters.isFolia(Bukkit.getServer().getClass().getClassLoader())) return FOLIA;
        return detect(Bukkit.getName(), hasPaperApi());
    }

    /**
     * Unknown forks (Leaves, ...) fall back to Paper when the server has Paper's API, else to Spigot, so a
     * fork is only called tested for versions its base software was.
     */
    public static ServerSoftware detect(String name, boolean paperApi) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (lower.contains("folia")) return FOLIA;
        if (lower.contains("purpur")) return PURPUR;
        if (lower.contains("pufferfish")) return PUFFERFISH;
        if (lower.contains("paper")) return PAPER;
        // Spigot itself ("CraftBukkit", version "git-Spigot-...") has no Paper API, so it lands on the fallback below.
        return paperApi ? PAPER : SPIGOT;
    }

    /** Paper's own classes: {@code com.destroystokyo.paper} up to 1.20.4 and {@code io.papermc.paper} since 1.19. */
    private static boolean hasPaperApi() {
        ClassLoader loader = Bukkit.class.getClassLoader();
        return Adapters.classExists("io.papermc.paper.configuration.GlobalConfiguration", loader)
            || Adapters.classExists("com.destroystokyo.paper.PaperConfig", loader)
            || Adapters.classExists("org.github.paperspigot.PaperSpigotConfig", loader);
    }
}
