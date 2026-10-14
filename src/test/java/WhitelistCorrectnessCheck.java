import com.destroystokyo.paper.profile.PlayerProfile;
import dev.jorel.commandapi.executors.CommandArguments;
import io.github.soul4723.vanillapermissions.command.WhitelistCommandHandler;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Run with -ea and Maven's test classpath. Real executor/requirement bodies, API proxies.
// No native parser, network, dispatcher, whitelist event, disk persistence, or proxy routing.
// ponytail: compiler lambda indices; use live registered commands when dispatch coverage is needed.
public class WhitelistCorrectnessCheck {
    static class Target {
        final UUID id = UUID.randomUUID();
        boolean listed, cancelled, fails;
        int mutations;
        final OfflinePlayer player = (OfflinePlayer) Proxy.newProxyInstance(OfflinePlayer.class.getClassLoader(),
                new Class<?>[]{OfflinePlayer.class}, (p, m, v) -> switch (m.getName()) {
                    case "isWhitelisted" -> listed;
                    case "setWhitelisted" -> {
                        mutations++;
                        if (fails) throw new IllegalStateException("simulated failure");
                        if (!cancelled) listed = (boolean) v[0];
                        yield null;
                    }
                    default -> throw new AssertionError("Unexpected offline player call: " + m);
                });
    }
    static PlayerProfile profile(UUID id, String name) {
        return (PlayerProfile) Proxy.newProxyInstance(PlayerProfile.class.getClassLoader(),
                new Class<?>[]{PlayerProfile.class}, (p, m, v) -> switch (m.getName()) {
                    case "getId" -> id;
                    case "getName" -> name;
                    default -> throw new AssertionError("Unexpected profile call: " + m);
                });
    }
    static Object invoke(int index, CommandSender sender, CommandArguments args) throws Exception {
        Method method = WhitelistCommandHandler.class.getDeclaredMethod("lambda$registerCommands$" + index,
                args == null ? new Class<?>[]{CommandSender.class} : new Class<?>[]{CommandSender.class, CommandArguments.class});
        method.setAccessible(true);
        return method.invoke(null, args == null ? new Object[]{sender} : new Object[]{sender, args});
    }
    static void run(int index, CommandSender sender, List<PlayerProfile> profiles) throws Exception {
        Map<String, Object> args = profiles == null ? Map.of() : Map.of("player", profiles);
        invoke(index, sender, new CommandArguments(args.values().toArray(), args, new String[0], Map.of(), "fixture"));
    }
    static void feedback(List<String> messages, String... expected) {
        assert messages.equals(List.of(expected)) : messages;
        messages.clear();
    }
    public static void main(String[] ignored) throws Exception {
        Map<UUID, Target> targets = new HashMap<>();
        List<UUID> lookups = new ArrayList<>();
        boolean[] enabled = {false};
        Server server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (p, m, v) -> switch (m.getName()) {
                    case "getOfflinePlayer" -> {
                        assert v[0] instanceof UUID : "Name lookup used";
                        lookups.add((UUID) v[0]);
                        Target target = targets.get(v[0]);
                        assert target != null : "Wrong UUID";
                        yield target.player;
                    }
                    case "setWhitelist" -> { enabled[0] = (boolean) v[0]; yield null; }
                    default -> throw new AssertionError("Unexpected server call: " + m);
                });
        Field field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, server);
        try {
            for (Class<? extends CommandSender> role : List.of(ConsoleCommandSender.class, BlockCommandSender.class, Player.class)) {
                List<String> messages = new ArrayList<>(), permissions = new ArrayList<>();
                boolean[] permitted = {true};
                CommandSender sender = (CommandSender) Proxy.newProxyInstance(role.getClassLoader(), new Class<?>[]{role},
                        (p, m, v) -> switch (m.getName()) {
                            case "isOp" -> false;
                            case "hasPermission" -> { permissions.add((String) v[0]); yield permitted[0]; }
                            case "sendMessage" -> { messages.add((String) v[0]); yield null; }
                            default -> throw new AssertionError("Unexpected sender call: " + m);
                        });
                // Invoke actual registration predicates separately; dispatcher itself remains a live gate.
                for (int gate : new int[]{0, 2, 4, 6}) {
                    assert (boolean) invoke(gate, sender, null);
                    assert permissions.remove(0).equals("minecraft.command.whitelist." + List.of("add", "remove", "on", "off").get(gate / 2));
                    permitted[0] = false;
                    assert !(boolean) invoke(gate, sender, null);
                    permitted[0] = true;
                    permissions.clear();
                }
                Target first = new Target(), second = new Target();
                targets.put(first.id, first); targets.put(second.id, second);
                List<PlayerProfile> profiles = List.of(profile(first.id, "Newcomer"), profile(second.id, "Other"));
                lookups.clear();
                run(1, sender, profiles);
                assert lookups.equals(List.of(first.id, second.id));
                assert first.listed && second.listed;
                feedback(messages, "§aNewcomer added to whitelist.", "§aOther added to whitelist.");
                run(1, sender, profiles);
                feedback(messages, "§cNewcomer is already whitelisted.", "§cOther is already whitelisted.");
                assert first.mutations == 1 && second.mutations == 1;
                run(3, sender, profiles);
                assert !first.listed && !second.listed;
                feedback(messages, "§aNewcomer removed from whitelist.", "§aOther removed from whitelist.");
                run(3, sender, profiles);
                feedback(messages, "§cNewcomer is not whitelisted.", "§cOther is not whitelisted.");
                assert first.mutations == 2 && second.mutations == 2;
                for (int body : new int[]{1, 3}) {
                    first.listed = second.listed = body == 3;
                    first.cancelled = true;
                    run(body, sender, profiles);
                    assert first.listed == (body == 3) && second.listed == (body == 1);
                    feedback(messages, body == 1 ? "§cCould not add Newcomer to whitelist." : "§cCould not remove Newcomer from whitelist.",
                            body == 1 ? "§aOther added to whitelist." : "§aOther removed from whitelist.");
                    first.cancelled = false; first.fails = true;
                    second.listed = body == 3;
                    run(body, sender, profiles);
                    feedback(messages, body == 1 ? "§cCould not add Newcomer to whitelist." : "§cCould not remove Newcomer from whitelist.",
                            body == 1 ? "§aOther added to whitelist." : "§aOther removed from whitelist.");
                    first.fails = false;
                    lookups.clear();
                    run(body, sender, Arrays.asList(null, profile(null, "MissingUUID"), profile(first.id, null), profile(first.id, ""), profile(first.id, " ")));
                    assert lookups.isEmpty();
                    assert messages.size() == 5 && messages.stream().allMatch("§cInvalid player identity"::equals) : messages;
                    messages.clear();
                }
                run(5, sender, null); assert enabled[0]; feedback(messages, "§aWhitelist enabled.");
                run(7, sender, null); assert !enabled[0]; feedback(messages, "§aWhitelist disabled.");
            }
        } finally {
            field.set(null, previous);
        }
        System.out.println("Whitelist real executors/gates: console/block/player, UUID profiles, multiple targets, invalid identities, duplicates, cancellation/failure, on/off passed");
    }
}
