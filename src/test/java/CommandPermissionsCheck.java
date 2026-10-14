import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.command.CommandSender;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

// Run with -ea and Maven's test classpath. Bukkit fallback only; no LuckPerms/server fixture.
public class CommandPermissionsCheck {
    public static void main(String[] args) {
        Set<String> grants = new HashSet<>();
        boolean[] fail = {false};
        CommandSender sender = (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(), new Class<?>[]{CommandSender.class},
                (proxy, method, values) -> switch (method.getName()) {
                    case "hasPermission" -> {
                        if (fail[0]) throw new IllegalStateException("Permission provider unavailable");
                        yield grants.contains(values[0]);
                    }
                    default -> throw new AssertionError("Unexpected sender call: " + method);
                });

        String leaf = "minecraft.command.time.set";
        assert !PermissionManager.hasPermission(sender, leaf) : "Unset allowed";
        grants.add("minecraft.command.time");
        assert !PermissionManager.hasPermission(sender, leaf) : "Bare parent grant allowed child";
        assert !PermissionManager.hasPermission(sender, "minecraft.command.weather.rain")
                : "Unrelated parent allowed";
        grants.clear();
        grants.add(leaf);
        assert PermissionManager.hasPermission(sender, leaf) : "Leaf denied";
        grants.clear();
        fail[0] = true;
        assert !PermissionManager.hasPermission(sender, leaf) : "Provider failure allowed";
        System.out.println("Exact permission, unset, and provider-failure checks passed");
    }
}
