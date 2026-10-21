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
import static org.junit.jupiter.api.Assertions.assertSame;

class PaperSchedulerTest {
    private final PluginScheduler scheduler = new PaperAdapter().scheduler(null);
    private final AtomicInteger ran = new AtomicInteger();
    private final AtomicInteger retired = new AtomicInteger();
    private final AtomicReference<Consumer<ScheduledTask>> callback = new AtomicReference<>();
    private final AtomicReference<Runnable> retiredCallback = new AtomicReference<>();

    private Player player(boolean accepts) {
        ScheduledTask accepted = proxy(ScheduledTask.class, (p, m, a) -> { throw new AssertionError(m); });
        EntityScheduler entity = proxy(EntityScheduler.class, (p, m, a) -> {
            assertEquals("runDelayed", m.getName());
            assertNull(a[0]);
            assertEquals(10L, a[3]);
            @SuppressWarnings("unchecked")
            Consumer<ScheduledTask> task = (Consumer<ScheduledTask>) a[1];
            callback.set(task);
            retiredCallback.set((Runnable) a[2]);
            return accepts ? accepted : null;
        });
        return proxy(Player.class, (p, m, a) -> {
            assertEquals("getScheduler", m.getName());
            return entity;
        });
    }

    @Test
    void refreshRunsOnlyWhenTheEntitySchedulerCallsIt() {
        scheduler.runLater(player(true), ran::incrementAndGet, 10, retired::incrementAndGet);
        assertEquals(0, ran.get());
        callback.get().accept(null);
        assertEquals(1, ran.get());
        assertEquals(0, retired.get());
    }

    @Test
    void acceptedTaskCanRetireWithoutRunning() {
        Runnable onRetired = retired::incrementAndGet;
        scheduler.runLater(player(true), ran::incrementAndGet, 10, onRetired);
        assertSame(onRetired, retiredCallback.get());
        retiredCallback.get().run();
        assertEquals(1, retired.get());
        assertEquals(0, ran.get());
    }

    @Test
    void alreadyRetiredEntityStillRunsCleanupWhenRunDelayedReturnsNull() {
        scheduler.runLater(player(false), ran::incrementAndGet, 10, retired::incrementAndGet);
        assertEquals(1, retired.get());
        assertEquals(0, ran.get());
    }
}
