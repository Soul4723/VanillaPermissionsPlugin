import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.executors.CommandArguments;
import dev.jorel.commandapi.wrappers.NativeProxyCommandSender;
import dev.jorel.commandapi.wrappers.Rotation;
import io.github.soul4723.vanillapermissions.command.VanillaPermissionsPluginCommand;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Run with -ea and Maven's test classpath. Invokes named compiled executor bodies.
// Parsed locations/rotations are supplied directly; this does not test Brigadier parsing or dispatch.
public class TeleportCorrectnessCheck {
    static final class Fixture {
        boolean accepted = true;
        int calls;
        Object destination;
        final List<String> messages = new ArrayList<>();
        final Set<String> permissions = new java.util.HashSet<>();
        final Location current = new Location(null, 0, 0, 0, 35, -15);
        final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
            (self, method, values) -> switch (method.getName()) {
                case "isOp" -> false;
                case "hasPermission" -> permissions.contains(values[0]);
                case "sendMessage" -> { messages.add((String) values[0]); yield null; }
                case "getName" -> "Fixture";
                case "getLocation" -> current;
                case "teleport" -> { calls++; destination = values[0]; yield accepted; }
                case "equals" -> self == values[0];
                case "hashCode" -> System.identityHashCode(self);
                default -> throw new AssertionError("Unexpected player call: " + method);
            });
        Fixture(String... grants) { permissions.addAll(List.of(grants)); }
    }

    static NativeProxyCommandSender nativeSource(CommandSender caller, Player callee) {
        return (NativeProxyCommandSender) Proxy.newProxyInstance(NativeProxyCommandSender.class.getClassLoader(),
            new Class<?>[]{NativeProxyCommandSender.class}, (proxy, method, values) -> switch (method.getName()) {
                case "getCaller" -> caller;
                case "getCallee" -> callee;
                default -> throw new AssertionError("Unexpected native source call: " + method);
            });
    }

    static Map<String, Object> arguments(Object... pairs) {
        Map<String, Object> args = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) args.put((String) pairs[i], pairs[i + 1]);
        return args;
    }

    static void run(String name, Class<?>[] signature, Object... values) throws Exception {
        Method executor = VanillaPermissionsPluginCommand.class.getDeclaredMethod(name, signature);
        executor.setAccessible(true);
        int argumentsIndex = java.util.Arrays.asList(signature).indexOf(CommandArguments.class);
        Map<String, Object> parsed = (Map<String, Object>) values[argumentsIndex];
        CommandArguments args = new CommandArguments(parsed.values().toArray(), parsed, new String[0], Map.of(), "fixture");
        Object[] invocation = values.clone();
        invocation[argumentsIndex] = args;
        executor.invoke(null, invocation);
    }

    static void feedback(Fixture sender, String expected) {
        assert sender.messages.size() == 1 : sender.messages;
        assert sender.messages.get(0).contains(expected) : sender.messages;
        sender.messages.clear();
    }

    static Location location(World world, double x, double y, double z) {
        return new Location(world, x, y, z);
    }

    static void teleportToEntity(Fixture caller, Fixture executor, Fixture target) throws Exception {
        caller.permissions.add("minecraft.command.teleport");
        for (boolean accepted : new boolean[]{true, false}) {
            executor.accepted = accepted;
            run("teleportToEntity", new Class<?>[]{boolean.class, NativeProxyCommandSender.class,
                    CommandArguments.class, EntitySelectorArgument.OneEntity.class},
                false, nativeSource(caller.player, executor.player), arguments("target", target.player), null);
            assert executor.destination == target.player;
            feedback(caller, accepted ? "§aTeleported to Fixture" : "§cCould not teleport to Fixture");
        }
    }

    static void teleportToLocation(Fixture caller, Fixture executor, World world) throws Exception {
        caller.permissions.add("minecraft.command.teleport");
        Location parsed = location(world, 10, 64, 20);
        run("teleportToLocation", new Class<?>[]{NativeProxyCommandSender.class, CommandArguments.class},
            nativeSource(caller.player, executor.player), arguments("location", parsed, "rotation", new Rotation(90, 30)));
        Location destination = (Location) executor.destination;
        assert destination != parsed && destination.getWorld() == world;
        assert destination.getX() == 10 && destination.getY() == 64 && destination.getZ() == 20;
        assert destination.getYaw() == 90 && destination.getPitch() == 30;
        feedback(caller, "§aTeleported to ");

        run("teleportToLocation", new Class<?>[]{NativeProxyCommandSender.class, CommandArguments.class},
            nativeSource(caller.player, executor.player), arguments("location", parsed));
        destination = (Location) executor.destination;
        assert destination.getYaw() == 35 && destination.getPitch() == -15 : "Absent rotation must preserve executor rotation";
        feedback(caller, "§aTeleported to ");

        int calls = executor.calls;
        run("teleportToLocation", new Class<?>[]{NativeProxyCommandSender.class, CommandArguments.class},
            nativeSource(caller.player, executor.player), arguments("location", location(world, 30_000_001, 64, 20)));
        assert executor.calls == calls : "Out-of-bounds location teleported";
        feedback(caller, "§cCoordinates out of world bounds");

        run("teleportToLocation", new Class<?>[]{NativeProxyCommandSender.class, CommandArguments.class},
            nativeSource(caller.player, executor.player), arguments("location", location(world, 30_000_000, 20_000_000, -30_000_000)));
        assert executor.calls == calls + 1 : "Inclusive world bounds rejected";
        feedback(caller, "§aTeleported to ");
    }

    static void teleportTargetsToEntity(Fixture caller, Fixture first, Fixture second, Fixture destination) throws Exception {
        caller.permissions.add("minecraft.command.teleport");
        for (int successes = 0; successes <= 2; successes++) {
            first.accepted = successes > 0;
            second.accepted = successes > 1;
            int calls = first.calls + second.calls;
            run("teleportTargetsToEntity", new Class<?>[]{boolean.class, NativeProxyCommandSender.class,
                    CommandArguments.class, EntitySelectorArgument.ManyEntities.class,
                    EntitySelectorArgument.OneEntity.class},
                false, nativeSource(caller.player, destination.player),
                arguments("targets", List.of(first.player, second.player), "destination", destination.player), null, null);
            assert first.calls + second.calls == calls + 2;
            assert first.destination == destination.player && second.destination == destination.player;
            if (successes == 0) feedback(caller, "§cTeleport failed for all 2 targets.");
            else feedback(caller, "§aTeleported " + successes + " of 2 targets");
        }
        run("teleportTargetsToEntity", new Class<?>[]{boolean.class, NativeProxyCommandSender.class,
                CommandArguments.class, EntitySelectorArgument.ManyEntities.class,
                EntitySelectorArgument.OneEntity.class},
            false, nativeSource(caller.player, destination.player), arguments("targets", List.of(), "destination", destination.player), null, null);
        feedback(caller, "§cNo entities matched the target");
    }

    static void teleportTargetsToLocation(Fixture caller, Fixture first, Fixture second, World world) throws Exception {
        caller.permissions.add("minecraft.command.teleport");
        Location parsed = location(world, 10, 64, 20);
        for (int successes = 0; successes <= 2; successes++) {
            first.accepted = successes > 0;
            second.accepted = successes > 1;
            int calls = first.calls + second.calls;
            run("teleportTargetsToLocation", new Class<?>[]{boolean.class, NativeProxyCommandSender.class,
                    CommandArguments.class, EntitySelectorArgument.ManyEntities.class},
                false, nativeSource(caller.player, caller.player),
                arguments("targets", List.of(first.player, second.player), "location", parsed, "rotation", new Rotation(90, 30)), null);
            assert first.calls + second.calls == calls + 2;
            for (Fixture target : List.of(first, second)) {
                Location destination = (Location) target.destination;
                assert destination.getWorld() == world : "Native parsed world lost";
                assert destination.getX() == 10 && destination.getY() == 64 && destination.getZ() == 20;
                assert destination.getYaw() == 90 && destination.getPitch() == 30;
            }
            if (successes == 0) feedback(caller, "§cTeleport failed for all 2 targets.");
            else feedback(caller, "§aTeleported " + successes + " of 2 targets");
        }

        run("teleportTargetsToLocation", new Class<?>[]{boolean.class, NativeProxyCommandSender.class,
                CommandArguments.class, EntitySelectorArgument.ManyEntities.class},
            false, nativeSource(caller.player, caller.player),
            arguments("targets", List.of(first.player), "location", parsed), null);
        Location defaultRotation = (Location) first.destination;
        assert defaultRotation.getYaw() == 35 && defaultRotation.getPitch() == -15;
        feedback(caller, "§aTeleported 1 of 1 targets");

        int calls = first.calls + second.calls;
        run("teleportTargetsToLocation", new Class<?>[]{boolean.class, NativeProxyCommandSender.class,
                CommandArguments.class, EntitySelectorArgument.ManyEntities.class},
            false, nativeSource(caller.player, caller.player),
            arguments("targets", List.of(first.player), "location", location(world, 1, 20_000_001, 2)), null);
        assert first.calls + second.calls == calls : "Out-of-bounds location teleported";
        feedback(caller, "§cNo valid entity target or destination was found");

        run("teleportTargetsToLocation", new Class<?>[]{boolean.class, NativeProxyCommandSender.class,
                CommandArguments.class, EntitySelectorArgument.ManyEntities.class},
            false, nativeSource(caller.player, caller.player), arguments("targets", List.of(), "location", parsed), null);
        feedback(caller, "§cNo entities matched the target");
    }

    static void callerPermissions(Fixture caller, Fixture callee, Fixture target, World world) throws Exception {
        caller.permissions.clear();
        callee.permissions.add("minecraft.command.teleport");
        int calls = target.calls;
        run("teleportTargetsToLocation", new Class<?>[]{boolean.class, NativeProxyCommandSender.class,
                CommandArguments.class, EntitySelectorArgument.ManyEntities.class},
            false, nativeSource(caller.player, callee.player),
            arguments("targets", List.of(target.player), "location", location(world, 1, 2, 3)), null);
        assert target.calls == calls : "Callee permissions authorized caller";
        feedback(caller, "§cYou don't have permission to use this teleport form");

        caller.permissions.add("minecraft.command.teleport");
        callee.permissions.clear();
        run("teleportTargetsToLocation", new Class<?>[]{boolean.class, NativeProxyCommandSender.class,
                CommandArguments.class, EntitySelectorArgument.ManyEntities.class},
            false, nativeSource(caller.player, callee.player),
            arguments("targets", List.of(target.player), "location", location(world, 1, 2, 3)), null);
        assert target.calls == calls + 1 : "Caller grant was ignored";
        feedback(caller, "§aTeleported 1 of 1 targets");
    }

    public static void main(String[] ignored) throws Exception {
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
            (p, m, v) -> { throw new AssertionError("Unexpected World call: " + m); });
        Fixture caller = new Fixture();
        Fixture executor = new Fixture();
        Fixture first = new Fixture(), second = new Fixture(), destination = new Fixture();
        teleportToEntity(caller, executor, destination);
        teleportToLocation(caller, executor, world);
        teleportTargetsToEntity(caller, first, second, destination);
        teleportTargetsToLocation(caller, first, second, world);
        callerPermissions(caller, executor, first, world);
        assert first.messages.isEmpty() && second.messages.isEmpty() && destination.messages.isEmpty();
        System.out.println("Named teleport executor bodies: all four forms, caller permissions, target results, native world, rotation, empty targets, and world bounds passed");
    }
}
