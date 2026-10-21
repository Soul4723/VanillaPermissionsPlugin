package io.github.soul4723.vanillapermissions.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerSoftwareTest {

    @Test
    void forksReportTheirOwnName() {
        // "Running on Bukkit - <name>" in LuckPerms' banner on real boots of each.
        assertEquals(ServerSoftware.PAPER, ServerSoftware.detect("Paper", true));
        assertEquals(ServerSoftware.PURPUR, ServerSoftware.detect("Purpur", true));
        assertEquals(ServerSoftware.PUFFERFISH, ServerSoftware.detect("Pufferfish", true));
    }

    @Test
    void foliaIsDetectedBeforePaper() {
        assertEquals(ServerSoftware.FOLIA, ServerSoftware.detect("Folia", true));
        assertEquals(ServerSoftware.FOLIA, ServerSoftware.detect("Folia (Paper)", true));
        assertEquals(ServerSoftware.FOLIA, ServerSoftware.detect("FOLIA", false));
        assertEquals("folia", ServerSoftware.FOLIA.id());
    }

    @Test
    void spigotReportsCraftBukkitWithoutPaperApi() {
        assertEquals(ServerSoftware.SPIGOT, ServerSoftware.detect("CraftBukkit", false));
    }

    @Test
    void unknownForksFallBackByPaperApi() {
        assertEquals(ServerSoftware.PAPER, ServerSoftware.detect("Leaves", true));
        assertEquals(ServerSoftware.SPIGOT, ServerSoftware.detect("SomeSpigotFork", false));
        assertEquals(ServerSoftware.SPIGOT, ServerSoftware.detect(null, false));
    }

    @Test
    void idsMatchCompatYmlSoftwareField() {
        assertEquals("paper", ServerSoftware.PAPER.id());
        assertEquals("pufferfish", ServerSoftware.PUFFERFISH.id());
    }
}
