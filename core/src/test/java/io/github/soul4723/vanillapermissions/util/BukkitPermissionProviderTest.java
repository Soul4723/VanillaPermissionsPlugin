package io.github.soul4723.vanillapermissions.util;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

import static io.github.soul4723.vanillapermissions.util.TestLuckPerms.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tri-state fallback and meta delegation of the Bukkit provider; players are proxies, no server or LuckPerms. */
class BukkitPermissionProviderTest {
    private static final String NODE = "minecraft.command.time.set";

    private final UUID id = UUID.randomUUID();
    private final Map<String, Boolean> setNodes = new HashMap<>();
    private boolean operator, setFails;
    private Field providerField;
    private Object previousProvider;
    private final Player player = proxy(Player.class, (p, m, a) -> switch (m.getName()) {
        case "isPermissionSet" -> {
            if (setFails) throw new IllegalStateException("Permission provider unavailable");
            yield setNodes.containsKey((String) a[0]);
        }
        case "hasPermission" -> setNodes.getOrDefault((String) a[0], operator);
        case "getUniqueId" -> id;
        default -> throw new AssertionError("Unexpected player call: " + m);
    });
    private final BukkitPermissionProvider provider = new BukkitPermissionProvider("TestPerms", null, null);

    @BeforeEach
    void installProvider() throws Exception {
        providerField = PermissionManager.class.getDeclaredField("provider");
        providerField.setAccessible(true);
        previousProvider = providerField.get(null);
        providerField.set(null, provider);
    }

    @AfterEach
    void restoreProvider() throws Exception {
        providerField.set(null, previousProvider);
    }

    @Test
    void setNodesKeepTheirValue() {
        setNodes.put(NODE, true);
        setNodes.put("minecraft.command.time.add", false);
        assertEquals(PermissionValue.TRUE, PermissionManager.queryPermission(player, NODE));
        assertEquals(PermissionValue.FALSE, PermissionManager.queryPermission(player, "minecraft.command.time.add"));
    }

    @Test
    void unsetNodeIsUndefinedEvenForOperators() {
        operator = true;
        assertEquals(PermissionValue.UNDEFINED, PermissionManager.queryPermission(player, NODE),
            "Operator default leaked into the tri-state");
        assertTrue(PermissionManager.hasPermission(player, NODE), "hasPermission must still follow Bukkit defaults");
    }

    @Test
    void fallbackAppliesOnlyToUnsetNodes() {
        setNodes.put("granted", true);
        setNodes.put("denied", false);
        assertTrue(PermissionManager.hasPermissionOrFallback(player, "granted", false), "Grant lost to fallback");
        assertFalse(PermissionManager.hasPermissionOrFallback(player, "denied", true), "Denial lost to fallback");
        assertTrue(PermissionManager.hasPermissionOrFallback(player, "unset", true), "Unset ignored true fallback");
        assertFalse(PermissionManager.hasPermissionOrFallback(player, "unset", false), "Unset ignored false fallback");
    }

    @Test
    void lookupFailureIsUndefined() {
        setFails = true;
        assertEquals(PermissionValue.UNDEFINED, PermissionManager.queryPermission(player, NODE));
        assertTrue(PermissionManager.hasPermissionOrFallback(player, NODE, true), "Failure did not use the fallback");
    }

    @Test
    void nonPlayerSendersAreUndefined() {
        CommandSender console = proxy(CommandSender.class, (p, m, a) -> {
            throw new AssertionError("Unexpected sender call: " + m);
        });
        assertEquals(PermissionValue.UNDEFINED, PermissionManager.queryPermission(console, NODE));
    }

    /** {@code /execute if permission} asks non-player entities through Bukkit; a failing lookup must not grant. */
    @Test
    void nonPlayerLookupFailureDenies() {
        CommandSender entity = proxy(CommandSender.class, (p, m, a) -> {
            throw new IllegalStateException("Permission provider unavailable");
        });
        assertFalse(PermissionManager.hasPermission(entity, NODE), "Provider failure allowed");
    }

    @Test
    void metaIsEmptyWithoutASource() {
        assertEquals(OptionalInt.empty(), provider.getIntegerMeta(id, player, "key"));
        assertFalse(provider.hasMeta(), "hasMeta true without a source");
        assertEquals("TestPerms", provider.getPermissionsPlugin());
        assertNull(new BukkitPermissionProvider(null, null, null).getPermissionsPlugin());
    }

    @Test
    void metaDelegatesToTheSource() {
        BukkitPermissionProvider.IntegerMeta source = new BukkitPermissionProvider.IntegerMeta() {
            @Override public OptionalInt get(UUID subjectId, CommandSender onlineSubject, String key) {
                assertEquals(id, subjectId);
                assertSame(player, onlineSubject);
                return key.equals("present") ? OptionalInt.of(7) : OptionalInt.empty();
            }
            @Override public String describe() { return "test meta"; }
        };
        BukkitPermissionProvider withMeta = new BukkitPermissionProvider(null, source, null);
        assertTrue(withMeta.hasMeta(), "hasMeta false with a source");
        assertEquals(OptionalInt.of(7), withMeta.getIntegerMeta(id, player, "present"));
        assertEquals(OptionalInt.empty(), withMeta.getIntegerMeta(id, player, "absent"));
        assertEquals("test meta", withMeta.describeMeta());
    }

    @Test
    void loginChecksAlsoAskStoredData() {
        BukkitPermissionProvider withLogin = new BukkitPermissionProvider("GroupManager", null,
            (subject, node) -> node.equals("minecraft.bypass.whitelist"), null);
        assertTrue(withLogin.hasPermissionAtLogin(player, "minecraft.bypass.whitelist"),
            "Stored grant ignored before the plugin attached permissions");
        assertFalse(withLogin.hasPermissionAtLogin(player, "minecraft.bypass.player-limit"), "Unset node granted at login");
        assertFalse(withLogin.hasPermission(player, "minecraft.bypass.whitelist"), "Stored data leaked outside login");
        setNodes.put("minecraft.bypass.player-limit", true);
        assertTrue(withLogin.hasPermissionAtLogin(player, "minecraft.bypass.player-limit"), "Attached grant ignored at login");
    }

    @Test
    void loginChecksWithoutStoredDataFollowBukkit() {
        assertFalse(PermissionManager.hasPermissionAtLogin(player, "minecraft.bypass.whitelist"));
        setNodes.put("minecraft.bypass.whitelist", true);
        assertTrue(PermissionManager.hasPermissionAtLogin(player, "minecraft.bypass.whitelist"));
    }

    @Test
    void failingStoredDataLookupDeniesAtLogin() {
        BukkitPermissionProvider failing = new BukkitPermissionProvider("GroupManager", null,
            (subject, node) -> { throw new IllegalStateException("Vault unavailable"); }, null);
        assertFalse(failing.hasPermissionAtLogin(player, "minecraft.bypass.whitelist"), "Lookup failure granted a bypass");
    }
}
