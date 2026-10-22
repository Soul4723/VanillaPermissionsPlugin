package io.github.soul4723.vanillapermissions.legacy.feature;

import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code minecraft.bypass.force-gamemode} before 1.13. Modern Paper cancels the {@code DEFAULT_GAMEMODE} change, which
 * covers two cases (Paper's {@code MinecraftServer.java.patch} and {@code ServerPlayer.java.patch}); both are handled
 * here without an event cause:
 * <ul>
 *   <li>Join: these versions apply {@code force-gamemode} inside {@code EntityPlayer#a(NBTTagCompound)} while loading
 *   the player's data, with no event at all. The saved {@code playerGameType} is still in the playerdata file until the
 *   player is next saved, so it is read at join and put back. A first join has no file and keeps the forced mode. The
 *   client briefly shows the forced mode before the restore.</li>
 *   <li>{@code /defaultgamemode} with {@code force-gamemode} on: {@code CommandGamemodeDefault} sets every online
 *   player's mode, which fires a cause-less {@code PlayerGameModeChangeEvent}; it is recognised by that class on the
 *   call stack.</li>
 * </ul>
 */
final class ForceGamemodeBypassListener implements Listener {

    static final String BYPASS_PERMISSION = "minecraft.bypass.force-gamemode";

    private final Server server;
    private final Logger logger;
    private final Object console;
    private final Method getForceGamemode;

    private ForceGamemodeBypassListener(Server server, Logger logger, Object console, Method getForceGamemode) {
        this.server = server;
        this.logger = logger;
        this.console = console;
        this.getForceGamemode = getForceGamemode;
    }

    /**
     * Finds {@code MinecraftServer#getForceGamemode()} through {@code CraftServer#getServer()} (the same names on
     * v1_8_R3 through v1_12_R1) and reads it once, so a server where it differs fails here and not at every join.
     */
    static ForceGamemodeBypassListener create(Server server, Logger logger) throws ReflectiveOperationException {
        Object console = server.getClass().getMethod("getServer").invoke(server);
        if (console == null) throw new IllegalStateException("CraftServer#getServer() returned null");
        Method getForceGamemode = console.getClass().getMethod("getForceGamemode");
        if (getForceGamemode.getReturnType() != boolean.class) {
            throw new NoSuchMethodException(console.getClass().getName() + "#getForceGamemode() does not return boolean");
        }
        getForceGamemode.invoke(console);
        return new ForceGamemodeBypassListener(server, logger, console, getForceGamemode);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!forceGamemode() || !PermissionManager.hasPermission(player, BYPASS_PERMISSION)) return;
        File file = playerDataFile(player);
        if (file == null) return;
        Integer saved;
        try {
            saved = PlayerDataReader.readRootInt(file, "playerGameType");
        } catch (IOException e) {
            logger.warning("Could not read the saved game mode of " + player.getName() + " from " + file
                + ", so the force-gamemode bypass left the forced mode: " + e);
            return;
        }
        GameMode mode = restoredMode(saved, player.getGameMode());
        if (mode != null) player.setGameMode(mode);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (!fromDefaultGamemodeCommand(new Throwable().getStackTrace())) return;
        if (PermissionManager.hasPermission(event.getPlayer(), BYPASS_PERMISSION)) event.setCancelled(true);
    }

    /** Whether vanilla's {@code /defaultgamemode} (NMS {@code CommandGamemodeDefault}, 1.8 - 1.12) is on the stack. */
    static boolean fromDefaultGamemodeCommand(StackTraceElement[] stack) {
        for (StackTraceElement frame : stack) {
            String type = frame.getClassName();
            if (type.startsWith("net.minecraft.server.") && type.endsWith(".CommandGamemodeDefault")) return true;
        }
        return false;
    }

    /** The saved mode to restore, or {@code null} to keep the current one (no saved mode, an unknown id, or the same). */
    @SuppressWarnings("deprecation") // GameMode#getByValue is the 1.8.8 API's id lookup.
    static GameMode restoredMode(Integer saved, GameMode current) {
        if (saved == null) return null;
        GameMode mode = GameMode.getByValue(saved);
        return mode == null || mode == current ? null : mode;
    }

    private boolean forceGamemode() {
        try {
            return (Boolean) getForceGamemode.invoke(console);
        } catch (ReflectiveOperationException | RuntimeException e) {
            logger.log(Level.FINE, "Could not read force-gamemode", e);
            return false;
        }
    }

    /** CraftBukkit keeps every player's data in the main (first) world's {@code playerdata} folder. */
    private File playerDataFile(Player player) {
        List<World> worlds = server.getWorlds();
        if (worlds.isEmpty()) return null;
        return new File(new File(worlds.get(0).getWorldFolder(), "playerdata"), player.getUniqueId() + ".dat");
    }
}
