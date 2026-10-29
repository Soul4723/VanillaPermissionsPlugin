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

    public static final String SYNTAX_DENIED = "You can't use @ selectors such as @a.";
    public static final String UNRESOLVED = "You can't target that here.";

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

    /** The player-facing text. */
    public String message() {
        switch (reason) {
            case TOO_MANY: return "You can target at most " + limit + (limit == 1 ? " player or entity" : " players or entities") + " here.";
            case ENTITY: return "You can't target non-player entities here.";
            case PLAYER: return "You can't target other players here.";
            case SELF: return "You can't target yourself here.";
            case WEIGHT: return "You can't target players ranked above you.";
            case UNSUPPORTED: return UNRESOLVED;
            default: throw new IllegalStateException("unknown reason " + reason);
        }
    }
}
