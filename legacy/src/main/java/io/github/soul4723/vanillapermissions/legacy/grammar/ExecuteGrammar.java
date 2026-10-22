package io.github.soul4723.vanillapermissions.legacy.grammar;

import java.util.Set;

/**
 * Legacy {@code /execute <entity> <x> <y> <z> <command>} and
 * {@code /execute <entity> <x> <y> <z> detect <x> <y> <z> <block> <data> <command>}.
 *
 * <p>Modern equivalent: {@code execute as <targets> [if block <pos> <block>] run <command>}, so the path is
 * {@code execute.as.targets}, then {@code execute.if.block.pos.block} for {@code detect}, then {@code execute.run}
 * (each a new segment, because modern {@code as} and {@code if block} redirect back to the {@code execute} root). The
 * nested command is checked on its own, for the same player, with selectors resolved from each target.
 *
 * <p>Vanilla needs at least five tokens and takes {@code detect} only when it is exactly {@code detect} and more than
 * ten tokens follow; this mirrors that, so the nested command checked is the one vanilla runs.
 */
public final class ExecuteGrammar extends Grammar {

    private static final Set<String> PATHS = Tokens.paths(
        "execute.as.targets", "execute.if.block.pos.block", "execute.run");

    public ExecuteGrammar() {
        super(PATHS, "execute");
    }

    @Override
    public Parse parse(String command, String[] args) {
        Parse.Builder parse = Parse.root("execute");
        if (args.length == 0) return parse.build();
        parse.literal("as").entities("targets", 0);
        if (args.length < 5) return parse.build();
        int inner = 4;
        if (args[4].equals("detect") && args.length > 10) {
            parse.redirect("execute").literal("if").literal("block").argument("pos").argument("block");
            inner = 10;
        }
        return parse.redirect("execute").literal("run").innerCommand(inner).build();
    }
}
