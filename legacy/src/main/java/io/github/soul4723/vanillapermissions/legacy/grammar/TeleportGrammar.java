package io.github.soul4723.vanillapermissions.legacy.grammar;

import java.util.Set;

/**
 * {@code /tp} and (from 1.10) {@code /teleport}. Modern {@code /tp} is a redirect to {@code /teleport}, so both map
 * onto the root {@code teleport}. That root is not checked: the legacy root is CraftBukkit's own check
 * ({@code minecraft.command.tp} or {@code minecraft.command.teleport}).
 *
 * <p>{@code /tp} picks its form by argument count, as {@code CommandTp.execute} does (1.8.8 and 1.12.2 agree):
 * <ul>
 *   <li>1: {@code <destination>} is {@code teleport.destination}; 2: {@code <target> <destination>} is
 *   {@code teleport.targets.destination}.</li>
 *   <li>3 or 5: the sender moves to {@code x y z [yaw pitch]}, {@code teleport.location}. Modern has no rotation node
 *   without targets, so 5 arguments stop at {@code location}.</li>
 *   <li>4 or 6: {@code <target> x y z [yaw pitch]}, {@code teleport.targets.location[.rotation]}.</li>
 *   <li>7 or more: not 2, 4 or 6, so vanilla reads token 0 as {@code x}, ignores the rest and moves the sender:
 *   {@code teleport.location}. Token 0 is no entity then.</li>
 *   <li>0: usage error, root only.</li>
 * </ul>
 *
 * <p>{@code /teleport <entity> x y z [yaw [pitch]]} ({@code CommandTeleport})
 * needs at least 4 arguments (otherwise usage error, root only) and is {@code teleport.targets.location}, plus
 * {@code .rotation} from the fifth argument on (yaw alone is accepted).
 */
public final class TeleportGrammar extends Grammar {

    /** Every path of {@code /tp}; {@code /teleport} (1.10+) only uses {@code targets.location[.rotation]} of them. */
    private static final Set<String> PATHS = Tokens.paths(
        "teleport.destination", "teleport.targets.destination", "teleport.location", "teleport.targets.location.rotation");

    public TeleportGrammar() {
        super(PATHS, "tp", "teleport");
    }

    @Override
    public Parse parse(String command, String[] args) {
        Parse.Builder parse = Parse.root("teleport");
        int count = args.length;
        if ("teleport".equalsIgnoreCase(command)) {
            if (count < 4) return parse.build();
            parse.entities("targets", 0).argument("location");
            if (count > 4) parse.argument("rotation");
            return parse.build();
        }
        switch (count) {
            case 0:
                break;
            case 1:
                parse.entities("destination", 0);
                break;
            case 2:
                parse.entities("targets", 0).entities("destination", 1);
                break;
            case 4:
                parse.entities("targets", 0).argument("location");
                break;
            case 6:
                parse.entities("targets", 0).argument("location").argument("rotation");
                break;
            default:
                // 3, 5, 7 and more: the sender moves; the modern tree has no rotation node without targets
                parse.argument("location");
                break;
        }
        return parse.build();
    }
}
