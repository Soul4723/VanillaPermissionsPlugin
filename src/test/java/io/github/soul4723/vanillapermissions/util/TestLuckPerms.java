package io.github.soul4723.vanillapermissions.util;

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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/** Minimal LuckPerms stand-in for listener tests: every player shares one node map. */
public final class TestLuckPerms implements AutoCloseable {
    public final Map<String, Tristate> nodes = new HashMap<>();
    private final Field hookField;
    private final Object previousHook;

    public TestLuckPerms() throws ReflectiveOperationException {
        QueryOptions options = proxy(QueryOptions.class, (p, m, a) -> { throw new AssertionError(m); });
        CachedPermissionData data = proxy(CachedPermissionData.class, (p, m, a) -> {
            Tristate value = nodes.getOrDefault((String) a[0], Tristate.UNDEFINED);
            return switch (m.getName()) {
                case "checkPermission" -> value;
                case "queryPermission" -> new Result<Tristate, Node>() {
                    @Override public Tristate result() { return value; }
                    @Override public Node node() { return null; }
                };
                default -> throw new AssertionError(m);
            };
        });
        CachedDataManager cached = proxy(CachedDataManager.class, (p, m, a) -> data);
        User user = proxy(User.class, (p, m, a) -> cached);
        UserManager users = proxy(UserManager.class, (p, m, a) -> user);
        ContextManager context = proxy(ContextManager.class, (p, m, a) -> options);
        LuckPerms luckPerms = proxy(LuckPerms.class, (p, m, a) -> switch (m.getName()) {
            case "getUserManager" -> users;
            case "getContextManager" -> context;
            default -> throw new AssertionError(m);
        });
        LuckPermsHook hook = new LuckPermsHook() {
            @Override public boolean isEnabled() { return true; }
            @Override public LuckPerms getLuckPerms() { return luckPerms; }
        };
        hookField = PermissionManager.class.getDeclaredField("luckPermsHook");
        hookField.setAccessible(true);
        previousHook = hookField.get(null);
        hookField.set(null, hook);
    }

    @Override
    public void close() throws IllegalAccessException {
        hookField.set(null, previousHook);
    }

    public static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
