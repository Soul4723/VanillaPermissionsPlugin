package io.github.soul4723.vanillapermissions.util;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

import static io.github.soul4723.vanillapermissions.util.TestLuckPerms.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Vault chat meta lookups against stub Chat and Permission services; no Vault plugin or server. */
class VaultHookTest {
    private static final String KEY = "minecraft.selector.limit.minecraft.stone";

    private final UUID id = UUID.randomUUID();
    private final Map<String, Integer> values = new HashMap<>();
    private boolean chatFails;
    private int chatCalls;
    private final Plugin owner = proxy(Plugin.class, (p, m, a) -> { throw new AssertionError(m); });
    private final Map<Class<?>, Object> services = new HashMap<>();
    private Player online;
    private final Server server = proxy(Server.class, (p, m, a) -> switch (m.getName()) {
        case "getServicesManager" -> proxy(ServicesManager.class, (sp, sm, sa) -> {
            if (!sm.getName().equals("getRegistration")) throw new AssertionError(sm);
            return register((Class<?>) sa[0]);
        });
        case "getPlayer" -> a[0].equals(id) ? online : null;
        default -> throw new AssertionError("Unexpected server call: " + m);
    });

    private final Chat chat = new Chat(null) {
        @Override public String getName() { return "TestChat"; }
        @Override public boolean isEnabled() { return true; }
        @Override public int getPlayerInfoInteger(String world, String player, String node, int def) {
            chatCalls++;
            if (chatFails) throw new IllegalStateException("Chat unavailable");
            return values.getOrDefault(node, def);
        }
        @Override public String getPlayerPrefix(String world, String player) { throw new AssertionError(); }
        @Override public void setPlayerPrefix(String world, String player, String prefix) { throw new AssertionError(); }
        @Override public String getPlayerSuffix(String world, String player) { throw new AssertionError(); }
        @Override public void setPlayerSuffix(String world, String player, String suffix) { throw new AssertionError(); }
        @Override public String getGroupPrefix(String world, String group) { throw new AssertionError(); }
        @Override public void setGroupPrefix(String world, String group, String prefix) { throw new AssertionError(); }
        @Override public String getGroupSuffix(String world, String group) { throw new AssertionError(); }
        @Override public void setGroupSuffix(String world, String group, String suffix) { throw new AssertionError(); }
        @Override public void setPlayerInfoInteger(String world, String player, String node, int value) { throw new AssertionError(); }
        @Override public int getGroupInfoInteger(String world, String group, String node, int def) { throw new AssertionError(); }
        @Override public void setGroupInfoInteger(String world, String group, String node, int value) { throw new AssertionError(); }
        @Override public double getPlayerInfoDouble(String world, String player, String node, double def) { throw new AssertionError(); }
        @Override public void setPlayerInfoDouble(String world, String player, String node, double value) { throw new AssertionError(); }
        @Override public double getGroupInfoDouble(String world, String group, String node, double def) { throw new AssertionError(); }
        @Override public void setGroupInfoDouble(String world, String group, String node, double value) { throw new AssertionError(); }
        @Override public boolean getPlayerInfoBoolean(String world, String player, String node, boolean def) { throw new AssertionError(); }
        @Override public void setPlayerInfoBoolean(String world, String player, String node, boolean value) { throw new AssertionError(); }
        @Override public boolean getGroupInfoBoolean(String world, String group, String node, boolean def) { throw new AssertionError(); }
        @Override public void setGroupInfoBoolean(String world, String group, String node, boolean value) { throw new AssertionError(); }
        @Override public String getPlayerInfoString(String world, String player, String node, String def) { throw new AssertionError(); }
        @Override public void setPlayerInfoString(String world, String player, String node, String value) { throw new AssertionError(); }
        @Override public String getGroupInfoString(String world, String group, String node, String def) { throw new AssertionError(); }
        @Override public void setGroupInfoString(String world, String group, String node, String value) { throw new AssertionError(); }
    };

    private <T> RegisteredServiceProvider<?> register(Class<T> type) {
        Object service = services.get(type);
        return service == null ? null
            : new RegisteredServiceProvider<>(type, type.cast(service), ServicePriority.Normal, owner);
    }

    private Player player(UUID uuid) {
        World world = proxy(World.class, (p, m, a) -> {
            if (!m.getName().equals("getName")) throw new AssertionError(m);
            return "world";
        });
        return proxy(Player.class, (p, m, a) -> switch (m.getName()) {
            case "getUniqueId" -> uuid;
            case "getWorld" -> world;
            case "getName" -> "Steve";
            default -> throw new AssertionError("Unexpected player call: " + m);
        });
    }

    private VaultHook connect() {
        services.put(Chat.class, chat);
        VaultHook hook = VaultHook.connect(server, null);
        assertNotNull(hook, "No hook despite a Chat registration");
        return hook;
    }

    @Test
    void presentValueIsReturned() {
        values.put(KEY, 12);
        Player player = player(id);
        assertEquals(OptionalInt.of(12), connect().get(id, player, KEY));
    }

    @Test
    void absentValueIsEmpty() {
        assertEquals(OptionalInt.empty(), connect().get(id, player(id), KEY), "Vault default leaked as a value");
    }

    @Test
    void zeroIsAValue() {
        values.put(KEY, 0);
        assertEquals(OptionalInt.of(0), connect().get(id, player(id), KEY));
    }

    @Test
    void otherOnlinePlayerIsLookedUpByIdentifier() {
        values.put(KEY, 5);
        online = player(id);
        assertEquals(OptionalInt.of(5), connect().get(id, player(UUID.randomUUID()), KEY));
        assertEquals(OptionalInt.of(5), connect().get(id, null, KEY));
    }

    @Test
    void offlineSubjectIsEmptyWithoutQueryingChat() {
        values.put(KEY, 5);
        assertEquals(OptionalInt.empty(), connect().get(id, null, KEY));
        assertEquals(0, chatCalls, "Offline subject reached Chat");
    }

    @Test
    void chatFailureIsEmpty() {
        chatFails = true;
        assertEquals(OptionalInt.empty(), connect().get(id, player(id), KEY));
        assertEquals(1, chatCalls, "Chat was not queried");
    }

    @Test
    void connectNeedsAChatRegistration() {
        assertNull(VaultHook.connect(server, null), "Hook without a Chat registration");
    }

    @Test
    void chatNameIsReported() {
        VaultHook hook = connect();
        assertEquals("TestChat", hook.getChatName());
        assertEquals("§aVault chat meta §7(TestChat)", hook.describe());
    }

    @Test
    void permissionsPluginNamesTheBackingPlugin() {
        assertNull(VaultHook.permissionsPlugin(server), "Name without a Permission registration");
        services.put(Permission.class, permission("SuperPerms"));
        assertNull(VaultHook.permissionsPlugin(server), "SuperPerms names no permissions plugin");
        services.put(Permission.class, permission("GroupManager"));
        assertEquals("GroupManager", VaultHook.permissionsPlugin(server));
    }

    @Test
    void loginPermissionsReadThePluginsStoredData() {
        assertNull(VaultHook.loginPermissions(server), "Login lookup without a Permission registration");
        services.put(Permission.class, permission("SuperPerms"));
        assertNull(VaultHook.loginPermissions(server), "SuperPerms only repeats Player#hasPermission");
        services.put(Permission.class, permission("GroupManager", java.util.Set.of("world/Steve/minecraft.bypass.whitelist")));
        BukkitPermissionProvider.LoginPermissions login = VaultHook.loginPermissions(server);
        assertNotNull(login, "No login lookup for a real permissions plugin");
        Player steve = player(id);
        assertEquals(true, login.has(steve, "minecraft.bypass.whitelist"));
        assertEquals(false, login.has(steve, "minecraft.bypass.player-limit"));
    }

    static Permission permission(String name) {
        return permission(name, null);
    }

    /** {@code granted} answers {@code playerHas} as "world/player/node"; {@code null} means it must not be called. */
    static Permission permission(String name, java.util.Set<String> granted) {
        return new Permission() {
            @Override public String getName() { return name; }
            @Override public boolean isEnabled() { return true; }
            @Override public boolean hasSuperPermsCompat() { return true; }
            @Override public boolean playerHas(String world, String player, String permission) {
                if (granted == null) throw new AssertionError("playerHas called");
                return granted.contains(world + "/" + player + "/" + permission);
            }
            @Override public boolean playerAdd(String world, String player, String permission) { throw new AssertionError(); }
            @Override public boolean playerRemove(String world, String player, String permission) { throw new AssertionError(); }
            @Override public boolean groupHas(String world, String group, String permission) { throw new AssertionError(); }
            @Override public boolean groupAdd(String world, String group, String permission) { throw new AssertionError(); }
            @Override public boolean groupRemove(String world, String group, String permission) { throw new AssertionError(); }
            @Override public boolean playerInGroup(String world, String player, String group) { throw new AssertionError(); }
            @Override public boolean playerAddGroup(String world, String player, String group) { throw new AssertionError(); }
            @Override public boolean playerRemoveGroup(String world, String player, String group) { throw new AssertionError(); }
            @Override public String[] getPlayerGroups(String world, String player) { throw new AssertionError(); }
            @Override public String getPrimaryGroup(String world, String player) { throw new AssertionError(); }
            @Override public String[] getGroups() { throw new AssertionError(); }
            @Override public boolean hasGroupSupport() { return false; }
        };
    }
}
