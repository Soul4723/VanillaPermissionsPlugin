package io.github.soul4723.vanillapermissions.paper;

import io.github.soul4723.vanillapermissions.platform.PluginScheduler;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static io.github.soul4723.vanillapermissions.util.TestLuckPerms.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Folia: a command tree may only be resent from the player's own thread, so the resend goes through its scheduler. */
class PaperSchedulerTest {

    @Test
    void taskRunsOnlyWhenTheEntitySchedulerCallsIt() {
        AtomicReference<Consumer<ScheduledTask>> callback = new AtomicReference<>();
        EntityScheduler entity = proxy(EntityScheduler.class, (p, m, a) -> {
            assertEquals("runDelayed", m.getName());
            assertNull(a[0]);
            assertEquals(10L, a[3]);
            @SuppressWarnings("unchecked")
            Consumer<ScheduledTask> task = (Consumer<ScheduledTask>) a[1];
            callback.set(task);
            return null;
        });
        Player player = proxy(Player.class, (p, m, a) -> {
            assertEquals("getScheduler", m.getName());
            return entity;
        });
        AtomicInteger ran = new AtomicInteger();
        PluginScheduler scheduler = new PaperAdapter().scheduler(null);
        scheduler.runLater(player, ran::incrementAndGet, 10);
        assertEquals(0, ran.get());
        callback.get().accept(null);
        assertEquals(1, ran.get());
    }
}
