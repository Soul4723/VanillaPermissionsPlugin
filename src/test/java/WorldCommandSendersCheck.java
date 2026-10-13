import dev.jorel.commandapi.executors.CommandArguments;
import dev.jorel.commandapi.wrappers.NativeProxyCommandSender;
import io.github.soul4723.extrapermissions.command.DifficultyCommandHandler;
import io.github.soul4723.extrapermissions.command.GameruleCommandHandler;
import io.github.soul4723.extrapermissions.command.TimeCommandHandler;
import io.github.soul4723.extrapermissions.command.WeatherCommandHandler;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Run with -ea and Maven's test classpath. Actual executor/gate bodies, substituted APIs.
// ponytail: compiler lambda indices; inspect javap after registration edits and use live
// registered command checks when parser, dispatch, or /execute routing coverage is needed.
public class WorldCommandSendersCheck {
    static class WorldState {
        long time = 6000;
        boolean storm, thunder, rule = true;
        Difficulty difficulty;
        final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (p, m, v) -> switch (m.getName()) {
                    case "getTime" -> time;
                    case "getFullTime" -> 72000L;
                    case "getGameTime" -> 90000L;
                    case "setTime" -> { time = (long) v[0]; yield null; }
                    case "setStorm" -> { storm = (boolean) v[0]; yield null; }
                    case "setThundering" -> { thunder = (boolean) v[0]; yield null; }
                    case "getGameRuleValue" -> rule;
                    case "setGameRule" -> { rule = (boolean) v[1]; yield true; }
                    case "setDifficulty" -> { difficulty = (Difficulty) v[0]; yield null; }
                    default -> throw new AssertionError("Unexpected world call: " + m);
                });
    }
    static Object invoke(Class<?> handler, int index, CommandSender sender, Map<String, Object> args) throws Exception {
        Method method = handler.getDeclaredMethod("lambda$registerCommands$" + index, args == null
                ? new Class<?>[]{CommandSender.class}
                : new Class<?>[]{NativeProxyCommandSender.class, CommandArguments.class});
        method.setAccessible(true);
        return method.invoke(null, args == null ? new Object[]{sender} : new Object[]{sender,
                new CommandArguments(args.values().toArray(), args, new String[0], Map.of(), "fixture")});
    }
    static void run(Class<?> handler, int index, NativeProxyCommandSender source, Map<String, Object> args,
                    List<String> messages, String expected) throws Exception {
        invoke(handler, index, source, args);
        assert messages.equals(List.of(expected)) : messages;
        messages.clear();
    }
    public static void main(String[] ignored) throws Exception {
        WorldState sourceWorld = new WorldState(), otherWorld = new WorldState();
        Server server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (p, m, v) -> {
                    assert m.getName().equals("getWorlds") : m;
                    return List.of(sourceWorld.world, otherWorld.world);
                });
        Field field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, server);
        try {
            for (Class<? extends CommandSender> role : List.of(ConsoleCommandSender.class, BlockCommandSender.class, Player.class)) {
                List<String> messages = new ArrayList<>(), permissions = new ArrayList<>();
                boolean[] permitted = {true};
                CommandSender caller = (CommandSender) Proxy.newProxyInstance(role.getClassLoader(), new Class<?>[]{role},
                        (p, m, v) -> switch (m.getName()) {
                            case "isOp" -> false;
                            case "hasPermission" -> { permissions.add((String) v[0]); yield permitted[0]; }
                            case "sendMessage" -> { messages.add((String) v[0]); yield null; }
                            default -> throw new AssertionError("Caller world/other API used: " + m);
                        });
                NativeProxyCommandSender source = (NativeProxyCommandSender) Proxy.newProxyInstance(
                        NativeProxyCommandSender.class.getClassLoader(), new Class<?>[]{NativeProxyCommandSender.class},
                        (p, m, v) -> switch (m.getName()) {
                            case "getCaller" -> caller;
                            case "getWorld" -> sourceWorld.world;
                            default -> throw new AssertionError("Proxy/callee feedback or privileges used: " + m);
                        });
                // Registration predicates tested separately; this does not emulate dispatcher rejection.
                Class<?>[] handlers = {TimeCommandHandler.class, WeatherCommandHandler.class,
                        GameruleCommandHandler.class, DifficultyCommandHandler.class};
                String[][] suffixes = {{"time.set", "time.add", "time.query"}, {"weather.clear", "weather.rain", "weather.thunder"},
                        {"gamerule.query", "gamerule.set"}, {"difficulty"}};
                for (int h = 0; h < handlers.length; h++) {
                    for (int g = 0; g < suffixes[h].length; g++) {
                        assert (boolean) invoke(handlers[h], g * 2, caller, null);
                        assert permissions.equals(List.of("minecraft.command." + suffixes[h][g])) : permissions;
                        permissions.clear();
                        permitted[0] = false;
                        assert !(boolean) invoke(handlers[h], g * 2, caller, null);
                        permitted[0] = true;
                        permissions.clear();
                    }
                }
                run(TimeCommandHandler.class, 1, source, Map.of("time", 1000), messages, "§aTime set to 1000");
                assert sourceWorld.time == 1000;
                run(TimeCommandHandler.class, 3, source, Map.of("time", 20), messages, "§aAdded 20 ticks to time");
                assert sourceWorld.time == 1020;
                run(TimeCommandHandler.class, 5, source, Map.of("type", "day"), messages, "§aTime: 3");
                run(WeatherCommandHandler.class, 1, source, Map.of(), messages, "§aWeather cleared.");
                assert !sourceWorld.storm && !sourceWorld.thunder;
                run(WeatherCommandHandler.class, 3, source, Map.of(), messages, "§aRain started.");
                assert sourceWorld.storm && !sourceWorld.thunder;
                run(WeatherCommandHandler.class, 5, source, Map.of(), messages, "§aThunderstorm started.");
                assert sourceWorld.storm && sourceWorld.thunder;
                run(GameruleCommandHandler.class, 3, source, Map.of("rule", "doDaylightCycle", "value", "false"), messages,
                        "§aSet doDaylightCycle to false");
                assert !sourceWorld.rule;
                run(GameruleCommandHandler.class, 1, source, Map.of("rule", "doDaylightCycle"), messages, "§adoDaylightCycle = false");
                run(TimeCommandHandler.class, 1, source, Map.of(), messages, "§cTime is required");
                run(GameruleCommandHandler.class, 3, source, Map.of("rule", "doDaylightCycle", "value", "invalid"), messages,
                        "§cError setting gamerule: Invalid boolean value: invalid (expected true or false)");
                assert !sourceWorld.rule && sourceWorld.time == 1020;
                assert otherWorld.time == 6000 && !otherWorld.storm && !otherWorld.thunder && otherWorld.rule;
                run(DifficultyCommandHandler.class, 1, source, Map.of("difficulty", "hard"), messages, "§aSet difficulty to hard");
                assert sourceWorld.difficulty == Difficulty.HARD && otherWorld.difficulty == Difficulty.HARD;
                assert permissions.isEmpty() : "Executor added permission policy";
            }
        } finally {
            field.set(null, previous);
        }
        System.out.println("World commands actual executors/gates: console/block/player source world, caller-only feedback, unchanged difficulty scope and validation passed");
    }
}
