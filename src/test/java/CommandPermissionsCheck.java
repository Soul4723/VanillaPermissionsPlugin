import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.command.CommandSender;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

// Run with -ea and Maven's test classpath. Bukkit fallback only; no LuckPerms/server fixture.
public class CommandPermissionsCheck {
    public static void main(String[] args) {
        Set<String> grants = new HashSet<>();
        boolean[] op = {false};
        boolean[] fail = {false};
        CommandSender sender = (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(), new Class<?>[]{CommandSender.class},
                (proxy, method, values) -> switch (method.getName()) {
                    case "isOp" -> op[0];
                    case "hasPermission" -> {
                        if (fail[0]) throw new IllegalStateException("Permission provider unavailable");
                        yield grants.contains(values[0]);
                    }
                    default -> throw new AssertionError("Unexpected sender call: " + method);
                });

        String leaf = "minecraft.command.time.set";
        assert !PermissionManager.hasPermissionOrParent(sender, leaf) : "Unset allowed";
        grants.add("minecraft.command.time");
        assert !PermissionManager.hasPermission(sender, leaf) : "Fixture grants leaf";
        assert PermissionManager.hasPermissionOrParent(sender, leaf) : "Parent denied";
        assert !PermissionManager.hasPermissionOrParent(sender, "minecraft.command.weather.rain")
                : "Unrelated parent allowed";
        grants.clear();
        grants.add(leaf);
        assert PermissionManager.hasPermissionOrParent(sender, leaf) : "Leaf denied";
        grants.clear();
        grants.add("minecraft.selector.player");
        assert PermissionManager.hasPermissionOrParent(sender, "minecraft.selector.player.effect.targets")
                : "Selector parent denied";
        grants.clear();
        fail[0] = true;
        assert !PermissionManager.hasPermissionOrParent(sender, leaf) : "Provider failure allowed";
        op[0] = true;
        assert PermissionManager.hasPermissionOrParent(sender, leaf) : "Default OP bypass denied";
        System.out.println("Permission helper parent, leaf, unset, failure, and default OP checks passed");
    }
}
