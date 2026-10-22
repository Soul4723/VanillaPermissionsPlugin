package io.github.soul4723.vanillapermissions.legacy.grammar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a {@link Grammar} read from one typed command: the modern (1.13+) node path it maps to, and which typed
 * tokens hold entity arguments.
 *
 * <p>A parse is one or more {@link Segment}s. Most commands have one. A second segment stands for a modern redirect,
 * after which the path starts again at a root: legacy {@code execute <entity> <x y z> <command>} is modern
 * {@code execute as <targets>} (path {@code execute.as.targets}), then {@code execute run} (path {@code execute.run}),
 * so it has two segments.
 *
 * <p>Build one with {@link #root(String)}:
 * <pre>{@code
 * // effect <player> <effect> 30  ->  minecraft.command.effect.give.targets.effect.seconds
 * return Parse.root("effect").literal("give").entities("targets", 0).argument("effect").argument("seconds").build();
 * }</pre>
 */
public final class Parse {

    private final List<Segment> segments;
    private final int innerCommandStart;
    private final List<Integer> selectorTokens;

    private Parse(List<Segment> segments, int innerCommandStart, List<Integer> selectorTokens) {
        this.segments = Collections.unmodifiableList(segments);
        this.innerCommandStart = innerCommandStart;
        this.selectorTokens = Collections.unmodifiableList(new ArrayList<>(selectorTokens));
    }

    /** Starts a parse whose path begins at the modern command {@code name}, e.g. {@code teleport} for legacy /tp. */
    public static Builder root(String name) {
        return new Builder(name);
    }

    public List<Segment> segments() {
        return segments;
    }

    /** Index of the first token of a nested command (legacy /execute), or -1 if there is none. */
    public int innerCommandStart() {
        return innerCommandStart;
    }

    /** Selector-capable tokens, including legacy arguments with no modern permission node. */
    public List<Integer> selectorTokens() {
        List<Integer> tokens = new ArrayList<>(selectorTokens);
        for (Segment segment : segments) {
            for (Step step : segment.steps()) {
                if (!step.kind().isEntity()) continue;
                for (int i = 0; i < step.width(); i++) tokens.add(step.token() + i);
            }
        }
        return tokens;
    }

    /** Every permission node this parse checks, without the {@code minecraft.command.} prefix, in check order. */
    public List<String> checkedNodes() {
        List<String> nodes = new ArrayList<>();
        for (Segment segment : segments) nodes.addAll(segment.checkedNodes());
        return nodes;
    }

    /** The full path of each segment joined by {@code " | "}, e.g. {@code gamemode.creative.target}. For tests and logs. */
    public String describe() {
        StringBuilder text = new StringBuilder();
        for (Segment segment : segments) {
            if (text.length() > 0) text.append(" | ");
            text.append(segment.path());
        }
        return text.toString();
    }

    /** Kind of a parsed node. Entity kinds are resolved and checked against the selector rules. */
    public enum Kind {
        LITERAL,
        ARGUMENT,
        /** Any entity: a player name, a UUID or an {@code @} selector, resolved against every entity type. */
        ENTITIES,
        /** Players only: vanilla resolves the selector against players. */
        PLAYERS,
        /** Scoreboard holders: {@code @} selectors are resolved; plain names and {@code *} are score holders. */
        SCORE_HOLDERS;

        public boolean isEntity() {
            return this == ENTITIES || this == PLAYERS || this == SCORE_HOLDERS;
        }
    }

    /** One node of a path. Entity steps record the typed token they came from. */
    public static final class Step {
        private final String name;
        private final Kind kind;
        private final int token;
        private final int width;

        Step(String name, Kind kind, int token, int width) {
            this.name = name;
            this.kind = kind;
            this.token = token;
            this.width = width;
        }

        public String name() {
            return name;
        }

        public Kind kind() {
            return kind;
        }

        /** Index of the token in the argument array (after the label), or -1 for nodes without a token of their own. */
        public int token() {
            return token;
        }

        /** Number of typed tokens an entity step covers (each a name or selector); 0 for other nodes. */
        public int width() {
            return width;
        }

        @Override
        public String toString() {
            return name + (kind.isEntity() ? "@" + token + (width > 1 ? "+" + width : "") : "");
        }
    }

    /** A path from one modern root. */
    public static final class Segment {
        private final List<Step> steps;
        private final boolean checkRoot;

        Segment(List<Step> steps, boolean checkRoot) {
            this.steps = Collections.unmodifiableList(steps);
            this.checkRoot = checkRoot;
        }

        /** The nodes, starting with the root command (which has no token). */
        public List<Step> steps() {
            return steps;
        }

        public List<String> names() {
            List<String> names = new ArrayList<>(steps.size());
            for (Step step : steps) names.add(step.name);
            return names;
        }

        public String path() {
            return String.join(".", names());
        }

        /** The root (if requested through {@link Builder#checkRoot()}) and every longer prefix, as the modern tree checks them. */
        public List<String> checkedNodes() {
            List<String> nodes = new ArrayList<>();
            StringBuilder node = new StringBuilder(steps.get(0).name);
            if (checkRoot) nodes.add(node.toString());
            for (int i = 1; i < steps.size(); i++) {
                node.append('.').append(steps.get(i).name);
                nodes.add(node.toString());
            }
            return nodes;
        }
    }

    /** Builds a {@link Parse} node by node, in modern path order (which need not be the typed order). */
    public static final class Builder {
        private final List<Segment> segments = new ArrayList<>();
        private List<Step> steps = new ArrayList<>();
        private boolean checkRoot;
        private int innerCommandStart = -1;
        private final List<Integer> selectorTokens = new ArrayList<>();

        private Builder(String root) {
            steps.add(new Step(root, Kind.LITERAL, -1, 0));
        }

        /**
         * Also check {@code minecraft.command.<root>} for this segment. The legacy root is CraftBukkit's own check,
         * so this is only set where the modern root differs and modern servers check it too
         * (e.g. legacy {@code scoreboard teams} maps to modern {@code team}).
         */
        public Builder checkRoot() {
            checkRoot = true;
            return this;
        }

        /** A literal node, e.g. {@code creative} for a typed {@code c}, or {@code give} that legacy never types. */
        public Builder literal(String name) {
            steps.add(new Step(name, Kind.LITERAL, -1, 0));
            return this;
        }

        /** A plain argument node (a number, a name, coordinates...). */
        public Builder argument(String name) {
            steps.add(new Step(name, Kind.ARGUMENT, -1, 0));
            return this;
        }

        /** An entity argument at typed token {@code token}: names, UUIDs and selectors of any entity type. */
        public Builder entities(String name, int token) {
            return entity(name, Kind.ENTITIES, token, 1);
        }

        /** Adds consecutive argument nodes for the available tokens starting at {@code first}. */
        public Builder arguments(String[] args, int first, String... names) {
            for (int i = 0; i < names.length && first + i < args.length; i++) argument(names[i]);
            return this;
        }

        /** A player argument at typed token {@code token}: vanilla only selects players there. */
        public Builder players(String name, int token) {
            return entity(name, Kind.PLAYERS, token, 1);
        }

        /** A scoreboard holder argument at typed token {@code token} ({@code scoreboard players ...}). */
        public Builder scoreHolders(String name, int token) {
            return entity(name, Kind.SCORE_HOLDERS, token, 1);
        }

        /**
         * Scoreboard holders spread over {@code width} typed tokens, each a name, {@code *} or selector of its own.
         * Their targets are checked together.
         */
        public Builder scoreHolders(String name, int token, int width) {
            return entity(name, Kind.SCORE_HOLDERS, token, width);
        }

        private Builder entity(String name, Kind kind, int token, int width) {
            steps.add(new Step(name, kind, token, width));
            return this;
        }

        /** Ends the current segment and starts a new one at modern root {@code root}, as after a modern redirect. */
        public Builder redirect(String root) {
            segments.add(new Segment(steps, checkRoot));
            steps = new ArrayList<>();
            checkRoot = false;
            steps.add(new Step(root, Kind.LITERAL, -1, 0));
            return this;
        }

        /** Marks a selector-capable legacy argument that has no modern node. */
        public Builder selector(int token) {
            selectorTokens.add(token);
            return this;
        }

        /** Marks tokens from {@code token} on as a nested command to check on its own (legacy /execute). */
        public Builder innerCommand(int token) {
            innerCommandStart = token;
            return this;
        }

        public Parse build() {
            List<Segment> all = new ArrayList<>(segments);
            all.add(new Segment(steps, checkRoot));
            return new Parse(all, innerCommandStart, selectorTokens);
        }
    }
}
