import io.github.soul4723.vanillapermissions.command.TimeCommandHandler;
import io.github.soul4723.vanillapermissions.command.WeatherCommandHandler;
import org.bukkit.World;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

// Run with assertions enabled and Maven's test classpath; no server required.
public class WorldCommandSendersCheck {
    private static final class WeatherState {
        boolean storm;
        boolean thunder;
        int weatherDuration;
        int thunderDuration;
        final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setStorm" -> { storm = (boolean) args[0]; yield null; }
                    case "setThundering" -> { thunder = (boolean) args[0]; yield null; }
                    case "setWeatherDuration" -> { weatherDuration = (int) args[0]; yield null; }
                    case "setThunderDuration" -> { thunderDuration = (int) args[0]; yield null; }
                    default -> throw new AssertionError("Unexpected World call: " + method);
                });
    }

    public static void main(String[] ignored) throws Exception {
        Method parseTime = TimeCommandHandler.class.getDeclaredMethod("parseTime", String.class);
        parseTime.setAccessible(true);
        assert (int) parseTime.invoke(null, "day") == 1_000;
        assert (int) parseTime.invoke(null, "noon") == 6_000;
        assert (int) parseTime.invoke(null, "night") == 13_000;
        assert (int) parseTime.invoke(null, "midnight") == 18_000;
        assert (int) parseTime.invoke(null, "24000") == 24_000;
        expectInvalidTime(parseTime, "-1");
        expectInvalidTime(parseTime, "tomorrow");

        Method setWeather = WeatherCommandHandler.class.getDeclaredMethod(
                "setWeather", World.class, String.class, Integer.class);
        setWeather.setAccessible(true);

        WeatherState state = new WeatherState();
        setWeather.invoke(null, state.world, "clear", 30);
        assert !state.storm && !state.thunder;
        assert state.weatherDuration == 600 && state.thunderDuration == 600;

        setWeather.invoke(null, state.world, "rain", 90);
        assert state.storm && !state.thunder;
        assert state.weatherDuration == 1_800 && state.thunderDuration == 1_800;

        setWeather.invoke(null, state.world, "thunder", 45);
        assert state.storm && state.thunder;
        assert state.weatherDuration == 900 && state.thunderDuration == 900;

        setWeather.invoke(null, state.world, "clear", null);
        assert state.weatherDuration >= 6_000 && state.weatherDuration <= 18_000;
        assert state.weatherDuration == state.thunderDuration;
        System.out.println("Time aliases/range and weather states/duration checks passed");
    }

    private static void expectInvalidTime(Method parseTime, String value) throws Exception {
        try {
            parseTime.invoke(null, value);
            throw new AssertionError("Accepted invalid time: " + value);
        } catch (InvocationTargetException e) {
            assert e.getCause() instanceof IllegalArgumentException : e.getCause();
        }
    }
}
