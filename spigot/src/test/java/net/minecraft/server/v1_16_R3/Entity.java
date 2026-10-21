package net.minecraft.server.v1_16_R3;

public class Entity {

    private final int level;

    public Entity(int level) {
        this.level = level;
    }

    protected int y() {
        return level;
    }

    public Object getBukkitEntity() {
        return null;
    }
}
