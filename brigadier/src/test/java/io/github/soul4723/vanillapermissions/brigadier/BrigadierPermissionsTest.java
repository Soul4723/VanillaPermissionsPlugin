package io.github.soul4723.vanillapermissions.brigadier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import io.github.soul4723.vanillapermissions.selector.SelectorRules;
import io.github.soul4723.vanillapermissions.util.LuckPermsHook;
import io.github.soul4723.vanillapermissions.util.LuckPermsPermissionProvider;
import io.github.soul4723.vanillapermissions.util.PermissionManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedDataManager;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.cacheddata.Result;
import net.luckperms.api.context.ContextManager;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.util.Tristate;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static io.github.soul4723.vanillapermissions.util.TestLuckPerms.proxy;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which native nodes get checks, and the tri-state behaviour of the predicate installed on them. */
class BrigadierPermissionsTest {
    private static final String PERMISSION = "minecraft.command.gamemode.survival";

    private final UUID id = UUID.randomUUID();
    private Tristate result = Tristate.UNDEFINED;
    private boolean vanilla = true;
    private int fallbackCalls;
    private TestSource source;
    private Predicate<Object> predicate;
    private Field hookField;
    private Object previousHook;

    @BeforeEach
    void installPredicate() throws Exception {
        Player player = proxy(Player.class, (proxy, method, values) -> {
            if (method.getName().equals("getUniqueId")) return id;
            throw new AssertionError("Unexpected Player call: " + method);
        });
        QueryOptions options = proxy(QueryOptions.class, (proxy, method, values) -> {
            throw new AssertionError("Unexpected QueryOptions call: " + method);
        });
        CachedPermissionData permissionData = proxy(CachedPermissionData.class, (proxy, method, values) -> {
            assertEquals("queryPermission", method.getName());
            assertEquals(PERMISSION, values[0]);
            return new Result<Tristate, Node>() {
                @Override public Tristate result() { return result; }
                @Override public Node node() { return null; }
            };
        });
        CachedDataManager cachedData = proxy(CachedDataManager.class, (proxy, method, values) -> {
            assertEquals("getPermissionData", method.getName());
            assertSame(options, values[0]);
            return permissionData;
        });
        User user = proxy(User.class, (proxy, method, values) -> {
            assertEquals("getCachedData", method.getName());
            return cachedData;
        });
        UserManager users = proxy(UserManager.class, (proxy, method, values) -> {
            assertEquals("getUser", method.getName());
            assertEquals(id, values[0]);
            return user;
        });
        ContextManager context = proxy(ContextManager.class, (proxy, method, values) -> {
            assertEquals("getQueryOptions", method.getName());
            assertSame(player, values[0]);
            return options;
        });
        LuckPerms luckPerms = proxy(LuckPerms.class, (proxy, method, values) -> switch (method.getName()) {
            case "getUserManager" -> users;
            case "getContextManager" -> context;
            default -> throw new AssertionError("Unexpected LuckPerms call: " + method);
        });
        LuckPermsHook hook = new LuckPermsHook() {
            @Override public boolean isEnabled() { return true; }
            @Override public LuckPerms getLuckPerms() { return luckPerms; }
        };
        hookField = PermissionManager.class.getDeclaredField("provider");
        hookField.setAccessible(true);
        previousHook = hookField.get(null);
        hookField.set(null, new LuckPermsPermissionProvider(hook, null));

        source = new TestSource(player);
        Predicate<Object> fallback = value -> {
            assertSame(source, value, "Vanilla fallback received wrong command source");
            fallbackCalls++;
            return vanilla;
        };
        TestBridge bridge = new TestBridge() {
            @Override
            public CommandSender sender(Object value) {
                return ((TestSource) value).getBukkitSender();
            }
        };
        predicate = new BrigadierPermissions(null, bridge, null).permissionPredicate("gamemode.survival", fallback);
    }

    @AfterEach
    void restoreHook() throws Exception {
        hookField.set(null, previousHook);
    }

    @Test
    void explicitDenialOverridesVanilla() {
        result = Tristate.FALSE;
        assertFalse(predicate.test(source), "Explicit LuckPerms denial yielded to vanilla permission");
        assertEquals(0, fallbackCalls, "Explicit denial invoked vanilla fallback");
    }

    @Test
    void explicitGrantOverridesVanilla() {
        result = Tristate.TRUE;
        vanilla = false;
        assertTrue(predicate.test(source), "Explicit LuckPerms grant yielded to vanilla denial");
        assertEquals(0, fallbackCalls, "Explicit grant invoked vanilla fallback");
    }

    @Test
    void undefinedUsesVanillaRequirement() {
        result = Tristate.UNDEFINED;
        vanilla = true;
        assertTrue(predicate.test(source), "Undefined LuckPerms permission lost vanilla grant");
        vanilla = false;
        assertFalse(predicate.test(source), "Undefined LuckPerms permission bypassed vanilla denial");
        assertEquals(2, fallbackCalls, "Vanilla fallback count mismatch");
    }

    @Test
    void onlyVanillaCommandsAreChecked() throws ReflectiveOperationException {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        LiteralCommandNode<Object> time = dispatcher.register(LiteralArgumentBuilder.literal("time")
            .then(LiteralArgumentBuilder.literal("set")));
        LiteralCommandNode<Object> teleport = dispatcher.register(LiteralArgumentBuilder.literal("teleport"));
        LiteralCommandNode<Object> tp = dispatcher.register(LiteralArgumentBuilder.literal("tp").redirect(teleport));
        LiteralCommandNode<Object> gamemode = dispatcher.register(LiteralArgumentBuilder.literal("gamemode"));
        // Paper adds a minecraft: alias for every vanilla command before plugins register theirs.
        for (CommandNode<Object> node : List.copyOf(dispatcher.getRoot().getChildren())) {
            CommandNode<Object> target = node.getRedirect() == null ? node : node.getRedirect();
            dispatcher.register(LiteralArgumentBuilder.literal("minecraft:" + node.getName()).redirect(target));
        }
        // A plugin takes over /gamemode and adds its own commands.
        RootCommandNode<Object> root = dispatcher.getRoot();
        assertTrue(VanillaTree.removeNode(root, gamemode));
        LiteralCommandNode<Object> pluginGamemode = dispatcher.register(LiteralArgumentBuilder.literal("gamemode"));
        LiteralCommandNode<Object> lp = dispatcher.register(LiteralArgumentBuilder.literal("lp"));
        LiteralCommandNode<Object> namespacedLp = dispatcher.register(LiteralArgumentBuilder.literal("luckperms:lp").redirect(lp));

        Map<CommandNode<?>, String> roots = new IdentityHashMap<>();
        for (VanillaTree.VanillaRoot vanilla : VanillaTree.fromMinecraftAliases(root)) {
            assertNull(roots.put(vanilla.node(), vanilla.path()), "Node collected twice: " + vanilla.path());
        }

        assertEquals("time", roots.get(time));
        assertEquals("time", roots.get(root.getChild("minecraft:time")), "minecraft: alias bypasses the command node");
        assertEquals("tp", roots.get(tp));
        assertEquals("tp", roots.get(root.getChild("minecraft:tp")));
        assertEquals("teleport", roots.get(teleport));
        assertEquals("gamemode", roots.get(gamemode), "Vanilla command replaced by a plugin lost its checks");
        assertEquals(List.of(gamemode), VanillaTree.vanillaCommands(dispatcher, "gamemode"));
        for (CommandNode<?> plugin : List.of(pluginGamemode, lp, namespacedLp)) {
            assertFalse(roots.containsKey(plugin), "Plugin command got vanilla nodes: " + plugin.getName());
        }
    }

    /** Paper 1.21.4+ registers minecraft: aliases as copies of the command node that share its children. */
    @Test
    void copiedMinecraftAliasesAreChecked() throws ReflectiveOperationException {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        LiteralCommandNode<Object> time = dispatcher.register(literal("time").then(literal("set")));
        LiteralCommandNode<Object> seed = dispatcher.register(literal("seed").executes(c -> 1));
        LiteralCommandNode<Object> teleport = dispatcher.register(literal("teleport"));
        LiteralCommandNode<Object> tp = dispatcher.register(literal("tp").redirect(teleport));
        LiteralCommandNode<Object> gamemode = dispatcher.register(literal("gamemode").then(literal("creative")));
        RootCommandNode<Object> root = dispatcher.getRoot();
        for (CommandNode<Object> node : List.copyOf(root.getChildren())) {
            root.addChild(copy("minecraft:" + node.getName(), node));
        }
        assertTrue(VanillaTree.removeNode(root, gamemode));
        LiteralCommandNode<Object> pluginGamemode = dispatcher.register(literal("gamemode").then(literal("creative")));

        Map<CommandNode<?>, String> roots = new IdentityHashMap<>();
        for (VanillaTree.VanillaRoot vanilla : VanillaTree.fromMinecraftAliases(root)) {
            assertNull(roots.put(vanilla.node(), vanilla.path()), "Node collected twice: " + vanilla.path());
        }

        assertEquals("time", roots.get(time));
        assertEquals("time", roots.get(root.getChild("minecraft:time")), "Copied minecraft: alias bypasses the command node");
        assertEquals("seed", roots.get(seed));
        assertEquals("seed", roots.get(root.getChild("minecraft:seed")));
        assertEquals("tp", roots.get(tp));
        assertEquals("tp", roots.get(root.getChild("minecraft:tp")));
        assertEquals("teleport", roots.get(teleport));
        assertEquals("gamemode", roots.get(root.getChild("minecraft:gamemode")));
        assertFalse(roots.containsKey(pluginGamemode), "Plugin command that shadows a vanilla one got vanilla nodes");

        assertEquals(List.of(time, root.getChild("minecraft:time")), VanillaTree.vanillaCommands(dispatcher, "time"));
        assertEquals(List.of(root.getChild("minecraft:gamemode")), VanillaTree.vanillaCommands(dispatcher, "gamemode"));
        assertEquals(List.of(teleport), VanillaTree.vanillaCommands(dispatcher, "tp"));
    }

    @Test
    void copiedAliasPathDropsTheNamespace() {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        LiteralCommandNode<Object> gamemode = dispatcher.register(literal("gamemode")
            .then(literal("creative").then(argument("target", word()).executes(c -> 1))));
        dispatcher.getRoot().addChild(copy("minecraft:gamemode", gamemode));

        assertEquals(List.of("gamemode", "creative", "target"), leafPath(dispatcher, "minecraft:gamemode creative Bob"));
        assertEquals("gamemode.target.creative.target",
            SelectorRules.selectorPath(leafPath(dispatcher, "minecraft:gamemode creative Bob"), "target"));
    }

    /** As PaperBrigadier.copyLiteral: same requirement, executor and redirect, and the same child nodes. */
    private static LiteralCommandNode<Object> copy(String name, CommandNode<Object> source) {
        LiteralArgumentBuilder<Object> builder = LiteralArgumentBuilder.literal(name)
            .requires(source.getRequirement())
            .forward(source.getRedirect(), source.getRedirectModifier(), source.isFork());
        if (source.getCommand() != null) builder.executes(source.getCommand());
        for (CommandNode<Object> child : source.getChildren()) builder.then(child);
        return builder.build();
    }

    @Test
    void parsedPathIncludesTheCommandAfterRedirects() throws Exception {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        LiteralCommandNode<Object> gamemode = dispatcher.register(literal("gamemode")
            .then(literal("creative").then(argument("target", word()).executes(c -> 1))));
        dispatcher.register(literal("minecraft:gamemode").redirect(gamemode));
        LiteralCommandNode<Object> teleport = dispatcher.register(literal("teleport")
            .then(argument("destination", word()).executes(c -> 1)));
        dispatcher.register(literal("tp").redirect(teleport));
        LiteralCommandNode<Object> execute = dispatcher.register(literal("execute")
            .then(literal("run").redirect(dispatcher.getRoot())));
        dispatcher.register(literal("execute")
            .then(literal("as").then(argument("targets", word()).redirect(execute))));

        List<String> gamemodePath = List.of("gamemode", "creative", "target");
        assertEquals(gamemodePath, leafPath(dispatcher, "gamemode creative Bob"));
        assertEquals(gamemodePath, leafPath(dispatcher, "minecraft:gamemode creative Bob"));
        assertEquals(List.of("teleport", "destination"), leafPath(dispatcher, "tp Bob"));
        assertEquals(List.of("teleport", "destination"), leafPath(dispatcher, "teleport Bob"));
        assertEquals(gamemodePath, leafPath(dispatcher, "execute run gamemode creative Bob"));
        assertEquals(gamemodePath, leafPath(dispatcher, "execute as x run minecraft:gamemode creative Bob"));
        assertEquals("gamemode.target.creative.target",
            SelectorRules.selectorPath(leafPath(dispatcher, "minecraft:gamemode creative Bob"), "target"));
    }

    private static List<String> leafPath(CommandDispatcher<Object> dispatcher, String input) {
        CommandContext<Object> leaf = dispatcher.parse(input, new Object()).getContext().build(input);
        while (leaf.getChild() != null) leaf = leaf.getChild();
        return VanillaTree.parsedPath(leaf);
    }

    public static final class TestSource {
        private final CommandSender sender;

        TestSource(CommandSender sender) { this.sender = sender; }

        public CommandSender getBukkitSender() { return sender; }
    }
}
