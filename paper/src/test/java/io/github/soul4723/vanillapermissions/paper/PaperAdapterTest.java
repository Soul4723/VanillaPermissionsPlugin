package io.github.soul4723.vanillapermissions.paper;

import io.github.soul4723.vanillapermissions.platform.Adapters;
import io.github.soul4723.vanillapermissions.platform.VersionAdapter;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The real candidate list against the Paper API this module compiles with. */
class PaperAdapterTest {

    @Test
    void paperApiSelectsThePaperAdapter() {
        assertTrue(Adapters.candidates().get(0).probe().matches(), "Paper probe missed LifecycleEvents.COMMANDS");
        VersionAdapter adapter = Adapters.select(PaperAdapterTest.class.getClassLoader(), Logger.getAnonymousLogger());
        PaperAdapter paper = assertInstanceOf(PaperAdapter.class, adapter);
        assertEquals("paper", paper.id());
        assertEquals(0, paper.checkedNodeCount(), "A fresh adapter reported checked nodes before installing");
    }
}
