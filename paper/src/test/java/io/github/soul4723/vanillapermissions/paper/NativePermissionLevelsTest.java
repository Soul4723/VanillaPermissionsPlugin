package io.github.soul4723.vanillapermissions.paper;

import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Operator levels read from both native permission shapes: int levels (to 1.21.10) and PermissionSet (1.21.11+). */
class NativePermissionLevelsTest {

    @Nested
    class IntLevels {
        private final NativePermissionLevels levels = detect(IntSource.class);

        @Test
        void detectsIntShape() {
            assertEquals("IntLevels", levels.getClass().getSimpleName());
        }

        @Test
        void readsSourceAndPlayerLevels() throws ReflectiveOperationException {
            assertEquals(3, levels.level(new IntSource(3)));
            assertEquals(4, levels.level(new IntPlayer(4)));
            assertEquals(1, levels.level(new LegacyIntEntity(1)));
        }

        /** Since 1.21.2 only players declare getPermissionLevel; other entities have no operator level. */
        @Test
        void entitiesWithoutALevelAreLevelZero() throws ReflectiveOperationException {
            assertEquals(0, levels.level(new IntEntity()));
        }

        @Test
        void lowersButNeverRaises() throws ReflectiveOperationException {
            IntSource source = new IntSource(2);
            assertEquals(1, ((IntSource) levels.withLevelAtMost(source, 1)).permissionLevel);
            assertSame(source, levels.withLevelAtMost(source, 2));
            assertSame(source, levels.withLevelAtMost(source, 4));
        }

        @Test
        void selectorCheckUsesGamemasterLevelAndPaperNode() throws ReflectiveOperationException {
            IntSource source = new IntSource(0);
            assertFalse(levels.allowsSelectors(source));
            assertEquals(List.of("2 minecraft.command.selector"), source.checks);
            assertTrue(levels.allowsSelectors(new IntSource(2)));
        }
    }

    @Nested
    class PermissionSetLevels {
        private final NativePermissionLevels levels = detect(SetSource.class);

        @Test
        void detectsPermissionSetShape() {
            assertEquals("PermissionSetLevels", levels.getClass().getSimpleName());
        }

        @Test
        void readsHighestCommandLevelOfAnySet() throws ReflectiveOperationException {
            assertEquals(2, levels.level(new SetSource(LevelBasedPermissionSet.forLevel(PermissionLevel.GAMEMASTERS))));
            assertEquals(4, levels.level(new SetSource(PermissionSet.ALL_PERMISSIONS)));
            assertEquals(0, levels.level(new SetSource(permission -> false)));
            assertEquals(3, levels.level(new SetPlayer(LevelBasedPermissionSet.forLevel(PermissionLevel.ADMINS))));
            assertEquals(0, levels.level(new IntEntity()));
        }

        @Test
        void lowersToALevelBasedSetButNeverRaises() throws ReflectiveOperationException {
            SetSource source = new SetSource(PermissionSet.ALL_PERMISSIONS);
            SetSource lowered = (SetSource) levels.withLevelAtMost(source, 1);
            LevelBasedPermissionSet set = assertInstanceOf(LevelBasedPermissionSet.class, lowered.permissions());
            assertEquals(PermissionLevel.MODERATORS, set.level());

            SetSource gamemaster = new SetSource(LevelBasedPermissionSet.forLevel(PermissionLevel.GAMEMASTERS));
            assertSame(gamemaster, levels.withLevelAtMost(gamemaster, 2));
            assertSame(gamemaster, levels.withLevelAtMost(gamemaster, 4));
        }

        @Test
        void selectorCheckUsesEntitySelectorPermissionAndPaperNode() throws ReflectiveOperationException {
            SetSource source = new SetSource(LevelBasedPermissionSet.forLevel(PermissionLevel.MODERATORS));
            assertFalse(levels.allowsSelectors(source));
            assertEquals(List.of(Permissions.COMMANDS_ENTITY_SELECTORS + " minecraft.command.selector"), source.checks);
            assertTrue(levels.allowsSelectors(new SetSource(LevelBasedPermissionSet.forLevel(PermissionLevel.GAMEMASTERS))));
        }
    }

    @Test
    void rejectsASourceWithNeitherShape() {
        assertThrows(NoSuchMethodException.class, () -> NativePermissionLevels.detect(IntEntity.class));
    }

    private static NativePermissionLevels detect(Class<?> sourceClass) {
        try {
            return NativePermissionLevels.detect(sourceClass);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    public static final class IntSource {
        private final int permissionLevel;
        final List<String> checks = new ArrayList<>();

        IntSource(int permissionLevel) {
            this.permissionLevel = permissionLevel;
        }

        public boolean hasPermission(int level, String node) {
            checks.add(level + " " + node);
            return permissionLevel >= level;
        }

        public IntSource withPermission(int level) {
            return new IntSource(level);
        }
    }

    public static class IntEntity {}

    public static class IntPlayer extends IntEntity {
        private final int level;

        IntPlayer(int level) {
            this.level = level;
        }

        protected int getPermissionLevel() {
            return level;
        }
    }

    /** Up to 1.21.1 every entity declared getPermissionLevel; subclasses inherit it. */
    public static class LegacyIntEntityBase {
        private final int level;

        LegacyIntEntityBase(int level) {
            this.level = level;
        }

        protected int getPermissionLevel() {
            return level;
        }
    }

    public static final class LegacyIntEntity extends LegacyIntEntityBase {
        LegacyIntEntity(int level) {
            super(level);
        }
    }

    public static final class SetSource {
        private final PermissionSet permissions;
        final List<String> checks = new ArrayList<>();

        SetSource(PermissionSet permissions) {
            this.permissions = permissions;
        }

        public PermissionSet permissions() {
            return permissions;
        }

        public boolean hasPermission(Permission permission, String node) {
            checks.add(permission + " " + node);
            return permissions.hasPermission(permission);
        }

        public SetSource withPermission(PermissionSet permissions) {
            return new SetSource(permissions);
        }
    }

    public static final class SetPlayer extends IntEntity {
        private final PermissionSet permissions;

        SetPlayer(PermissionSet permissions) {
            this.permissions = permissions;
        }

        public PermissionSet permissions() {
            return permissions;
        }
    }
}
