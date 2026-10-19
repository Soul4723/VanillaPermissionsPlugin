package io.github.soul4723.vanillapermissions.util;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which native nodes get checks, and the tri-state behaviour of the predicate installed on them. */
class NativeCommandPermissionsTest {
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
        hookField = PermissionManager.class.getDeclaredField("luckPermsHook");
        hookField.setAccessible(true);
        previousHook = hookField.get(null);
        hookField.set(null, hook);

        source = new TestSource(player);
        Predicate<Object> fallback = value -> {
            assertSame(source, value, "Vanilla fallback received wrong command source");
            fallbackCalls++;
            return vanilla;
        };
        Method getBukkitSender = TestSource.class.getMethod("getBukkitSender");
        Class<?> predicateType = Class.forName(NativeCommandPermissions.class.getName() + "$PermissionPredicate");
        Constructor<?> constructor = predicateType.getDeclaredConstructor(String.class, Predicate.class, Method.class);
        constructor.setAccessible(true);
        @SuppressWarnings("unchecked")
        Predicate<Object> created = (Predicate<Object>) constructor.newInstance("gamemode.survival", fallback, getBukkitSender);
        predicate = created;
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
        assertTrue(NativeGameModeTree.removeNode(root, gamemode));
        LiteralCommandNode<Object> pluginGamemode = dispatcher.register(LiteralArgumentBuilder.literal("gamemode"));
        LiteralCommandNode<Object> lp = dispatcher.register(LiteralArgumentBuilder.literal("lp"));
        LiteralCommandNode<Object> namespacedLp = dispatcher.register(LiteralArgumentBuilder.literal("luckperms:lp").redirect(lp));

        Map<CommandNode<?>, String> roots = new IdentityHashMap<>();
        for (NativeCommandPermissions.VanillaRoot vanilla : NativeCommandPermissions.vanillaRoots(root)) {
            assertNull(roots.put(vanilla.node(), vanilla.path()), "Node collected twice: " + vanilla.path());
        }

        assertEquals("time", roots.get(time));
        assertEquals("time", roots.get(root.getChild("minecraft:time")), "minecraft: alias bypasses the command node");
        assertEquals("tp", roots.get(tp));
        assertEquals("tp", roots.get(root.getChild("minecraft:tp")));
        assertEquals("teleport", roots.get(teleport));
        assertEquals("gamemode", roots.get(gamemode), "Vanilla command replaced by a plugin lost its checks");
        assertSame(gamemode, NativeCommandPermissions.vanillaCommand(dispatcher, "gamemode"));
        for (CommandNode<?> plugin : List.of(pluginGamemode, lp, namespacedLp)) {
            assertFalse(roots.containsKey(plugin), "Plugin command got vanilla nodes: " + plugin.getName());
        }
    }

    public static final class TestSource {
        private final CommandSender sender;

        TestSource(CommandSender sender) { this.sender = sender; }

        public CommandSender getBukkitSender() { return sender; }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
