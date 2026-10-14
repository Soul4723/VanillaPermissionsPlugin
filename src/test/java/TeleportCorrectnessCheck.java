import dev.jorel.commandapi.executors.CommandArguments;
import dev.jorel.commandapi.wrappers.NativeProxyCommandSender;
import io.github.soul4723.vanillapermissions.command.VanillaPermissionsPluginCommand;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Run with -ea and Maven's test classpath. Invokes compiled executor bodies.
// Substitutes native wrappers and teleport outcomes, including cancellation's false return;
// does not dispatch commands, construct real native sources, or fire teleport events.
// ponytail: lambda indices tied to source/compiler; use registered command integration tests
// when dispatch coverage is needed, and inspect javap after registration edits.
public class TeleportCorrectnessCheck {
    static class Fixture {
        boolean accepted = true;
        int calls;
        Object destination;
        final List<String> messages = new ArrayList<>();
        boolean permitted = true;
        int permissionChecks;
        final CommandSender sender;
        final Player player;
        final java.util.UUID id = java.util.UUID.randomUUID();
        Fixture(World world) { this(world, Player.class); }
        Fixture(World world, Class<? extends CommandSender> role) {
            sender = (CommandSender) Proxy.newProxyInstance(role.getClassLoader(), new Class<?>[]{role},
                (proxy, method, values) -> switch (method.getName()) {
                    case "isOp" -> { permissionChecks++; yield permitted; }
                    case "getUniqueId" -> id;
                    case "hasPermission" -> false;
                    case "getWorld" -> world;
                    case "getName" -> "Target";
                    case "teleport" -> { calls++; destination = values[0]; yield accepted; }
                    case "sendMessage" -> { messages.add((String) values[0]); yield null; }
                    default -> throw new AssertionError("Unexpected sender call: " + method);
                });
            player = sender instanceof Player ? (Player) sender : null;
        }
    }
    static NativeProxyCommandSender nativeSource(CommandSender caller, World world) {
        return (NativeProxyCommandSender) Proxy.newProxyInstance(NativeProxyCommandSender.class.getClassLoader(),
            new Class<?>[]{NativeProxyCommandSender.class}, (proxy, method, values) -> switch (method.getName()) {
                case "getCaller" -> caller;
                case "getWorld" -> world;
                default -> throw new AssertionError("Unexpected native source call: " + method);
            });
    }
    static void run(int index, CommandSender sender, Map<String, Object> args) throws Exception {
        Method executor = VanillaPermissionsPluginCommand.class.getDeclaredMethod(
            "lambda$registerVanillaCommands$" + index, index == 16 ? NativeProxyCommandSender.class : CommandSender.class, CommandArguments.class);
        executor.setAccessible(true);
        executor.invoke(null, sender, new CommandArguments(args.values().toArray(), args,
            new String[0], Map.of(), "fixture"));
    }
    static Map<String, Object> coordinates() {
        return Map.of("x", 10.0, "y", 64.0, "z", 20.0, "yaw", 90.0f, "pitch", 30.0f);
    }
    static void feedback(Fixture sender, String expected) {
        assert sender.messages.size() == 1 : sender.messages;
        assert sender.messages.get(0).contains(expected) : sender.messages;
        sender.messages.clear();
    }
    public static void main(String[] ignored) throws Exception {
        World source = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
            (p, m, v) -> { throw new AssertionError("Unexpected World call: " + m); });
        Fixture sender = new Fixture(source), first = new Fixture(null), second = new Fixture(null);
        for (boolean accepted : new boolean[]{true, false}) {
            sender.accepted = accepted;
            run(10, sender.player, Map.of("target", first.player));
            assert sender.destination == first.player;
            feedback(sender, accepted ? "§aTeleported to Target" : "§cCould not teleport to Target");
            run(12, sender.player, coordinates());
            Location location = (Location) sender.destination;
            assert location.getWorld() == source;
            assert location.getYaw() == 90 && location.getPitch() == 30;
            feedback(sender, accepted ? "§aTeleported to " : "§cCould not teleport to ");
        }
        for (int index : new int[]{14, 16}) {
            for (int successes = 0; successes <= 2; successes++) {
                first.accepted = successes > 0;
                second.accepted = successes > 1;
                Map<String, Object> args = new java.util.HashMap<>(coordinates());
                args.put("targets", List.of(first.player, second.player));
                args.put("destination", sender.player);
                int before = first.calls + second.calls;
                run(index, index == 16 ? nativeSource(sender.player, source) : sender.player, args);
                assert first.calls + second.calls == before + 2;
                if (index == 16) {
                    for (Fixture target : List.of(first, second)) {
                        Location location = (Location) target.destination;
                        assert location.getWorld() == source : "Target world used";
                        assert location.getX() == 10 && location.getY() == 64 && location.getZ() == 20;
                        assert location.getYaw() == 90 && location.getPitch() == 30;
                    }
                } else {
                    assert first.destination == sender.player && second.destination == sender.player;
                }
                if (successes == 0) feedback(sender, "§cTeleport failed for all targets!");
                else {
                    assert sender.messages.get(0).contains("failed") == (successes == 1);
                    feedback(sender, "§aTeleported " + successes + " entit");
                }
            }
            Map<String, Object> empty = new java.util.HashMap<>(coordinates());
            empty.put("targets", List.of()); empty.put("destination", sender.player);
            run(index, index == 16 ? nativeSource(sender.player, source) : sender.player, empty);
            feedback(sender, "§cNo targets found!");
        }
        run(16, nativeSource(sender.player, source), Map.of("targets", List.of(first.player), "x", 1.0, "y", 64.0, "z", 2.0));
        Location defaultRotation = (Location) first.destination;
        assert defaultRotation.getYaw() == 0 && defaultRotation.getPitch() == 0;
        feedback(sender, "§aTeleported 1 entity");
        // Native world differs from player/block worlds and target worlds, as with /execute in.
        for (Class<? extends CommandSender> role : List.of(Player.class, ConsoleCommandSender.class, BlockCommandSender.class)) {
            Fixture caller = new Fixture(null, role);
            Map<String, Object> args = new java.util.HashMap<>(coordinates());
            args.put("targets", List.of(first.player, second.player));
            for (int successes = 0; successes <= 2; successes++) {
                first.accepted = successes > 0;
                second.accepted = successes > 1;
                int before = first.calls + second.calls;
                run(16, nativeSource(caller.sender, source), args);
                assert first.calls + second.calls == before + 2;
                assert ((Location) first.destination).getWorld() == source;
                assert ((Location) second.destination).getWorld() == source;
                if (successes == 0) feedback(caller, "§cTeleport failed for all targets!");
                else {
                    assert caller.messages.get(0).contains("failed") == (successes == 1);
                    feedback(caller, "§aTeleported " + successes + " entit");
                }
            }
            assert caller.permissionChecks == 3 : "Caller permission identity lost";
            caller.permitted = false;
            int before = first.calls + second.calls;
            run(16, nativeSource(caller.sender, source), args);
            assert caller.permissionChecks == 4;
            assert first.calls + second.calls == before : "Denied caller teleported targets";
            feedback(caller, "§cYou don't have permission to teleport entities to coordinates!");
        }
        assert first.permissionChecks == 0 && second.permissionChecks == 0 : "Target permissions used";
        assert first.messages.isEmpty() && second.messages.isEmpty() : "Target messages added";
        System.out.println("Four actual teleport executor bodies: results, counts, native player/console/block worlds, caller permissions, rotation, empty targets passed");
    }
}
