package io.github.soul4723.vanillapermissions.legacy.grammar;

import java.util.Set;

/**
 * {@code /weather <clear|rain|thunder> [duration]}. Modern nodes (1.21.11): {@code weather.<clear|rain|thunder>} and
 * {@code weather.<...>.duration}.
 *
 * <p>Verified against CommandWeather of 1.8.8 and 1.12.2 (identical): the keyword is token 0, compared with
 * {@code equalsIgnoreCase}; the duration is token 1 (seconds). Vanilla rejects more than two tokens with a usage
 * error; extra tokens are ignored here, which only adds a check. 1.9.4 - 1.11.2 are assumed to behave the same.
 */
public final class WeatherGrammar extends Grammar {

    private static final String[] KEYWORDS = {"clear", "rain", "thunder"};

    private static final Set<String> PATHS = Tokens.paths(
        "weather.clear.duration", "weather.rain.duration", "weather.thunder.duration");

    public WeatherGrammar() {
        super(PATHS, "weather");
    }

    @Override
    public Parse parse(String command, String[] args) {
        Parse.Builder parse = Parse.root("weather");
        String token = Tokens.at(args, 0);
        for (String keyword : KEYWORDS) {
            if (Tokens.is(token, keyword)) {
                parse.literal(keyword);
                if (args.length > 1) parse.argument("duration");
                break;
            }
        }
        return parse.build();
    }
}
