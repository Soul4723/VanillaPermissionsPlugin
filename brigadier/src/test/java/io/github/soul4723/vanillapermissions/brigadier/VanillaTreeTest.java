package io.github.soul4723.vanillapermissions.brigadier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.tree.CommandNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Parsed-node access that also works on Brigadier 1.0.14 (Minecraft 1.13.x), whose getNodes() is a map. */
class VanillaTreeTest {

    @Test
    void parsedNodesMatchBrigadiersOwnList() {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        dispatcher.register(literal("gamemode").then(literal("creative").then(argument("target", word()).executes(c -> 1))));
        String input = "gamemode creative Bob";
        CommandContext<Object> context = dispatcher.parse(input, new Object()).getContext().build(input);

        List<VanillaTree.ParsedNode> nodes = VanillaTree.parsedNodes(context);
        List<ParsedCommandNode<Object>> expected = context.getNodes();
        assertEquals(expected.size(), nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            assertSame(expected.get(i).getNode(), nodes.get(i).node());
            assertEquals(expected.get(i).getRange(), nodes.get(i).range());
        }
        assertEquals(3, nodes.size());
        assertEquals("Bob", nodes.get(2).range().get(input));
    }

    @Test
    void mapFormKeepsInputOrder() {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        dispatcher.register(literal("time").then(literal("set").then(argument("time", word()))));
        CommandNode<Object> time = dispatcher.getRoot().getChild("time");
        CommandNode<Object> set = time.getChild("set");
        CommandNode<Object> value = set.getChild("time");
        Map<CommandNode<Object>, StringRange> map = new LinkedHashMap<>();
        map.put(time, StringRange.between(0, 4));
        map.put(set, StringRange.between(5, 8));
        map.put(value, StringRange.between(9, 12));

        List<String> names = new ArrayList<>();
        for (VanillaTree.ParsedNode node : VanillaTree.fromMap(map)) names.add(node.node().getName() + "@" + node.range().getStart());
        assertEquals(List.of("time@0", "set@5", "time@9"), names);
    }

    /**
     * Brigadier 1.0.14 puts the redirect target first in the child context's node map: the command for {@code /tp},
     * the dispatcher root for {@code execute run}. Neither may leave the command name out of the selector path.
     */
    @Test
    void mapFormPathsAfterRedirectsStartWithTheCommand() {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        dispatcher.register(literal("gamemode").then(literal("creative").then(argument("target", word()))));
        dispatcher.register(literal("teleport").then(argument("destination", word())));
        CommandNode<Object> root = dispatcher.getRoot();
        CommandNode<Object> creative = root.getChild("gamemode").getChild("creative");
        Map<CommandNode<Object>, StringRange> afterRun = new LinkedHashMap<>();
        afterRun.put(root, StringRange.between(8, 11));
        afterRun.put(root.getChild("gamemode"), StringRange.between(12, 20));
        afterRun.put(creative, StringRange.between(21, 29));
        afterRun.put(creative.getChild("target"), StringRange.between(30, 33));
        assertEquals(List.of("gamemode", "creative", "target"), VanillaTree.path(null, VanillaTree.fromMap(afterRun)));
        Map<CommandNode<Object>, StringRange> afterTp = new LinkedHashMap<>();
        afterTp.put(root.getChild("teleport"), StringRange.between(0, 2));
        afterTp.put(root.getChild("teleport").getChild("destination"), StringRange.between(3, 6));
        assertEquals(List.of("teleport", "destination"), VanillaTree.path(null, VanillaTree.fromMap(afterTp)));
    }
}
