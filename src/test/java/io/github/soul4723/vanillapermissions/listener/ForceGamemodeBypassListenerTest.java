package io.github.soul4723.vanillapermissions.listener;

import io.github.soul4723.vanillapermissions.util.BukkitPermissionProvider;
import io.github.soul4723.vanillapermissions.util.LuckPermsHook;
import io.github.soul4723.vanillapermissions.util.LuckPermsPermissionProvider;
import io.github.soul4723.vanillapermissions.util.PermissionManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedDataManager;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.context.ContextManager;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.util.Tristate;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real listener and PermissionManager against substituted LuckPerms/Bukkit APIs.
 * Proxies verify API wiring and policy only, not live login or context behaviour.
 */
class ForceGamemodeBypassListenerTest {
    private static final String LEAF = "minecraft.bypass.force-gamemode";

    private final UUID id = UUID.randomUUID();
    private final Map<String, Tristate> nodes = new HashMap<>();
    private boolean op, bukkitGrant, missingUser, contextFailure;
    private int contexts, checks;
    private QueryOptions options = freshOptions();
    private Player player;
    private Field hookField;
    private Object previousHook;
    private final ForceGamemodeBypassListener listener = new ForceGamemodeBypassListener();

    @BeforeEach
    void installLuckPerms() throws Exception {
        player = proxy(Player.class, (p, m, a) -> switch (m.getName()) {
            case "getUniqueId" -> id;
            case "isOp" -> op;
            case "hasPermission" -> bukkitGrant;
            default -> throw new AssertionError("Unexpected player call: " + m);
        });
        CachedPermissionData data = proxy(CachedPermissionData.class, (p, m, a) -> {
            assertEquals("checkPermission", m.getName());
            checks++;
            return nodes.getOrDefault((String) a[0], Tristate.UNDEFINED);
        });
        CachedDataManager cached = proxy(CachedDataManager.class, (p, m, a) -> {
            assertTrue(m.getName().equals("getPermissionData") && a != null && a.length == 1,
                    "No-argument/static permission lookup");
            assertSame(options, a[0], "Stale or mismatched query options");
            return data;
        });
        User user = proxy(User.class, (p, m, a) -> {
            assertEquals("getCachedData", m.getName());
            return cached;
        });
        UserManager users = proxy(UserManager.class, (p, m, a) -> {
            assertEquals("getUser", m.getName());
            assertEquals(id, a[0]);
            return missingUser ? null : user;
        });
        ContextManager context = proxy(ContextManager.class, (p, m, a) -> {
            assertEquals("getQueryOptions", m.getName());
            assertSame(player, a[0], "Wrong context subject");
            if (contextFailure) throw new IllegalStateException("Context provider unavailable");
            contexts++;
            return options;
        });
        LuckPerms luckPerms = proxy(LuckPerms.class, (p, m, a) -> switch (m.getName()) {
            case "getUserManager" -> users;
            case "getContextManager" -> context;
            default -> throw new AssertionError(m);
        });
        LuckPermsHook hook = new LuckPermsHook();
        set(hook, "luckPerms", luckPerms);
        set(hook, "enabled", true);
        hookField = PermissionManager.class.getDeclaredField("provider");
        hookField.setAccessible(true);
        previousHook = hookField.get(null);
        hookField.set(null, new LuckPermsPermissionProvider(hook, null));
    }

    @AfterEach
    void restoreLuckPerms() throws Exception {
        hookField.set(null, previousHook);
    }

    @Test
    void grantCancelsOnlyDefaultGamemodeChanges() {
        nodes.put(LEAF, Tristate.TRUE);
        assertTrue(cancelled(PlayerGameModeChangeEvent.Cause.DEFAULT_GAMEMODE));
        assertTrue(PermissionManager.hasPermission(player, LEAF));
        int before = contexts;
        for (PlayerGameModeChangeEvent.Cause cause : new PlayerGameModeChangeEvent.Cause[]{
                PlayerGameModeChangeEvent.Cause.COMMAND, PlayerGameModeChangeEvent.Cause.PLUGIN}) {
            assertFalse(cancelled(cause), cause.name());
        }
        assertEquals(before, contexts, "Unrelated cause queried permission");
    }

    @Test
    void contextIsQueriedFreshForEachCheck() {
        nodes.put(LEAF, Tristate.TRUE);
        assertTrue(PermissionManager.hasPermission(player, LEAF));
        options = freshOptions();
        nodes.put(LEAF, Tristate.FALSE);
        nodes.put("minecraft.bypass", Tristate.TRUE);
        assertFalse(PermissionManager.hasPermission(player, LEAF), "Cached prior-context grant");
        assertFalse(cancelled(PlayerGameModeChangeEvent.Cause.DEFAULT_GAMEMODE), "Explicit leaf denial lost to parent");
    }

    @Test
    void parentNodesDoNotImplyLeaf() {
        nodes.put("minecraft.bypass", Tristate.TRUE);
        int parentContexts = contexts;
        assertFalse(cancelled(PlayerGameModeChangeEvent.Cause.DEFAULT_GAMEMODE), "Bare parent grant allowed child");
        assertEquals(parentContexts + 1, contexts, "Repeated context lookup within one permission check");
        nodes.put("minecraft.bypass", Tristate.FALSE);
        nodes.put("minecraft", Tristate.TRUE);
        assertFalse(PermissionManager.hasPermission(player, LEAF), "Bare ancestor grant allowed child");
        nodes.put(LEAF, Tristate.TRUE);
        assertTrue(PermissionManager.hasPermission(player, LEAF), "Leaf grant lost to parent denial");
        nodes.clear();
        assertFalse(cancelled(PlayerGameModeChangeEvent.Cause.DEFAULT_GAMEMODE));
        assertTrue(contexts > 0 && checks > 0, "LuckPerms checks were not exercised");
    }

    @Test
    void luckPermsDenialBeatsOperatorStatus() {
        op = true;
        nodes.put(LEAF, Tristate.FALSE);
        assertFalse(PermissionManager.hasPermission(player, LEAF), "Explicit LP denial lost to OP status");
    }

    @Test
    void missingUserDeniesWithoutBukkitFallback() {
        bukkitGrant = true;
        missingUser = true;
        assertFalse(PermissionManager.hasPermission(player, LEAF), "Missing LP user changed fallback policy");
    }

    @Test
    void luckPermsFailureFallsBackToBukkit() throws Exception {
        bukkitGrant = true;
        contextFailure = true;
        assertTrue(PermissionManager.hasPermission(player, LEAF), "Exception Bukkit fallback lost");
        hookField.set(null, new BukkitPermissionProvider(null, null, null));
        assertTrue(cancelled(PlayerGameModeChangeEvent.Cause.DEFAULT_GAMEMODE), "Bukkit grant ignored without LuckPerms");
        bukkitGrant = false;
        assertFalse(cancelled(PlayerGameModeChangeEvent.Cause.DEFAULT_GAMEMODE), "Bukkit denial ignored without LuckPerms");
    }

    private boolean cancelled(PlayerGameModeChangeEvent.Cause cause) {
        PlayerGameModeChangeEvent event = new PlayerGameModeChangeEvent(player, GameMode.SURVIVAL, cause, null);
        listener.onGameModeChange(event);
        return event.isCancelled();
    }

    private static QueryOptions freshOptions() {
        return proxy(QueryOptions.class, (p, m, a) -> { throw new AssertionError(m); });
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = LuckPermsHook.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
