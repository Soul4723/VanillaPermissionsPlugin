package io.github.soul4723.extrapermissions.util;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

public class SelectorPathBuilder {

    private static final Set<EntityType> PLAYER_TYPES = new HashSet<>(java.util.Collections.singletonList(
            EntityType.PLAYER
    ));

    public enum SelectorScope {
        PLAYER("minecraft.selector.player"),
        ENTITY("minecraft.selector.entity"),
        SELF("minecraft.selector.self");

        private final String prefix;

        SelectorScope(String prefix) {
            this.prefix = prefix;
        }

        public String getPrefix() {
            return prefix;
        }
    }

    private final String commandName;
    private final String selectorPath;
    private final SelectorScope scope;

    public SelectorPathBuilder(String commandName, String selectorPath, Set<Entity> targetEntities) {
        this.commandName = commandName.toLowerCase();
        this.selectorPath = selectorPath != null ? selectorPath.toLowerCase() : "";
        this.scope = determineScopeFromEntities(targetEntities);
    }

    public String build() {
        StringBuilder path = new StringBuilder(scope.getPrefix());
        path.append(".").append(commandName);

        if (!selectorPath.isEmpty()) {
            path.append(".").append(selectorPath);
        }

        return path.toString();
    }

    public SelectorScope getScope() {
        return scope;
    }

    private static SelectorScope determineScopeFromEntity(Entity entity) {
        if (entity == null) {
            return SelectorScope.ENTITY;
        }
        return isPlayerType(entity.getType()) ? SelectorScope.PLAYER : SelectorScope.ENTITY;
    }

    private static SelectorScope determineScopeFromEntities(Set<Entity> entities) {
        if (entities == null || entities.isEmpty()) {
            return SelectorScope.ENTITY;
        }
        return entities.stream()
                .allMatch(e -> isPlayerType(e.getType())) ? SelectorScope.PLAYER : SelectorScope.ENTITY;
    }

    private static boolean isPlayerType(EntityType type) {
        return PLAYER_TYPES.contains(type);
    }

}
