import io.github.soul4723.vanillapermissions.command.GameruleCommandHandler;
import org.bukkit.GameRule;
import org.bukkit.World;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

// Run with assertions enabled and Maven's test classpath; no server required.
public class CommandCorrectnessCheck {
    public static void main(String[] args) throws Exception {
        Method set = GameruleCommandHandler.class.getDeclaredMethod(
                "setGameRuleValue", World.class, GameRule.class, String.class);
        set.setAccessible(true);
        Object[] lastValue = {null};
        int[] mutations = {0};
        boolean[] accepted = {true};
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, values) -> {
                    if (!method.getName().equals("setGameRule")) {
                        throw new AssertionError("Unexpected World call: " + method);
                    }
                    mutations[0]++;
                    lastValue[0] = values[1];
                    return accepted[0];
                });

        set.invoke(null, world, GameRule.KEEP_INVENTORY, "true");
        assert Boolean.TRUE.equals(lastValue[0]);
        set.invoke(null, world, GameRule.KEEP_INVENTORY, "false");
        assert Boolean.FALSE.equals(lastValue[0]);
        set.invoke(null, world, GameRule.KEEP_INVENTORY, "TRUE");
        assert Boolean.TRUE.equals(lastValue[0]);
        int before = mutations[0];
        expectInvalid(set, world, GameRule.KEEP_INVENTORY, "treu", "boolean");
        expectInvalid(set, world, GameRule.KEEP_INVENTORY, "0", "boolean");
        assert mutations[0] == before : "Invalid boolean mutated world";
        assert Boolean.TRUE.equals(lastValue[0]) : "Typo disabled keepInventory";

        set.invoke(null, world, GameRule.RANDOM_TICK_SPEED, "3");
        assert Integer.valueOf(3).equals(lastValue[0]);
        before = mutations[0];
        expectInvalid(set, world, GameRule.RANDOM_TICK_SPEED, "2147483648", "integer");
        assert mutations[0] == before : "Invalid integer mutated world";
        accepted[0] = false;
        expectInvalid(set, world, GameRule.KEEP_INVENTORY, "true", "Failed");
        expectInvalid(set, world, GameRule.RANDOM_TICK_SPEED, "3", "Failed");
        System.out.println("Gamerule input and rejected-mutation checks passed");
    }

    private static void expectInvalid(Method set, World world, GameRule<?> rule,
                                      String value, String message) throws Exception {
        try {
            set.invoke(null, world, rule, value);
            throw new AssertionError("Accepted invalid value or rejected mutation: " + value);
        } catch (InvocationTargetException e) {
            assert e.getCause() instanceof IllegalArgumentException : e.getCause();
            assert e.getCause().getMessage().contains(message) : e.getCause().getMessage();
        }
    }
}
