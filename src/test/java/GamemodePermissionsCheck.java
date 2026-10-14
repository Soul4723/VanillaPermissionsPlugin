import dev.jorel.commandapi.executors.CommandArguments;
import dev.jorel.commandapi.wrappers.NativeProxyCommandSender;
import io.github.soul4723.vanillapermissions.command.GameModeCommandHandler;
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
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Run with -ea and Maven's test classpath. Exercises the actual permission and execution helpers.
public class GamemodePermissionsCheck {
    private static final String BASE = "minecraft.command.gamemode";

    private static final class PlayerFixture {
        final UUID id = UUID.randomUUID();
        final List<String> messages = new ArrayList<>();
        final Map<GameMode, Integer> changes = new HashMap<>();
        final Player player = proxy(Player.class, (self, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "isOp" -> false;
            case "hasPermission" -> false;
            case "sendMessage" -> { messages.add((String) args[0]); yield null; }
            case "setGameMode" -> { changes.merge((GameMode) args[0], 1, Integer::sum); yield null; }
            case "equals" -> self == args[0];
            case "hashCode" -> System.identityHashCode(self);
            case "toString" -> "Player[" + id + "]";
            default -> throw new AssertionError("Unexpected player call: " + method);
        });
    }

    public static void main(String[] ignored) throws Exception {
        Map<UUID, Map<String, Tristate>> permissions = new HashMap<>();
        QueryOptions options = proxy(QueryOptions.class, (p, m, a) -> { throw new AssertionError(m); });
        UserManager users = proxy(UserManager.class, (p, m, a) -> {
            assert m.getName().equals("getUser");
            UUID id = (UUID) a[0];
            User user = proxy(User.class, (userProxy, method, args) -> {
                assert method.getName().equals("getCachedData");
                return proxy(CachedDataManager.class, (cachedProxy, cachedMethod, cachedArgs) -> {
                    assert cachedMethod.getName().equals("getPermissionData") && cachedArgs[0] == options;
                    return proxy(CachedPermissionData.class, (permissionProxy, permissionMethod, permissionArgs) -> {
                        Tristate value = permissions.getOrDefault(id, Map.of())
                                .getOrDefault((String) permissionArgs[0], Tristate.UNDEFINED);
                        return switch (permissionMethod.getName()) {
                            case "checkPermission" -> value;
                            case "queryPermission" -> new Result<Tristate, Node>() {
                                public Tristate result() { return value; }
                                public Node node() { return null; }
                            };
                            default -> throw new AssertionError(permissionMethod);
                        };
                    });
                });
            });
            return user;
        });
        ContextManager contexts = proxy(ContextManager.class, (p, m, a) -> {
            assert m.getName().equals("getQueryOptions");
            return options;
        });
        LuckPerms luckPerms = proxy(LuckPerms.class, (p, m, a) -> switch (m.getName()) {
            case "getUserManager" -> users;
            case "getContextManager" -> contexts;
            default -> throw new AssertionError(m);
        });
        LuckPermsHook hook = new LuckPermsHook();
        set(LuckPermsHook.class, hook, "luckPerms", luckPerms);
        set(LuckPermsHook.class, hook, "enabled", true);
        Field hookField = PermissionManager.class.getDeclaredField("luckPermsHook");
        hookField.setAccessible(true);
        Object previousHook = hookField.get(null);
        hookField.set(null, hook);

        try {
            PlayerFixture caller = fixture();
            permissions.put(caller.id, nodes(
                    BASE, Tristate.TRUE,
                    BASE + ".survival", Tristate.TRUE,
                    BASE + ".spectator", Tristate.TRUE,
                    BASE + ".creative", Tristate.FALSE,
                    BASE + ".adventure", Tristate.FALSE,
                    BASE + ".other", Tristate.FALSE));

            runNative(caller.player, caller.player, GameMode.SURVIVAL);
            runNative(caller.player, caller.player, GameMode.SPECTATOR);
            assert changed(caller, GameMode.SURVIVAL) && changed(caller, GameMode.SPECTATOR);

            int changes = caller.changes.values().stream().mapToInt(Integer::intValue).sum();
            runNative(caller.player, caller.player, GameMode.CREATIVE);
            runNative(caller.player, caller.player, GameMode.ADVENTURE);
            assert caller.changes.values().stream().mapToInt(Integer::intValue).sum() == changes
                    : "Explicit mode denial changed caller gamemode";

            PlayerFixture other = fixture();
            runNative(caller.player, other.player, GameMode.SURVIVAL);
            assert !changed(other, GameMode.SURVIVAL) : "Mode-only grant changed another player";

            permissions.get(caller.id).put(BASE + ".other", Tristate.TRUE);
            permissions.get(caller.id).put(BASE + ".survival.other", Tristate.FALSE);
            runNative(caller.player, other.player, GameMode.SURVIVAL);
            assert !changed(other, GameMode.SURVIVAL) : "Explicit mode-other denial ignored";

            permissions.get(caller.id).put(BASE + ".survival.other", Tristate.TRUE);
            runNative(caller.player, other.player, GameMode.SURVIVAL);
            assert changed(other, GameMode.SURVIVAL) : "Specific other grant denied";

            // A player used as /execute's callee must not lend permissions or turn an other into self.
            PlayerFixture unprivileged = fixture();
            PlayerFixture privileged = fixture();
            permissions.put(privileged.id, new HashMap<>(permissions.get(caller.id)));
            runNative(unprivileged.player, privileged.player, GameMode.SURVIVAL);
            assert !changed(privileged, GameMode.SURVIVAL) : "Callee permissions escalated caller";
            ConsoleCommandSender console = proxy(ConsoleCommandSender.class, (self, method, args) -> switch (method.getName()) {
                case "isOp", "hasPermission" -> false;
                case "sendMessage" -> null;
                default -> throw new AssertionError("Unexpected console call: " + method);
            });
            runNative(console, privileged.player, GameMode.SURVIVAL);
            assert !changed(privileged, GameMode.SURVIVAL) : "Player callee escalated console command";
            permissions.get(caller.id).put(BASE + ".other", Tristate.FALSE);
            permissions.get(caller.id).put(BASE + ".survival.other", Tristate.FALSE);
            int otherChangesBeforeExecute = other.changes.getOrDefault(GameMode.SURVIVAL, 0);
            runNative(caller.player, other.player, GameMode.SURVIVAL);
            assert other.changes.getOrDefault(GameMode.SURVIVAL, 0) == otherChangesBeforeExecute
                    : "Callee was misclassified as caller self";
            int callerSurvivalChanges = caller.changes.getOrDefault(GameMode.SURVIVAL, 0);
            int otherSurvivalChanges = other.changes.getOrDefault(GameMode.SURVIVAL, 0);
            runNative(caller.player, caller.player, GameMode.SURVIVAL,
                    Map.of("targets", List.of(caller.player, other.player)));
            assert caller.changes.getOrDefault(GameMode.SURVIVAL, 0) == callerSurvivalChanges
                    && other.changes.getOrDefault(GameMode.SURVIVAL, 0) == otherSurvivalChanges
                    : "Denied multi-target request partially changed gamemodes";

            PlayerFixture baseDenied = fixture();
            permissions.put(baseDenied.id, nodes(BASE, Tristate.FALSE, BASE + ".survival", Tristate.TRUE));
            runNative(baseDenied.player, baseDenied.player, GameMode.SURVIVAL);
            assert !changed(baseDenied, GameMode.SURVIVAL) : "Mode grant bypassed explicit base denial";

            System.out.println("Gamemode self modes, explicit denials, other-player gates, and execute identity checks passed");
        } finally {
            hookField.set(null, previousHook);
        }
    }

    private static void runNative(CommandSender caller, CommandSender callee, GameMode mode) throws Exception {
        runNative(caller, callee, mode, Map.of());
    }

    private static void runNative(CommandSender caller, CommandSender callee, GameMode mode,
                                 Map<String, Object> values) throws Exception {
        NativeProxyCommandSender proxy = proxy(NativeProxyCommandSender.class, (p, m, a) -> switch (m.getName()) {
            case "getCaller" -> caller;
            case "getCallee" -> callee;
            default -> throw new AssertionError("Unexpected native sender call: " + m);
        });
        CommandArguments args = new CommandArguments(values.values().toArray(), values, new String[0], Map.of(), "fixture");
        Method execute = GameModeCommandHandler.class.getDeclaredMethod("execute",
                NativeProxyCommandSender.class, CommandArguments.class,
                dev.jorel.commandapi.arguments.EntitySelectorArgument.ManyPlayers.class,
                GameMode.class, boolean.class);
        execute.setAccessible(true);
        execute.invoke(null, proxy, args, null, mode, false);
    }

    private static boolean changed(PlayerFixture fixture, GameMode mode) {
        return fixture.changes.getOrDefault(mode, 0) > 0;
    }

    private static PlayerFixture fixture() { return new PlayerFixture(); }

    private static Map<String, Tristate> nodes(Object... entries) {
        Map<String, Tristate> values = new HashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            values.put((String) entries[index], (Tristate) entries[index + 1]);
        }
        return values;
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
