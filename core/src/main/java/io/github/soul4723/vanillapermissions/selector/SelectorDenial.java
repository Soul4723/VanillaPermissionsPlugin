package io.github.soul4723.vanillapermissions.selector;

/**
 * Why {@link SelectorRules#check} refused a selection. Adapters turn it into their own error type
 * (a Brigadier {@code CommandSyntaxException}, a chat message on pre-1.13 servers) using {@link #message()}.
 */
public final class SelectorDenial {

    /** The rule that refused the selection. */
    public enum Reason {
        /** More targets than {@code minecraft.selector.limit.<path>} allows; see {@link #limit()}. */
        TOO_MANY,
        /** A non-player entity while {@code minecraft.selector.entity.<path>} is false. */
        ENTITY,
        /** Another player while {@code minecraft.selector.player.<path>} is false. */
        PLAYER,
        /** The sender while {@code minecraft.selector.self.<path>} is false. */
        SELF,
        /** A player whose {@code minecraft.selector.weight.<path>} is higher than the sender's. */
        WEIGHT,
        /** A target the rules cannot classify. */
        UNSUPPORTED
    }

    public static final String SYNTAX_DENIED = "You do not have permission to use target selectors";
    public static final String UNRESOLVED = "The selected targets could not be checked against selector permissions";

    private final Reason reason;
    private final int limit;

    private SelectorDenial(Reason reason, int limit) {
        this.reason = reason;
        this.limit = limit;
    }

    /** Denial for a selection larger than {@code limit}. */
    static SelectorDenial tooMany(int limit) {
        return new SelectorDenial(Reason.TOO_MANY, limit);
    }

    /** Denial for any reason except {@link Reason#TOO_MANY}, which needs a limit. */
    static SelectorDenial of(Reason reason) {
        return new SelectorDenial(reason, 0);
    }

    public Reason reason() {
        return reason;
    }

    /** The configured limit for {@link Reason#TOO_MANY}; 0 for every other reason. */
    public int limit() {
        return limit;
    }

    /** The player-facing text, identical to the 1.0.0 Brigadier error messages. */
    public String message() {
        switch (reason) {
            case TOO_MANY: return "You can select at most " + limit + (limit == 1 ? " target" : " targets") + " here";
            case ENTITY: return "You do not have permission to target non-player entities here";
            case PLAYER: return "You do not have permission to target other players here";
            case SELF: return "You do not have permission to target yourself here";
            case WEIGHT: return "You do not have permission to target players with a higher selector weight than yours";
            case UNSUPPORTED: return "This target cannot be checked against selector permissions, so it is not allowed";
            default: throw new IllegalStateException("unknown reason " + reason);
        }
    }
}
