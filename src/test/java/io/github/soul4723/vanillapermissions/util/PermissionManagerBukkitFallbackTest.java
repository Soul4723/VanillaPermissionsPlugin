package io.github.soul4723.vanillapermissions.util;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bukkit fallback for non-player senders; no LuckPerms or server fixture. */
class PermissionManagerBukkitFallbackTest {
    private static final String LEAF = "minecraft.command.time.set";

    private final Set<String> grants = new HashSet<>();
    private boolean providerFails;
    private final CommandSender sender = (CommandSender) Proxy.newProxyInstance(
            CommandSender.class.getClassLoader(), new Class<?>[]{CommandSender.class},
            (proxy, method, values) -> switch (method.getName()) {
                case "hasPermission" -> {
                    if (providerFails) throw new IllegalStateException("Permission provider unavailable");
                    yield grants.contains(values[0]);
                }
                default -> throw new AssertionError("Unexpected sender call: " + method);
            });

    @Test
    void unsetPermissionIsDenied() {
        assertFalse(PermissionManager.hasPermission(sender, LEAF), "Unset allowed");
    }

    @Test
    void parentGrantDoesNotImplyChild() {
        grants.add("minecraft.command.time");
        assertFalse(PermissionManager.hasPermission(sender, LEAF), "Bare parent grant allowed child");
        assertFalse(PermissionManager.hasPermission(sender, "minecraft.command.weather.rain"), "Unrelated parent allowed");
    }

    @Test
    void exactLeafGrantIsAllowed() {
        grants.add(LEAF);
        assertTrue(PermissionManager.hasPermission(sender, LEAF), "Leaf denied");
    }

    @Test
    void providerFailureDenies() {
        grants.add(LEAF);
        providerFails = true;
        assertFalse(PermissionManager.hasPermission(sender, LEAF), "Provider failure allowed");
    }
}
