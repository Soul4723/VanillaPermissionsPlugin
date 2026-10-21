package io.github.soul4723.vanillapermissions.spigot;

import io.github.soul4723.vanillapermissions.spigot.SpigotNames.LevelShape;
import io.github.soul4723.vanillapermissions.spigot.SpigotNames.NativeClass;
import io.github.soul4723.vanillapermissions.spigot.SpigotNames.NativeMember;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static io.github.soul4723.vanillapermissions.spigot.OperatorLevelsTest.senderWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Operator levels under Mojang names, which cover both shapes, so the source class decides: Spigot 26.x has a
 * PermissionSet ({@code net.minecraft.commands.CommandSourceStack} in {@code src/test/java}), Paper 1.20.5 an int level
 * (the class of the same name in {@code src/test/paper-1.20.5}, loaded ahead of the other by its own class loader).
 */
class MojangOperatorLevelsTest {

    private static final ClassLoader LOADER = MojangOperatorLevelsTest.class.getClassLoader();

    @Nested
    class Spigot26 {

        private CommandSourceStack source(CommandSender sender, PermissionLevel level) {
            return new CommandSourceStack(sender, LevelBasedPermissionSet.forLevel(level), new CommandSourceStack.Level(false));
        }

        private OperatorLevels levels() throws ReflectiveOperationException {
            return OperatorLevels.of(NativeAccess.resolveOperatorLevelsOnly(MappingGeneration.MOJANG, LOADER));
        }

        @Test
        void resolvesTheMojangPermissionSetNames() throws Exception {
            NativeAccess access = NativeAccess.resolveOperatorLevelsOnly(MappingGeneration.MOJANG, LOADER);

            assertEquals(LevelShape.PERMISSION_SET, access.shape());
            assertEquals("permissions", access.field(NativeMember.SOURCE_PERMISSIONS).getName());
            assertEquals("withPermission", access.method(NativeMember.SOURCE_WITH_PERMISSION_SET).getName());
            assertEquals("COMMANDS_ENTITY_SELECTORS", access.field(NativeMember.SELECTORS_PERMISSION).getName());
            assertEquals("id", access.method(NativeMember.PERMISSION_LEVEL_ID).getName());
            assertEquals("forLevel", access.method(NativeMember.FOR_LEVEL).getName());
            assertSame(Permission.HasCommandLevel.class, access.type(NativeClass.HAS_COMMAND_LEVEL));
            assertSame(Player.class, access.type(NativeClass.PLAYER));
            assertEquals("net.minecraft.commands.CommandSourceStack$DelegatePermissionSet",
                    access.type(NativeClass.DELEGATE_PERMISSION_SET).getName());
            assertFalse(access.has(NativeMember.SOURCE_PERMISSION_LEVEL));
            assertFalse(access.has(NativeMember.SOURCE_HAS_PERMISSION));
        }

        @Test
        void levelsComeFromTheRawSetAndOnlyPlayersHaveOne() throws Exception {
            OperatorLevels levels = levels();
            CommandSourceStack checking = source(senderWith("minecraft.command.gamemode"), PermissionLevel.ALL);
            checking.currentCommand = literal("gamemode").build();

            assertTrue(checking.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.OWNERS)),
                    "the delegate grants every level here");
            assertEquals(0, levels.level(checking));
            assertEquals(3, levels.level(source(senderWith(), PermissionLevel.ADMINS)));
            assertEquals(4, levels.level(new Player(LevelBasedPermissionSet.forLevel(PermissionLevel.OWNERS))));
            assertEquals(0, levels.level(new Entity(4)), "entities other than players have no level from 1.21.2");
        }

        @Test
        void selectorsNeedLevel2OrTheSelectorNodeWhateverCommandIsChecked() throws Exception {
            OperatorLevels levels = levels();
            CommandSourceStack commandOnly = source(senderWith("minecraft.command.gamemode"), PermissionLevel.ALL);
            commandOnly.currentCommand = literal("gamemode").build();

            assertFalse(levels.allowsSelectors(source(senderWith(), PermissionLevel.MODERATORS)));
            assertTrue(levels.allowsSelectors(source(senderWith(), PermissionLevel.GAMEMASTERS)));
            assertTrue(levels.allowsSelectors(source(senderWith("minecraft.command.selector"), PermissionLevel.ALL)));
            assertFalse(levels.allowsSelectors(commandOnly));
        }

        /** The delegate's own rule decides: with ignore-vanilla-permissions only the node counts, without a world the level does. */
        @Test
        void selectorsFollowCraftBukkitsIgnoreVanillaPermissions() throws Exception {
            OperatorLevels levels = levels();
            CommandSourceStack.Level ignoring = new CommandSourceStack.Level(true);

            assertFalse(levels.allowsSelectors(new CommandSourceStack(senderWith(),
                    LevelBasedPermissionSet.forLevel(PermissionLevel.OWNERS), ignoring)));
            assertTrue(levels.allowsSelectors(new CommandSourceStack(senderWith("minecraft.command.selector"),
                    LevelBasedPermissionSet.forLevel(PermissionLevel.ALL), ignoring)));
            assertTrue(levels.allowsSelectors(new CommandSourceStack(senderWith(),
                    LevelBasedPermissionSet.forLevel(PermissionLevel.GAMEMASTERS), null)));
            assertFalse(levels.allowsSelectors(new CommandSourceStack(senderWith(),
                    LevelBasedPermissionSet.forLevel(PermissionLevel.MODERATORS), null)));
        }

        @Test
        void capsOnlyLowerTheLevel() throws Exception {
            OperatorLevels levels = levels();
            CommandSourceStack source = source(senderWith(), PermissionLevel.ADMINS);

            assertSame(source, levels.withLevelAtMost(source, 3));
            assertSame(source, levels.withLevelAtMost(source, 4));
            Object capped = levels.withLevelAtMost(source, 1);
            assertNotSame(source, capped);
            assertEquals(1, levels.level(capped));
            assertFalse(levels.allowsSelectors(capped));
        }
    }

    @Nested
    class Paper1_20_5 {

        private final List<String> requested = new ArrayList<>();
        private final ClassLoader loader = paperClasses(requested);

        private OperatorLevels levels() throws ReflectiveOperationException {
            return OperatorLevels.of(NativeAccess.resolveOperatorLevelsOnly(MappingGeneration.MOJANG, loader));
        }

        private Object source(CommandSender sender, int level) throws ReflectiveOperationException {
            return Class.forName("net.minecraft.commands.CommandSourceStack", true, loader)
                    .getConstructor(CommandSender.class, int.class).newInstance(sender, level);
        }

        @Test
        void resolvesTheMojangIntNamesWithoutLoadingThePermissionSetClasses() throws Exception {
            NativeAccess access = NativeAccess.resolveOperatorLevelsOnly(MappingGeneration.MOJANG, loader);

            assertEquals(LevelShape.INT, access.shape());
            assertSame(loader, access.type(NativeClass.SOURCE).getClassLoader());
            assertEquals("permissionLevel", access.field(NativeMember.SOURCE_PERMISSION_LEVEL).getName());
            assertEquals("hasPermission", access.method(NativeMember.SOURCE_HAS_PERMISSION).getName());
            assertEquals("getPermissionLevel", access.method(NativeMember.ENTITY_PERMISSION_LEVEL).getName());
            assertSame(Entity.class, access.type(NativeClass.PERMISSION_LEVEL_HOLDER));
            assertFalse(access.has(NativeMember.SOURCE_PERMISSIONS));
            assertTrue(requested.stream().noneMatch(name -> name.startsWith("net.minecraft.server.permissions.")), requested.toString());
        }

        @Test
        void readsSourceAndEntityLevels() throws Exception {
            OperatorLevels levels = levels();

            assertEquals(3, levels.level(source(senderWith(), 3)));
            assertEquals(4, levels.level(new Entity(4)));
            assertEquals(0, levels.level("not a source or entity"));
        }

        @Test
        void selectorsNeedLevel2OrTheSelectorNode() throws Exception {
            OperatorLevels levels = levels();

            assertFalse(levels.allowsSelectors(source(senderWith(), 1)));
            assertTrue(levels.allowsSelectors(source(senderWith(), 2)));
            assertTrue(levels.allowsSelectors(source(senderWith("minecraft.command.selector"), 0)));
        }

        @Test
        void capsOnlyLowerTheLevel() throws Exception {
            OperatorLevels levels = levels();
            Object source = source(senderWith(), 3);

            assertSame(source, levels.withLevelAtMost(source, 4));
            Object capped = levels.withLevelAtMost(source, 1);
            assertNotSame(source, capped);
            assertEquals(1, levels.level(capped));
        }
    }

    /**
     * A class loader that finds the Paper 1.20.5 classes before the test class path, which holds Spigot 26.x's classes
     * under the same names, and records every class requested through it.
     */
    private static ClassLoader paperClasses(List<String> requested) {
        try {
            Path testClasses = Paths.get(MojangOperatorLevelsTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path paperClasses = testClasses.resolveSibling("paper-1.20.5-test-classes");
            assertTrue(Files.isDirectory(paperClasses), paperClasses + " is missing; the spigot pom compiles it in test-compile");
            return new URLClassLoader(new URL[]{paperClasses.toUri().toURL()}, LOADER) {
                @Override
                protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    requested.add(name);
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> loaded = findLoadedClass(name);
                        if (loaded != null) return loaded;
                        try {
                            return findClass(name);
                        } catch (ClassNotFoundException notPaperOnly) {
                            return super.loadClass(name, resolve);
                        }
                    }
                }
            };
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }
}
