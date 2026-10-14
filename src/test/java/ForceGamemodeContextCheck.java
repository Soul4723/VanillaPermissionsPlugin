import io.github.soul4723.vanillapermissions.listener.ForceGamemodeBypassListener;
import io.github.soul4723.vanillapermissions.util.LuckPermsHook;
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
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerGameModeChangeEvent;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Run with -ea and Maven's test classpath. Actual listener/manager; substituted LP/Bukkit APIs.
// ponytail: proxies verify API wiring/policy only; add real login/context checks before claiming runtime parity.
public class ForceGamemodeContextCheck {
    private static final String LEAF = "minecraft.bypass.force-gamemode";

    public static void main(String[] args) throws Exception {
        UUID id = UUID.randomUUID();
        boolean[] op = {false}, bukkitGrant = {false}, missingUser = {false}, contextFailure = {false};
        int[] contexts = {0}, checks = {0}, denials = {0};
        QueryOptions[] options = {proxy(QueryOptions.class, (p, m, a) -> { throw new AssertionError(m); })};
        Map<String, Tristate> nodes = new HashMap<>();
        Player player = proxy(Player.class, (p, m, a) -> switch (m.getName()) {
            case "getUniqueId" -> id;
            case "isOp" -> op[0];
            case "hasPermission" -> bukkitGrant[0];
            default -> throw new AssertionError("Unexpected player call: " + m);
        });
        CachedPermissionData data = proxy(CachedPermissionData.class, (p, m, a) -> {
            Tristate value = nodes.getOrDefault((String) a[0], Tristate.UNDEFINED);
            return switch (m.getName()) {
                case "checkPermission" -> { checks[0]++; yield value; }
                case "queryPermission" -> {
                    denials[0]++;
                    yield new Result<Tristate, Node>() {
                        public Tristate result() { return value; }
                        public Node node() { return null; }
                    };
                }
                default -> throw new AssertionError(m);
            };
        });
        CachedDataManager cached = proxy(CachedDataManager.class, (p, m, a) -> {
            assert m.getName().equals("getPermissionData") && a != null && a.length == 1
                    : "No-argument/static permission lookup";
            assert a[0] == options[0] : "Stale or mismatched query options";
            return data;
        });
        User user = proxy(User.class, (p, m, a) -> {
            assert m.getName().equals("getCachedData") : m;
            return cached;
        });
        UserManager users = proxy(UserManager.class, (p, m, a) -> {
            assert m.getName().equals("getUser") && id.equals(a[0]) : m;
            return missingUser[0] ? null : user;
        });
        ContextManager context = proxy(ContextManager.class, (p, m, a) -> {
            assert m.getName().equals("getQueryOptions") && a[0] == player : "Wrong context subject";
            if (contextFailure[0]) throw new IllegalStateException("Context provider unavailable");
            contexts[0]++;
            return options[0];
        });
        LuckPerms lp = proxy(LuckPerms.class, (p, m, a) -> switch (m.getName()) {
            case "getUserManager" -> users;
            case "getContextManager" -> context;
            default -> throw new AssertionError(m);
        });
        LuckPermsHook hook = new LuckPermsHook();
        set(LuckPermsHook.class, hook, "luckPerms", lp);
        set(LuckPermsHook.class, hook, "enabled", true);
        Field hookField = PermissionManager.class.getDeclaredField("luckPermsHook");
        hookField.setAccessible(true);
        Object previous = hookField.get(null);
        hookField.set(null, hook);
        try {
            ForceGamemodeBypassListener listener = new ForceGamemodeBypassListener();
            nodes.put(LEAF, Tristate.TRUE);
            assert cancelled(listener, player, PlayerGameModeChangeEvent.Cause.DEFAULT_GAMEMODE);
            assert PermissionManager.hasPermission(player, LEAF);
            int before = contexts[0];
            for (PlayerGameModeChangeEvent.Cause cause : new PlayerGameModeChangeEvent.Cause[]{
                    PlayerGameModeChangeEvent.Cause.COMMAND, PlayerGameModeChangeEvent.Cause.PLUGIN}) {
                assert !cancelled(listener, player, cause) : cause;
            }
            assert contexts[0] == before : "Unrelated cause queried permission";
            options[0] = proxy(QueryOptions.class, (p, m, a) -> { throw new AssertionError(m); });
            nodes.put(LEAF, Tristate.FALSE);
            nodes.put("minecraft.bypass", Tristate.TRUE);
            assert !PermissionManager.hasPermission(player, LEAF) : "Cached prior-context grant";
            assert !cancelled(listener, player, PlayerGameModeChangeEvent.Cause.DEFAULT_GAMEMODE)
                    : "Explicit leaf denial lost to parent";
            nodes.remove(LEAF);
            int parentContexts = contexts[0];
            assert cancelled(listener, player, PlayerGameModeChangeEvent.Cause.DEFAULT_GAMEMODE)
                    : "Exact parent grant lost";
            assert contexts[0] == parentContexts + 1 : "Repeated context lookup within one permission check";
            nodes.put("minecraft.bypass", Tristate.FALSE);
            nodes.put("minecraft", Tristate.TRUE);
            assert !PermissionManager.hasPermissionOrParent(player, LEAF) : "Parent denial ignored";
            nodes.put(LEAF, Tristate.TRUE);
            assert PermissionManager.hasPermissionOrParent(player, LEAF) : "Leaf grant lost to parent denial";
            nodes.clear();
            assert !cancelled(listener, player, PlayerGameModeChangeEvent.Cause.DEFAULT_GAMEMODE);
            assert contexts[0] > 0 && checks[0] > 0 && denials[0] > 0 : "LP paths untested";
            op[0] = true;
            nodes.put(LEAF, Tristate.FALSE);
            assert PermissionManager.hasPermissionOrParent(player, LEAF) : "Default OP bypass lost";
            op[0] = false;
            bukkitGrant[0] = true;
            missingUser[0] = true;
            assert !PermissionManager.hasPermissionOrParent(player, LEAF) : "Missing LP user changed fallback policy";
            missingUser[0] = false;
            contextFailure[0] = true;
            assert PermissionManager.hasPermissionOrParent(player, LEAF) : "Exception Bukkit fallback lost";
            hookField.set(null, null);
            assert cancelled(listener, player, PlayerGameModeChangeEvent.Cause.DEFAULT_GAMEMODE);
            bukkitGrant[0] = false;
            assert !cancelled(listener, player, PlayerGameModeChangeEvent.Cause.DEFAULT_GAMEMODE);
            System.out.println("Force-gamemode causes, player-context grants/denials, fresh lookups, parent/OP/fallback checks passed");
        } finally {
            hookField.set(null, previous);
        }
    }

    private static boolean cancelled(ForceGamemodeBypassListener listener, Player player,
                                     PlayerGameModeChangeEvent.Cause cause) {
        PlayerGameModeChangeEvent event = new PlayerGameModeChangeEvent(player, GameMode.SURVIVAL, cause, null);
        listener.onGameModeChange(event);
        return event.isCancelled();
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
