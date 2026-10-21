package io.github.soul4723.vanillapermissions.spigot;

import io.github.soul4723.vanillapermissions.spigot.SpigotNames.Levels;
import io.github.soul4723.vanillapermissions.spigot.SpigotNames.NativeClass;
import io.github.soul4723.vanillapermissions.spigot.SpigotNames.NativeMember;
import net.minecraft.commands.CommandListenerWrapper;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.EntityHuman;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Operator levels in both shapes: int levels on the fake Spigot 1.16.5 classes in {@code net.minecraft.server.v1_16_R3},
 * and PermissionSet levels on the fake Spigot 1.21.11 classes in {@code net.minecraft.commands},
 * {@code net.minecraft.server.permissions} and {@code net.minecraft.world.entity.player}.
 */
class OperatorLevelsTest {

    private static final ClassLoader LOADER = OperatorLevelsTest.class.getClassLoader();

    private static CommandSender senderWith(String... nodes) {
        Set<String> granted = Set.of(nodes);
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(), new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("hasPermission") && args[0] instanceof String node) return granted.contains(node);
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (method.getName().equals("equals")) return proxy == args[0];
                    return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
                });
    }

    private static CommandListenerWrapper source(CommandSender sender, PermissionLevel level) {
        return new CommandListenerWrapper(sender, LevelBasedPermissionSet.a(level));
    }

    private static OperatorLevels spigot1_21_11() throws ReflectiveOperationException {
        return OperatorLevels.of(NativeAccess.resolveLevels(MappingGeneration.of("v1_21_R7"), LOADER));
    }

    @Test
    void sourcesWithAnIntLevelNeverLoadThePermissionSetClasses() throws Exception {
        List<String> requested = new ArrayList<>();
        ClassLoader recording = new ClassLoader(LOADER) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                requested.add(name);
                return super.loadClass(name, resolve);
            }
        };
        NativeAccess access = NativeAccess.resolve(MappingGeneration.of("v1_16_R3"), recording);

        assertEquals(Levels.INT, access.levels());
        assertTrue(access.has(NativeMember.SOURCE_PERMISSION_LEVEL));
        assertFalse(access.has(NativeMember.SOURCE_PERMISSIONS));
        assertTrue(requested.stream().noneMatch(name -> name.startsWith("net.minecraft.server.permissions.")), requested.toString());
    }

    @Test
    void sourcesWithAPermissionSetResolveTheSpigot1_21_11Names() throws Exception {
        NativeAccess access = NativeAccess.resolveLevels(MappingGeneration.of("v1_21_R7"), LOADER);

        assertEquals(Levels.PERMISSION_SET, access.levels());
        assertEquals("g", access.field(NativeMember.SOURCE_PERMISSIONS).getName());
        assertEquals("I", access.method(NativeMember.PLAYER_PERMISSIONS).getName());
        assertEquals("e", access.field(NativeMember.SELECTORS_PERMISSION).getName());
        assertSame(Permission.b.class, access.type(NativeClass.HAS_COMMAND_LEVEL));
        assertFalse(access.has(NativeMember.SOURCE_PERMISSION_LEVEL));
        assertFalse(access.has(NativeMember.ENTITY_PERMISSION_LEVEL));
    }

    @Test
    void levelsComeFromTheRawSetNotFromBukkitNodes() throws Exception {
        OperatorLevels levels = spigot1_21_11();
        CommandSender everything = senderWith("minecraft.command.selector", "minecraft.command.gamemode");
        CommandListenerWrapper checking = source(everything, PermissionLevel.ALL);
        checking.currentCommand = literal("gamemode").build();

        assertTrue(checking.d().hasPermission(new Permission.b(PermissionLevel.OWNERS)), "the delegate grants every level here");
        assertEquals(0, levels.level(checking));
        assertEquals(3, levels.level(source(everything, PermissionLevel.ADMINS)));
        assertEquals(4, levels.level(new EntityHuman(LevelBasedPermissionSet.a(PermissionLevel.OWNERS))));
        assertEquals(0, levels.level("not a source or player"));
    }

    @Test
    void selectorsNeedLevel2OrTheSelectorNode() throws Exception {
        OperatorLevels levels = spigot1_21_11();

        assertFalse(levels.allowsSelectors(source(senderWith(), PermissionLevel.MODERATORS)));
        assertTrue(levels.allowsSelectors(source(senderWith(), PermissionLevel.GAMEMASTERS)));
        assertTrue(levels.allowsSelectors(source(senderWith("minecraft.command.selector"), PermissionLevel.ALL)));
    }

    @Test
    void theSelectorCheckIgnoresTheCommandCraftBukkitIsChecking() throws Exception {
        OperatorLevels levels = spigot1_21_11();
        CommandListenerWrapper commandOnly = source(senderWith("minecraft.command.gamemode"), PermissionLevel.ALL);
        commandOnly.currentCommand = literal("gamemode").build();
        CommandListenerWrapper selector = source(senderWith("minecraft.command.selector"), PermissionLevel.ALL);
        selector.currentCommand = literal("gamemode").build();

        assertTrue(commandOnly.d().hasPermission(Permissions.e), "the delegate answers for the current command");
        assertFalse(levels.allowsSelectors(commandOnly));
        assertTrue(levels.allowsSelectors(selector));
    }

    @Test
    void capsOnlyLowerTheLevel() throws Exception {
        OperatorLevels levels = spigot1_21_11();
        CommandListenerWrapper source = source(senderWith(), PermissionLevel.ADMINS);

        assertSame(source, levels.withLevelAtMost(source, 3));
        assertSame(source, levels.withLevelAtMost(source, 4));
        Object capped = levels.withLevelAtMost(source, 1);
        assertNotSame(source, capped);
        assertEquals(1, levels.level(capped));
        assertFalse(levels.allowsSelectors(capped));
        assertEquals(3, levels.level(source));
        assertEquals(0, levels.level(levels.withLevelAtMost(source, -1)));
    }

    @Test
    void theOldIntLookupFailsClosedOnAPermissionSetSource() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> NativeAccess.field(CommandListenerWrapper.class, "g", int.class, false, "CommandListenerWrapper.g"));
        assertTrue(error.getMessage().contains("is not an instance field of type int"), error.getMessage());
    }

    @Test
    void aStaticFieldMustBeStatic() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> NativeAccess.field(EntityHuman.class, "permissions", net.minecraft.server.permissions.PermissionSet.class, true,
                        "EntityHuman.permissions"));
        assertTrue(error.getMessage().contains("is not a static field"), error.getMessage());
    }
}
