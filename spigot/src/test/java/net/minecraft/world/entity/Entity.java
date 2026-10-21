package net.minecraft.world.entity;

/** Mimics Paper 1.20.5's Entity (Mojang names): every entity declares {@code getPermissionLevel()} until 1.21.2. */
public class Entity {

    private final int level;

    public Entity(int level) {
        this.level = level;
    }

    protected int getPermissionLevel() {
        return level;
    }
}
