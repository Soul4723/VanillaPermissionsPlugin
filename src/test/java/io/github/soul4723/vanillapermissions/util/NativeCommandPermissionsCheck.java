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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.function.Predicate;

public final class NativeCommandPermissionsCheck {
    private static final String PERMISSION = "minecraft.command.gamemode.survival";

    private NativeCommandPermissionsCheck() {}

    public static void main(String[] args) throws Exception {
        if (!NativeCommandPermissionsCheck.class.desiredAssertionStatus()) {
            throw new IllegalStateException("Run with -ea");
        }

        UUID id = UUID.randomUUID();
        Tristate[] result = {Tristate.UNDEFINED};
        Player player = proxy(Player.class, (proxy, method, values) -> {
            if (method.getName().equals("getUniqueId")) return id;
            throw new AssertionError("Unexpected Player call: " + method);
        });
        QueryOptions options = proxy(QueryOptions.class, (proxy, method, values) -> {
            throw new AssertionError("Unexpected QueryOptions call: " + method);
        });
        CachedPermissionData permissionData = proxy(CachedPermissionData.class, (proxy, method, values) -> {
            assert method.getName().equals("queryPermission") && PERMISSION.equals(values[0]) : method;
            return new Result<Tristate, Node>() {
                @Override public Tristate result() { return result[0]; }
                @Override public Node node() { return null; }
            };
        });
        CachedDataManager cachedData = proxy(CachedDataManager.class, (proxy, method, values) -> {
            assert method.getName().equals("getPermissionData") && values[0] == options : method;
            return permissionData;
        });
        User user = proxy(User.class, (proxy, method, values) -> {
            assert method.getName().equals("getCachedData") : method;
            return cachedData;
        });
        UserManager users = proxy(UserManager.class, (proxy, method, values) -> {
            assert method.getName().equals("getUser") && id.equals(values[0]) : method;
            return user;
        });
        ContextManager context = proxy(ContextManager.class, (proxy, method, values) -> {
            assert method.getName().equals("getQueryOptions") && values[0] == player : method;
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
        Field hookField = PermissionManager.class.getDeclaredField("luckPermsHook");
        hookField.setAccessible(true);
        Object previousHook = hookField.get(null);
        hookField.set(null, hook);

        TestSource source = new TestSource(player);
        boolean[] vanilla = {true};
        int[] fallbackCalls = {0};
        Predicate<Object> fallback = value -> {
            assert value == source : "Vanilla fallback received wrong command source";
            fallbackCalls[0]++;
            return vanilla[0];
        };
        Method getBukkitSender = TestSource.class.getMethod("getBukkitSender");
        Class<?> predicateType = Class.forName(NativeCommandPermissions.class.getName() + "$PermissionPredicate");
        Constructor<?> constructor = predicateType.getDeclaredConstructor(String.class, Predicate.class, Method.class);
        constructor.setAccessible(true);
        @SuppressWarnings("unchecked")
        Predicate<Object> predicate = (Predicate<Object>) constructor.newInstance(
            "gamemode.survival", fallback, getBukkitSender);

        try {
            result[0] = Tristate.FALSE;
            assert !predicate.test(source) : "Explicit LuckPerms denial yielded to vanilla permission";
            assert fallbackCalls[0] == 0 : "Explicit denial invoked vanilla fallback";

            result[0] = Tristate.TRUE;
            vanilla[0] = false;
            assert predicate.test(source) : "Explicit LuckPerms grant yielded to vanilla denial";
            assert fallbackCalls[0] == 0 : "Explicit grant invoked vanilla fallback";

            result[0] = Tristate.UNDEFINED;
            vanilla[0] = true;
            assert predicate.test(source) : "Undefined LuckPerms permission lost vanilla grant";
            vanilla[0] = false;
            assert !predicate.test(source) : "Undefined LuckPerms permission bypassed vanilla denial";
            assert fallbackCalls[0] == 2 : "Vanilla fallback count mismatch";
        } finally {
            hookField.set(null, previousHook);
        }

        System.out.println("Native command permission tri-state and vanilla fallback checks passed");
    }

    public static final class TestSource {
        private final CommandSender sender;

        TestSource(CommandSender sender) { this.sender = sender; }

        public CommandSender getBukkitSender() { return sender; }
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
