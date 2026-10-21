package io.github.soul4723.vanillapermissions.spigot;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AliasPermissionsTest {

    /** CraftBukkit's wrapper as far as the adapter sees it: a Bukkit command with a public {@code vanillaCommand}. */
    public static final class FakeVanillaCommandWrapper extends BukkitCommand {
        public final CommandNode<Object> vanillaCommand;

        FakeVanillaCommandWrapper(CommandNode<Object> node) {
            super(node.getName());
            this.vanillaCommand = node;
            // CraftBukkit's VanillaCommandWrapper.getPermission(CommandNode) on 1.13.2 - 1.20.4.
            setPermission("minecraft.command." + (node.getRedirect() == null ? node.getName() : node.getRedirect().getName()));
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            return true;
        }
    }

    /** A Bukkit command of another kind, e.g. a plugin's. */
    public static final class OtherCommand extends BukkitCommand {
        OtherCommand(String name) {
            super(name);
            setPermission("other.permission");
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            return true;
        }
    }

    /** Stands in for CraftBukkit's executor of plugin commands; only the simple name matters. */
    static final class BukkitCommandWrapper implements com.mojang.brigadier.Command<Object> {
        @Override
        public int run(CommandContext<Object> context) {
            return 1;
        }
    }

    private final ClassLoader loader = AliasPermissionsTest.class.getClassLoader();
    private final CommandDispatcher<Object> vanilla = new CommandDispatcher<>();
    private final LiteralCommandNode<Object> teleport = vanilla.register(literal("teleport").requires(source -> true).executes(context -> 1));
    private final LiteralCommandNode<Object> msg = vanilla.register(literal("msg").executes(context -> 1));
    private final LiteralCommandNode<Object> experience = vanilla.register(literal("experience").executes(context -> 1));
    private final FakeVanillaCommandWrapper teleportWrapper = new FakeVanillaCommandWrapper(teleport);
    private final FakeVanillaCommandWrapper tp = wrapper(literal("tp").redirect(teleport));
    private final FakeVanillaCommandWrapper tell = wrapper(literal("tell").redirect(msg));
    private final FakeVanillaCommandWrapper w = wrapper(literal("w").redirect(msg));
    private final FakeVanillaCommandWrapper xp = wrapper(literal("xp").redirect(experience));

    private FakeVanillaCommandWrapper wrapper(com.mojang.brigadier.builder.LiteralArgumentBuilder<Object> builder) {
        return new FakeVanillaCommandWrapper(vanilla.register(builder));
    }

    private List<String> apply(AliasPermissions aliases, Command... commands) throws ReflectiveOperationException {
        return aliases.apply(Arrays.asList(commands), FakeVanillaCommandWrapper.class, vanilla.getRoot(), loader, loader);
    }

    private static CommandSender senderWith(String... granted) {
        Set<String> nodes = new HashSet<>(Arrays.asList(granted));
        return (CommandSender) Proxy.newProxyInstance(AliasPermissionsTest.class.getClassLoader(), new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("hasPermission") && args[0] instanceof String) return nodes.contains(args[0]);
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void redirectAliasesAcceptTheirOwnNodeOrTheTargetsNode() throws Exception {
        AliasPermissions aliases = new AliasPermissions();

        // tp is in the map as "tp" and "minecraft:tp"; plain commands and other kinds stay as they are.
        List<String> changed = apply(aliases, teleportWrapper, tp, tp, tell, w, xp, new OtherCommand("tp"));

        assertEquals(Arrays.asList("tell", "tp", "w", "xp"), changed);
        assertEquals("minecraft.command.tp;minecraft.command.teleport", tp.getPermission());
        assertEquals("minecraft.command.tell;minecraft.command.msg", tell.getPermission());
        assertEquals("minecraft.command.w;minecraft.command.msg", w.getPermission());
        assertEquals("minecraft.command.xp;minecraft.command.experience", xp.getPermission());
        assertEquals("minecraft.command.teleport", teleportWrapper.getPermission());
    }

    @Test
    void bukkitsPermissionTestPassesWithEitherNode() throws Exception {
        apply(new AliasPermissions(), tp, tell);

        assertTrue(tp.testPermissionSilent(senderWith("minecraft.command.tp")));
        assertTrue(tp.testPermissionSilent(senderWith("minecraft.command.teleport")));
        assertFalse(tp.testPermissionSilent(senderWith("minecraft.command.tell")));
        assertTrue(tell.testPermissionSilent(senderWith("minecraft.command.tell")));
        assertTrue(tell.testPermissionSilent(senderWith("minecraft.command.msg")));
        assertFalse(tell.testPermissionSilent(senderWith()));
    }

    @Test
    void restorePutsCraftBukkitsPermissionBack() throws Exception {
        AliasPermissions aliases = new AliasPermissions();
        apply(aliases, tp, tell);
        tell.setPermission("changed.by.someone.else");

        aliases.restore();

        assertEquals("minecraft.command.teleport", tp.getPermission());
        assertEquals("changed.by.someone.else", tell.getPermission());
        aliases.restore();
        assertEquals("minecraft.command.teleport", tp.getPermission());
    }

    @Test
    void aPermissionAnotherPluginSetIsLeftAlone() throws Exception {
        // CommandAPI's fixPermissions sets its own node on the wrappers of its commands.
        tp.setPermission("other.plugin.tp");

        assertEquals(Collections.singletonList("tell"), apply(new AliasPermissions(), tp, tell));
        assertEquals("other.plugin.tp", tp.getPermission());
    }

    @Test
    void pluginCommandsInTheVanillaDispatcherAreLeftAlone() throws Exception {
        // lucko/commodore's /lp alias for LuckPerms' tree, whose executors are CraftBukkit's BukkitCommandWrapper.
        LiteralCommandNode<Object> luckperms = vanilla.register(literal("luckperms").executes(new BukkitCommandWrapper()));
        FakeVanillaCommandWrapper lp = wrapper(literal("lp").redirect(luckperms));

        assertTrue(apply(new AliasPermissions(), lp).isEmpty());
        assertEquals("minecraft.command.luckperms", lp.getPermission());
    }

    @Test
    void nodesOutsideTheVanillaRootsAreLeftAlone() throws Exception {
        CommandDispatcher<Object> elsewhere = new CommandDispatcher<>();
        FakeVanillaCommandWrapper detached = new FakeVanillaCommandWrapper(elsewhere.register(literal("tp2").redirect(teleport)));
        LiteralCommandNode<Object> foreignTarget = elsewhere.register(literal("target"));
        FakeVanillaCommandWrapper toForeign = wrapper(literal("alias").redirect(foreignTarget));

        assertTrue(apply(new AliasPermissions(), detached, toForeign).isEmpty());
        assertEquals("minecraft.command.teleport", detached.getPermission());
        assertEquals("minecraft.command.target", toForeign.getPermission());
    }

    @Test
    void aCommandMapWithoutWrappersIsAnError() {
        assertThrows(IllegalStateException.class, () -> apply(new AliasPermissions(), new OtherCommand("tp")));
    }

    @Test
    void aWrapperTypeWithoutTheVanillaNodeFieldIsAnError() {
        List<Command> commands = new ArrayList<>();
        commands.add(new OtherCommand("tp"));
        assertThrows(NoSuchFieldException.class,
                () -> new AliasPermissions().apply(commands, OtherCommand.class, vanilla.getRoot(), loader, loader));
    }
}
