package io.github.soul4723.vanillapermissions.legacy.feature;

import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code minecraft.bypass.move-speed.player} before 1.13, where there is no {@code PlayerFailMoveEvent}: the "moved too
 * quickly" and "moved wrongly" limits are static {@code org.spigotmc.SpigotConfig} fields read inside
 * {@code PlayerConnection#a(PacketPlayInFlying)}.
 *
 * <p>A Netty handler sits just before {@code packet_handler} in each player's pipeline (the TinyProtocol pattern:
 * ProtocolLib, {@code TinyProtocol/src/main/java/com/comphenix/tinyprotocol/TinyProtocol.java}). For a player who had
 * the node at the last tick sample, it takes the move packet out of the pipeline and posts the vanilla handler to the
 * main thread itself, through the same {@code MinecraftServer#postToMainThread} queue that vanilla's
 * {@code PlayerConnectionUtils.ensureMainThread} uses, so the order with the player's other packets is kept. On the
 * main thread the node is checked again and, only if it is still granted, the thresholds are raised for the length of
 * that one call and restored in {@code finally}. Every other packet, and every packet of a player without the node,
 * passes through untouched.
 *
 * <p>All Netty and NMS access is reflective; every member is looked up in {@link #install} so a server whose names
 * differ fails at enable. A later failure turns the feature off for the session with one warning and removes the
 * handlers. The handler only adds or removes itself as a single pipeline operation, so a pipeline is never left
 * half-modified.
 *
 * <p>Limitations: vehicle moves ({@code PacketPlayInVehicleMove}, 1.9+) are not covered; a handler another plugin
 * inserts before {@code packet_handler} after this one does not see move packets of players with the node.
 */
final class MoveSpeedBypass implements Listener {

    static final String PERMISSION = "minecraft.bypass.move-speed.player";
    private static final String HANDLER_NAME = "vanillapermissions_move_speed";
    private static final String PACKET_HANDLER = "packet_handler";

    private final Logger logger;
    private final Access access;
    /** Players who had the node at the last sample; read on Netty threads. */
    private final Set<UUID> bypass = ConcurrentHashMap.newKeySet();
    /** Installed handlers by player, to remove exactly the handler this instance added. */
    private final Map<UUID, Installed> installed = new ConcurrentHashMap<>();
    private final AtomicBoolean failed = new AtomicBoolean();
    private volatile boolean active = true;
    private volatile BukkitTask sampler;

    private MoveSpeedBypass(Logger logger, Access access) {
        this.logger = logger;
        this.access = access;
    }

    /** Looks every member up and hooks the players already online; throws if this server's names differ. */
    static MoveSpeedBypass install(Plugin plugin) throws ReflectiveOperationException {
        MoveSpeedBypass bypass = new MoveSpeedBypass(plugin.getLogger(), new Access(plugin.getServer()));
        // Permissions can change at any time; sample them on the main thread once a tick.
        bypass.sampler = plugin.getServer().getScheduler().runTaskTimer(plugin, bypass::sample, 1L, 1L);
        for (Player player : plugin.getServer().getOnlinePlayers()) bypass.inject(player);
        return bypass;
    }

    /** Removes every handler this instance added; safe to call more than once. */
    void uninstall() {
        active = false;
        BukkitTask task = sampler;
        sampler = null;
        if (task != null) task.cancel();
        for (UUID id : new ArrayList<>(installed.keySet())) eject(id);
        bypass.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        inject(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        bypass.remove(id);
        eject(id);
    }

    private void sample() {
        if (!active) return;
        for (Player player : access.server.getOnlinePlayers()) {
            if (PermissionManager.hasPermission(player, PERMISSION)) bypass.add(player.getUniqueId());
            else bypass.remove(player.getUniqueId());
        }
    }

    private void inject(Player player) {
        if (!active) return;
        eject(player.getUniqueId());
        Object connection;
        Object pipeline;
        try {
            connection = access.connection(player);
            pipeline = connection == null ? null : access.pipeline(connection);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            fail("could not reach the network connection of " + player.getName(), e);
            return;
        }
        if (pipeline == null) return; // A plugin's fake player (an NPC) without a real connection.
        try {
            if (access.pipelineGet.invoke(pipeline, PACKET_HANDLER) == null) return; // Not a vanilla network pipeline.
            Object handler = access.newHandler(new Interceptor(player, connection));
            if (access.pipelineGet.invoke(pipeline, HANDLER_NAME) != null) {
                // Left behind by an earlier instance (a reload that skipped onDisable): swap it in one operation.
                access.pipelineReplace.invoke(pipeline, HANDLER_NAME, HANDLER_NAME, handler);
            } else {
                access.pipelineAddBefore.invoke(pipeline, PACKET_HANDLER, HANDLER_NAME, handler);
            }
            installed.put(player.getUniqueId(), new Installed(pipeline, handler));
            if (!active) eject(player.getUniqueId()); // fail() ran on a Netty thread meanwhile.
        } catch (InvocationTargetException e) {
            // Netty refused, for example because the connection closed meanwhile; the pipeline is unchanged.
            logger.log(Level.FINE, "Could not add the move-speed handler for " + player.getName(), e.getCause());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            fail("could not add the move-speed handler for " + player.getName(), e);
        }
    }

    private void eject(UUID id) {
        Installed entry = installed.remove(id);
        if (entry == null) return;
        try {
            access.pipelineRemove.invoke(entry.pipeline, entry.handler);
        } catch (InvocationTargetException e) {
            // Already gone with the closed channel.
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logger.log(Level.FINE, "Could not remove the move-speed handler", e);
        }
    }

    /** Logs one warning and turns the feature off for this session; other features are unaffected. */
    private void fail(String what, Throwable cause) {
        if (!failed.compareAndSet(false, true)) return;
        logger.warning("The move-speed bypass (" + PERMISSION + ") is off until the next restart: " + what + ": " + cause);
        logger.log(Level.FINE, "Move-speed bypass failure", cause);
        uninstall();
    }

    /** Runs on the main thread, in the player's packet order. */
    private void handleOnMainThread(Player player, Object connection, Object packet) {
        double[] saved = null;
        try {
            if (active && PermissionManager.hasPermission(player, PERMISSION)) saved = access.raiseThresholds();
        } catch (IllegalAccessException | RuntimeException e) {
            fail("could not raise the SpigotConfig movement thresholds", e); // The move is still handled, unbypassed.
        }
        try {
            access.handleFlying.invoke(connection, packet);
        } catch (InvocationTargetException e) {
            // Thrown by vanilla's own handler: rethrow it as vanilla's posted task would, for the server to log.
            throw Access.<RuntimeException>sneaky(e.getCause());
        } catch (IllegalAccessException | RuntimeException e) {
            fail("could not run the move handler", e);
        } finally {
            if (saved != null) access.restoreThresholds(saved, logger);
        }
    }

    private static final class Installed {
        final Object pipeline;
        final Object handler;

        Installed(Object pipeline, Object handler) {
            this.pipeline = pipeline;
            this.handler = handler;
        }
    }

    /** A {@code ChannelInboundHandler} that forwards every event to the next handler, except bypassed move packets. */
    private final class Interceptor implements InvocationHandler {
        private final Player player;
        private final UUID id;
        private final Object connection;

        Interceptor(Player player, Object connection) {
            this.player = player;
            this.id = player.getUniqueId();
            this.connection = connection;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class) {
                if (name.equals("equals")) return proxy == args[0];
                if (name.equals("hashCode")) return System.identityHashCode(proxy);
                return "VanillaPermissions move-speed handler for " + player.getName();
            }
            if (name.equals("channelRead") && active && access.flying.isInstance(args[1]) && bypass.contains(id)) {
                final Object packet = args[1];
                try {
                    access.postToMainThread.invoke(access.console, (Runnable) () -> handleOnMainThread(player, connection, packet));
                    return null;
                } catch (ReflectiveOperationException | RuntimeException e) {
                    fail("could not post a move to the main thread", e); // Then pass the packet on as usual.
                }
            }
            Method fire = access.fireMethods.get(name);
            if (fire == null) return null; // handlerAdded and handlerRemoved: nothing to do.
            try {
                fire.invoke(args[0], Arrays.copyOfRange(args, 1, args.length));
                return null;
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    /** The reflective members, all looked up at install. Same names on v1_8_R3 through v1_12_R1, Spigot and Paper. */
    static final class Access {
        final Server server;
        final Object console;
        final Method postToMainThread;
        final Class<?> flying;
        final Method handleFlying;
        private final Class<?> craftPlayer;
        private final Method getHandle;
        private final Field playerConnection;
        private final Field networkManager;
        private final Field channel;
        private final Method channelPipeline;
        final Method pipelineGet;
        final Method pipelineAddBefore;
        final Method pipelineReplace;
        final Method pipelineRemove;
        private final Class<?> inboundHandler;
        /** {@code ChannelInboundHandler} method name to the {@code ChannelHandlerContext#fire...} method it forwards to. */
        final Map<String, Method> fireMethods = new HashMap<>();
        private final Field[] thresholds;

        Access(Server server) throws ReflectiveOperationException {
            this.server = server;
            String craft = server.getClass().getPackage().getName(); // org.bukkit.craftbukkit.v1_8_R3
            String nms = "net.minecraft.server." + craft.substring(craft.lastIndexOf('.') + 1);
            ClassLoader loader = server.getClass().getClassLoader();

            console = server.getClass().getMethod("getServer").invoke(server);
            if (console == null) throw new IllegalStateException("CraftServer#getServer() returned null");
            postToMainThread = console.getClass().getMethod("postToMainThread", Runnable.class);
            flying = Class.forName(nms + ".PacketPlayInFlying", false, loader);
            Class<?> connectionType = Class.forName(nms + ".PlayerConnection", false, loader);
            handleFlying = connectionType.getMethod("a", flying);
            craftPlayer = Class.forName(craft + ".entity.CraftPlayer", false, loader);
            getHandle = craftPlayer.getMethod("getHandle");
            playerConnection = Class.forName(nms + ".EntityPlayer", false, loader).getField("playerConnection");
            networkManager = connectionType.getField("networkManager");

            Class<?> channelType = Class.forName("io.netty.channel.Channel", false, loader);
            channel = fieldOfType(networkManager.getType(), "channel", channelType);
            channelPipeline = channelType.getMethod("pipeline");
            Class<?> pipelineType = Class.forName("io.netty.channel.ChannelPipeline", false, loader);
            Class<?> handlerType = Class.forName("io.netty.channel.ChannelHandler", false, loader);
            pipelineGet = pipelineType.getMethod("get", String.class);
            pipelineAddBefore = pipelineType.getMethod("addBefore", String.class, String.class, handlerType);
            pipelineReplace = pipelineType.getMethod("replace", String.class, String.class, handlerType);
            pipelineRemove = pipelineType.getMethod("remove", handlerType);

            inboundHandler = Class.forName("io.netty.channel.ChannelInboundHandler", false, loader);
            Class<?> contextType = Class.forName("io.netty.channel.ChannelHandlerContext", false, loader);
            for (Method method : inboundHandler.getMethods()) {
                String name = method.getName();
                if (name.equals("handlerAdded") || name.equals("handlerRemoved")) continue;
                Class<?>[] types = method.getParameterTypes();
                if (types.length == 0 || types[0] != contextType) throw new NoSuchMethodException("unexpected Netty handler method " + method);
                String fire = "fire" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
                fireMethods.put(name, contextType.getMethod(fire, Arrays.copyOfRange(types, 1, types.length)));
            }
            if (!fireMethods.containsKey("channelRead")) throw new NoSuchMethodException("no ChannelInboundHandler#channelRead");

            Class<?> spigotConfig = Class.forName("org.spigotmc.SpigotConfig", false, loader);
            List<Field> found = new ArrayList<>();
            boolean tooQuickly = false;
            // 1.8 has movedTooQuicklyThreshold, 1.9+ movedTooQuicklyMultiplier; both have movedWronglyThreshold.
            for (String name : new String[] {"movedTooQuicklyThreshold", "movedTooQuicklyMultiplier", "movedWronglyThreshold"}) {
                Field field;
                try {
                    field = spigotConfig.getField(name);
                } catch (NoSuchFieldException e) {
                    continue;
                }
                int modifiers = field.getModifiers();
                if (field.getType() != double.class || !Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
                    throw new NoSuchFieldException("SpigotConfig." + name + " is not a static double");
                }
                found.add(field);
                tooQuickly |= name.startsWith("movedTooQuickly");
            }
            if (!tooQuickly) throw new NoSuchFieldException("no SpigotConfig moved-too-quickly setting");
            thresholds = found.toArray(new Field[0]);
        }

        /** The player's {@code PlayerConnection}, or {@code null} for a player without one. */
        Object connection(Player player) throws ReflectiveOperationException {
            if (!craftPlayer.isInstance(player)) return null;
            Object handle = getHandle.invoke(player);
            return handle == null ? null : playerConnection.get(handle);
        }

        /** The connection's Netty pipeline, or {@code null} if it has no channel. */
        Object pipeline(Object connection) throws ReflectiveOperationException {
            Object manager = networkManager.get(connection);
            Object netty = manager == null ? null : channel.get(manager);
            return netty == null ? null : channelPipeline.invoke(netty);
        }

        Object newHandler(InvocationHandler handler) {
            return Proxy.newProxyInstance(inboundHandler.getClassLoader(), new Class<?>[] {inboundHandler}, handler);
        }

        /** Sets every threshold to the maximum and returns the previous values; restores them all if a write fails. */
        double[] raiseThresholds() throws IllegalAccessException {
            double[] saved = new double[thresholds.length];
            for (int i = 0; i < thresholds.length; i++) saved[i] = thresholds[i].getDouble(null);
            int raised = 0;
            try {
                for (; raised < thresholds.length; raised++) thresholds[raised].setDouble(null, Double.MAX_VALUE);
            } finally {
                if (raised < thresholds.length) {
                    for (int i = 0; i < raised; i++) thresholds[i].setDouble(null, saved[i]);
                }
            }
            return saved;
        }

        void restoreThresholds(double[] saved, Logger logger) {
            for (int i = 0; i < thresholds.length; i++) {
                try {
                    thresholds[i].setDouble(null, saved[i]);
                } catch (IllegalAccessException | RuntimeException e) {
                    logger.log(Level.SEVERE, "Could not restore SpigotConfig." + thresholds[i].getName(), e);
                }
            }
        }

        private static Field fieldOfType(Class<?> owner, String name, Class<?> type) throws NoSuchFieldException {
            try {
                Field field = owner.getField(name);
                if (type.isAssignableFrom(field.getType())) return field;
            } catch (NoSuchFieldException e) {
                // Look for it by type below.
            }
            Field match = null;
            for (Field field : owner.getDeclaredFields()) {
                if (type.isAssignableFrom(field.getType()) && !Modifier.isStatic(field.getModifiers())) {
                    if (match != null) throw new NoSuchFieldException("two " + type.getSimpleName() + " fields in " + owner.getName());
                    match = field;
                }
            }
            if (match == null) throw new NoSuchFieldException("no " + type.getSimpleName() + " field in " + owner.getName());
            match.setAccessible(true);
            return match;
        }

        @SuppressWarnings("unchecked")
        static <T extends Throwable> T sneaky(Throwable throwable) throws T {
            throw (T) throwable;
        }
    }
}
