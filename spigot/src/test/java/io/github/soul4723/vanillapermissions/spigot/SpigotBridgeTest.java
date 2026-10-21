package io.github.soul4723.vanillapermissions.spigot;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.soul4723.vanillapermissions.brigadier.SelectorOverride;
import io.github.soul4723.vanillapermissions.selector.SelectorRules;
import net.minecraft.server.v1_16_R3.ArgumentEntity;
import net.minecraft.server.v1_16_R3.ArgumentProfile;
import net.minecraft.server.v1_16_R3.ArgumentScoreholder;
import net.minecraft.server.v1_16_R3.CommandListenerWrapper;
import net.minecraft.server.v1_16_R3.Entity;
import net.minecraft.server.v1_16_R3.EntitySelector;
import net.minecraft.world.scores.ScoreHolder;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.util.Map;
import java.util.List;

import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpigotBridgeTest {

    private final CommandSender sender = (CommandSender) Proxy.newProxyInstance(
            CommandSender.class.getClassLoader(), new Class<?>[]{CommandSender.class},
            (proxy, method, args) -> {
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return proxy == args[0];
                if (method.getName().equals("toString")) return "sender";
                return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
            });

    private SpigotBridge bridge;
    private CommandDispatcher<Object> dispatcher;

    @BeforeEach
    void resolve() throws Exception {
        bridge = new SpigotBridge(getClass().getClassLoader(), new Object());
        bridge.resolve(MappingGeneration.of("v1_16_R3"));
        dispatcher = new CommandDispatcher<>();
        dispatcher.register(literal("gm")
                .then(argument("target", ArgumentEntity.multipleEntities())
                        .then(argument("other", ArgumentEntity.multipleEntities()).executes(c -> 1))
                        .executes(c -> 1)));
        dispatcher.register(literal("wrapped")
                .then(argument("profile", new ArgumentProfile())
                        .then(argument("holder", new ArgumentScoreholder()).executes(c -> 1))));
    }

    private CommandContext<Object> parse(String input) {
        return dispatcher.parse(input, new Object()).getContext().build(input);
    }

    private static boolean uses(CommandContext<?> context, String name) {
        return context.getArgument(name, EntitySelector.class).usesSelector();
    }

    @Test
    void operatorLevelReadsTheSourceFieldAndTheEntityMethod() throws Exception {
        assertEquals(3, bridge.operatorLevel(new CommandListenerWrapper(sender, 3)));
        assertEquals(2, bridge.operatorLevel(new Entity(2)));
        assertEquals(0, bridge.operatorLevel("not a source"));
    }

    @Test
    void withOperatorLevelAtMostOnlyLowers() throws Exception {
        CommandListenerWrapper source = new CommandListenerWrapper(sender, 3);

        assertSame(source, bridge.withOperatorLevelAtMost(source, 3));
        assertSame(source, bridge.withOperatorLevelAtMost(source, 4));
        Object lowered = bridge.withOperatorLevelAtMost(source, 1);
        assertEquals(1, bridge.operatorLevel(lowered));
        assertEquals(3, bridge.operatorLevel(source));
    }

    @Test
    void readsTheBukkitSenderAndPermission() throws Exception {
        CommandListenerWrapper source = new CommandListenerWrapper(sender, 0);
        assertSame(sender, bridge.sender(source));
        assertFalse(bridge.allowsSelectors(source));
        assertTrue(bridge.allowsSelectors(new CommandListenerWrapper(sender, 2)));
    }

    @Test
    void selectorResolverExistsOnlyForTheThreeArgumentClasses() {
        assertNotNull(bridge.selectorResolver(ArgumentEntity.multipleEntities()));
        assertNotNull(bridge.selectorResolver(new ArgumentProfile()));
        assertNotNull(bridge.selectorResolver(new ArgumentScoreholder()));
        assertNull(bridge.selectorResolver(StringArgumentType.word()));
    }

    @Test
    void scoreHolderWildcardResolvesTrackedEntries() throws Exception {
        CommandContext<Object> context = parse("wrapped Alice *");
        assertEquals(java.util.List.of("Steve", "Alex", "FakeHolder"),
                bridge.selectorResolver(new ArgumentScoreholder()).resolve(context, "holder"));
    }

    @Test
    void entityValidationUsesTheOptionalResolver() throws Exception {
        NativeAccess access = NativeAccess.resolve(MappingGeneration.of("v1_16_R3"), getClass().getClassLoader());
        assertEquals("c", access.method(SpigotNames.NativeMember.ENTITY_ARGUMENT_GET_OPTIONAL_ENTITIES).getName());
        assertEquals(java.util.List.of(), bridge.selectorResolver(ArgumentEntity.multipleEntities()).resolve(parse("gm @e"), "target"));
    }

    @Test
    void feedbackIsNotRedirectedAndTheSelectorCheckIsNeverBypassedByPaper() {
        assertFalse(bridge.redirectsFeedback());
        assertFalse(bridge.selectorCheckBypassed(new CommandListenerWrapper(sender, 0)));
    }

    @Test
    void bypassClearsEverySelectorFlagAndRestoreSetsThemBack() throws Exception {
        CommandContext<Object> context = parse("gm @s @e");
        assertTrue(uses(context, "target"));
        assertTrue(uses(context, "other"));

        SelectorOverride override = bridge.bypassSelectorCheck(context, null);
        assertFalse(uses(context, "target"));
        assertFalse(uses(context, "other"));

        override.restore();
        assertTrue(uses(context, "target"));
        assertTrue(uses(context, "other"));
    }

    @Test
    void bypassWithAnArgumentNameClearsOnlyThatArgument() throws Exception {
        CommandContext<Object> context = parse("gm @s @e");

        SelectorOverride override = bridge.bypassSelectorCheck(context, "other");
        assertTrue(uses(context, "target"));
        assertFalse(uses(context, "other"));

        override.restore();
        assertTrue(uses(context, "other"));
    }

    @Test
    void restoreLeavesPlainNamesCleared() throws Exception {
        CommandContext<Object> context = parse("gm Notch @e");
        assertFalse(uses(context, "target"));

        SelectorOverride override = bridge.bypassSelectorCheck(context, null);
        assertFalse(uses(context, "other"));
        override.restore();

        assertFalse(uses(context, "target"), "a name never used the selector flag");
        assertTrue(uses(context, "other"));
    }

    @Test
    void bypassReachesSelectorsInsideProfileAndScoreHolderResults() throws Exception {
        CommandContext<Object> context = parse("wrapped @a @e");
        ArgumentProfile.Result profile = context.getArgument("profile", ArgumentProfile.Result.class);
        ArgumentScoreholder.Result holder = context.getArgument("holder", ArgumentScoreholder.Result.class);
        assertTrue(profile.selector().usesSelector());
        assertTrue(holder.selector().usesSelector());

        SelectorOverride override = bridge.bypassSelectorCheck(context, null);
        assertFalse(profile.selector().usesSelector());
        assertFalse(holder.selector().usesSelector());

        override.restore();
        assertTrue(profile.selector().usesSelector());
        assertTrue(holder.selector().usesSelector());
    }

    @Test
    void bypassIgnoresArgumentsThatAreNotSelectors() throws Exception {
        CommandDispatcher<Object> other = new CommandDispatcher<>();
        other.register(literal("say").then(argument("text", StringArgumentType.word()).executes(c -> 1)));
        CommandContext<Object> context = other.parse("say hi", new Object()).getContext().build("say hi");

        bridge.bypassSelectorCheck(context, null).restore();
        assertEquals("hi", context.getArgument("text", String.class));
    }

    @Test
    void scoreHoldersAreRecognisedByTheirInterfaceName() {
        class Holder implements ScoreHolder {}

        assertEquals(SelectorRules.Target.Kind.SCORE_HOLDER, bridge.selectorTarget(new Holder()).kind());
        assertEquals(SelectorRules.Target.Kind.UNSUPPORTED, bridge.selectorTarget(new Object()).kind());
        assertEquals(SelectorRules.Target.Kind.SCORE_HOLDER, bridge.selectorTarget("Notch").kind());
    }

    @Test
    @SuppressWarnings("unchecked")
    void feedbackOnlyUnsilencesCopiesAndKeepsTheOriginalSender() throws Exception {
        Field accessField = SpigotBridge.class.getDeclaredField("access");
        accessField.setAccessible(true);
        NativeAccess access = (NativeAccess) accessField.get(bridge);
        Field classesField = NativeAccess.class.getDeclaredField("classes");
        classesField.setAccessible(true);
        ((Map<SpigotNames.NativeClass, Class<?>>) classesField.get(access))
                .put(SpigotNames.NativeClass.COMMAND_SOURCE, FeedbackOutput.class);
        Field membersField = NativeAccess.class.getDeclaredField("members");
        membersField.setAccessible(true);
        Map<SpigotNames.NativeMember, Member> members = (Map<SpigotNames.NativeMember, Member>) membersField.get(access);
        members.put(SpigotNames.NativeMember.SOURCE_WITH_SOURCE, FeedbackStack.class.getMethod("withSource", FeedbackOutput.class));
        members.put(SpigotNames.NativeMember.SOURCE_SILENT, FeedbackStack.class.getField("silent"));
        Field senderField = SpigotBridge.class.getDeclaredField("getBukkitSender");
        senderField.setAccessible(true);
        senderField.set(bridge, FeedbackStack.class.getMethod("getBukkitSender"));
        FeedbackStack source = new FeedbackStack(sender, true);

        FeedbackStack console = (FeedbackStack) bridge.feedbackToServer(source);
        assertTrue(source.silent);
        assertFalse(console.silent);
        assertSame(sender, console.getBukkitSender());
        List<Object> copies = bridge.feedbackToEntities(source, List.of(new Object(), new Object()));
        assertEquals(2, copies.size());
        for (Object copy : copies) {
            assertFalse(((FeedbackStack) copy).silent);
            assertSame(sender, ((FeedbackStack) copy).getBukkitSender());
        }
        assertTrue(source.silent, "A reused function source must remain silent");
    }

    public interface FeedbackOutput {
        CommandSender getBukkitSender();
    }

    public static final class FeedbackStack {
        private final CommandSender sender;
        public boolean silent;

        FeedbackStack(CommandSender sender, boolean silent) {
            this.sender = sender;
            this.silent = silent;
        }

        public CommandSender getBukkitSender() { return sender; }

        public FeedbackStack withSource(FeedbackOutput output) {
            return new FeedbackStack(output.getBukkitSender(), silent);
        }
    }

    @Test
    void useBeforeResolveIsRefused() {
        SpigotBridge unresolved = new SpigotBridge(getClass().getClassLoader(), new Object());
        assertThrows(IllegalStateException.class, () -> unresolved.prepare(dispatcher));
    }
}
