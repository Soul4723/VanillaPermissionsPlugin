package io.github.soul4723.vanillapermissions.spigot;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;

import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginNodesTest {

    /** Stands in for CraftBukkit's executor of plugin commands; only the simple name matters. */
    static final class BukkitCommandWrapper implements Command<Object> {
        @Override
        public int run(CommandContext<Object> context) {
            return 1;
        }
    }

    private final ClassLoader server = PluginNodesTest.class.getClassLoader();
    private final ClassLoader own = new URLClassLoader(new URL[0], server);
    private final ClassLoader otherPlugin = new URLClassLoader(new URL[0], server);

    @Test
    void loadersOfTheServerItsParentsAndThisPluginAreNotForeign() {
        assertFalse(PluginNodes.isForeign(server, server, own));
        assertFalse(PluginNodes.isForeign(server.getParent(), server, own));
        assertFalse(PluginNodes.isForeign(null, server, own));
        assertFalse(PluginNodes.isForeign(own, server, own));
        assertTrue(PluginNodes.isForeign(otherPlugin, server, own));
    }

    @Test
    void vanillaShapedCommandsAreKept() {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        CommandNode<Object> gamemode = dispatcher.register(literal("gamemode")
                .requires(source -> true)
                .then(literal("creative").executes(context -> 1)));
        dispatcher.register(literal("gm").redirect(gamemode));
        assertFalse(PluginNodes.isPluginCommand(gamemode, server, own));
        assertFalse(PluginNodes.isPluginCommand(dispatcher.getRoot().getChild("gm"), server, own));
    }

    @Test
    void commodoreStyleCommandsAndTheirAliasesAreSkipped() {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        CommandNode<Object> luckperms = dispatcher.register(literal("luckperms")
                .executes(new BukkitCommandWrapper())
                .then(literal("info").executes(new BukkitCommandWrapper())));
        dispatcher.register(literal("lp").redirect(luckperms));
        assertTrue(PluginNodes.isPluginCommand(luckperms, server, own));
        assertTrue(PluginNodes.isPluginCommand(dispatcher.getRoot().getChild("lp"), server, own));
    }

    @Test
    void commandsWhoseClassesComeFromAnotherPluginAreSkipped() {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        CommandNode<Object> root = dispatcher.register(literal("custom").executes(context -> 1));
        // The executor is a test-loader lambda: foreign when the "server" is an unrelated loader.
        ClassLoader unrelatedServer = new URLClassLoader(new URL[0], null);
        assertTrue(PluginNodes.isPluginCommand(root, unrelatedServer, own));
        assertFalse(PluginNodes.isPluginCommand(root, unrelatedServer, server));
    }
}
