package io.github.soul4723.vanillapermissions.paper;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static io.github.soul4723.vanillapermissions.util.TestLuckPerms.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Stand-ins for CommandSource/CommandSourceStack; the live NMS wiring is checked on a Paper server. */
class PaperBridgeTest {

    @Test
    void feedbackRedirectKeepsTheOriginalSender() throws Exception {
        CommandSender player = proxy(CommandSender.class, (p, m, a) -> { throw new AssertionError(m); });
        CommandSender console = proxy(CommandSender.class, (p, m, a) -> { throw new AssertionError(m); });
        RecordingOutput output = new RecordingOutput(console);

        Method getSender = FakeStack.class.getMethod("getBukkitSender");
        Method withSource = FakeStack.class.getMethod("withSource", FakeCommandSource.class);
        FakeStack redirected = (FakeStack) PaperBridge.withOutput(FakeCommandSource.class,
                getSender, withSource, new FakeStack(new RecordingOutput(player)), output);

        assertSame(player, redirected.getBukkitSender(), "Feedback redirect changed whose permissions are checked");
        redirected.source.sendSystemMessage("done");
        assertEquals(List.of("done"), output.messages, "Command output did not reach the feedback target");
        FakeStack nested = (FakeStack) PaperBridge.withOutput(FakeCommandSource.class,
                getSender, withSource, redirected, new RecordingOutput(console));
        assertSame(player, nested.getBukkitSender(), "Nested feedback redirect lost the original sender");
        assertInstanceOf(FakeCommandSource.class, nested.source);
    }

    public interface FakeCommandSource {
        CommandSender getBukkitSender(FakeStack stack);

        void sendSystemMessage(String message);
    }

    public static final class RecordingOutput implements FakeCommandSource {
        private final CommandSender sender;
        private final List<String> messages = new ArrayList<>();

        RecordingOutput(CommandSender sender) { this.sender = sender; }

        @Override public CommandSender getBukkitSender(FakeStack stack) { return sender; }

        @Override public void sendSystemMessage(String message) { messages.add(message); }
    }

    public static final class FakeStack {
        private final FakeCommandSource source;

        FakeStack(FakeCommandSource source) { this.source = source; }

        public CommandSender getBukkitSender() { return source.getBukkitSender(this); }

        public FakeStack withSource(FakeCommandSource replacement) { return new FakeStack(replacement); }
    }
}
