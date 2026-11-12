package com.muffinhead.MRPGNPC.NPCs;

import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.math.Vector3;

/**
 * A purely decorative NPC. It deliberately does not inherit MobNPC, so none of
 * the combat, targeting, skill, drop, death-command or random-spawn logic runs.
 */
public class DisplayNPC extends NPC {

    private String configId = "";
    private String animation = "";
    private int animationIntervalTicks;
    private int animationElapsedTicks;
    private float hitboxWidth = 0.6f;
    private float hitboxHeight = 1.8f;

    public DisplayNPC(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
        setImmobile(true);
        setCollidable(false);
    }

    public void configure(String configId, String animation, int animationIntervalTicks,
                          float hitboxWidth, float hitboxHeight) {
        this.configId = configId == null ? "" : configId;
        this.animation = animation == null ? "" : animation.trim();
        this.animationIntervalTicks = Math.max(0, animationIntervalTicks);
        this.hitboxWidth = clamp(hitboxWidth, 0.01f, 16.0f, 0.6f);
        this.hitboxHeight = clamp(hitboxHeight, 0.01f, 32.0f, 1.8f);
        recalculateBoundingBox(true);
    }

    private static float clamp(float value, float min, float max, float fallback) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return fallback;
        return Math.max(min, Math.min(max, value));
    }

    public String getConfigId() {
        return configId;
    }

    public String getDisplayAnimation() {
        return animation;
    }

    public void setDisplayAnimation(String animation, int intervalTicks) {
        this.animation = animation == null ? "" : animation.trim();
        this.animationIntervalTicks = Math.max(0, intervalTicks);
        this.animationElapsedTicks = 0;
        if (!this.animation.isEmpty()) {
            playAnimation(this.animation);
        }
    }

    @Override
    public float getWidth() {
        return hitboxWidth > 0 ? hitboxWidth : 0.6f;
    }

    @Override
    public float getLength() {
        return getWidth();
    }

    @Override
    public float getHeight() {
        return hitboxHeight > 0 ? hitboxHeight : 1.8f;
    }

    @Override
    public boolean canBeFollowed() {
        return false;
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        // Decorative displays are not combat entities.
        return false;
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        boolean updated = super.entityBaseTick(tickDiff);
        if (!updated || this.closed || this.animation.isEmpty() || this.animationIntervalTicks <= 0) {
            return updated;
        }

        this.animationElapsedTicks += Math.max(1, tickDiff);
        if (this.animationElapsedTicks >= this.animationIntervalTicks) {
            this.animationElapsedTicks %= this.animationIntervalTicks;
            playAnimation(this.animation);
        }
        return updated;
    }

    @Override
    public void spawnToAll() {
        super.spawnToAll();
        if (!this.animation.isEmpty()) {
            playAnimation(this.animation);
        }
    }
}
