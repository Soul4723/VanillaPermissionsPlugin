package io.github.soul4723.vanillapermissions.legacy;

import io.github.soul4723.vanillapermissions.legacy.grammar.LegacyVersion;
import io.github.soul4723.vanillapermissions.selector.SelectorRules;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.logging.Logger;

import static io.github.soul4723.vanillapermissions.util.TestLuckPerms.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandListenerTest {

    /** The listener is the only check on 1.8.8 - 1.12.2, so a failing check must refuse, not let the command through. */
    @Test
    void failingCheckRefusesTheCommand() {
        List<String> messages = new ArrayList<>();
        Server server = proxy(Server.class, (p, m, a) -> m.getName().equals("getOnlinePlayers") ? Collections.emptyList() : null);
        Player player = proxy(Player.class, (p, m, a) -> switch (m.getName()) {
            case "sendMessage" -> messages.add((String) a[0]);
            case "getServer" -> server;
            default -> null;
        });
        CommandGuard.Server broken = new CommandGuard.Server() {
            @Override public Command command(String label) { throw new IllegalStateException("command map changed"); }
            @Override public boolean commandBlockOverride(String token) { return false; }
            @Override public List<String> scoreHolders() { return List.of(); }
            @Override public boolean isVanilla(Command command) { return true; }
            @Override public List<String> expandAlias(Command command, CommandSender sender, String[] args) { return null; }
            @Override public List<Entity> resolve(Entity origin, String token, boolean playersOnly) { return null; }
        };
        CommandGuard guard = new CommandGuard(broken, new SelectorRules.Permissions() {
            @Override public boolean hasPermission(CommandSender sender, String node, boolean fallback) { return fallback; }
            @Override public OptionalInt getIntegerMeta(UUID id, CommandSender subject, String node) { return OptionalInt.empty(); }
        }, LegacyVersion.V1_12_2);
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        CommandListener listener = new CommandListener(guard, logger);

        for (int i = 0; i < 2; i++) {
            PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/gamemode creative");
            listener.onCommand(event);
            assertTrue(event.isCancelled());
        }
        assertEquals(2, messages.size());
        assertTrue(messages.get(0).endsWith(CommandGuard.NO_PERMISSION));
    }
}
