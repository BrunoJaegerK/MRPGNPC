package com.muffinhead.MRPGNPC.NPCs;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.network.protocol.AnimateEntityPacket;
import cn.nukkit.network.protocol.PlaySoundPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;

import java.util.Locale;

/**
 * A decorative model entity. It deliberately does not inherit MobNPC and therefore
 * never runs combat targeting, skills, drops or random-spawn behavior.
 */
public class DisplayNPC extends NPC {

    private String configId = "";
    private String animation = "";
    private String animationController = "__runtime_controller";
    private String animationNextState = "default";
    private String animationStopExpression = "query.any_animation_finished";
    private float animationBlendOut;
    private int animationIntervalTicks;
    private int animationElapsedTicks;
    private String sound = "";
    private float soundVolume = 1.0f;
    private float soundPitch = 1.0f;
    private int soundIntervalTicks;
    private int soundElapsedTicks;
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

    public void configureAnimationControl(String controller, String nextState,
                                          String stopExpression, float blendOutTime) {
        this.animationController = controller == null || controller.trim().isEmpty()
                ? "__runtime_controller" : controller.trim();
        this.animationNextState = nextState == null || nextState.trim().isEmpty()
                ? "default" : nextState.trim();
        this.animationStopExpression = stopExpression == null || stopExpression.trim().isEmpty()
                ? "query.any_animation_finished" : stopExpression.trim();
        this.animationBlendOut = clamp(blendOutTime, 0.0f, 10.0f, 0.0f);
    }

    public void configureSound(String sound, float volume, float pitch, int intervalTicks) {
        this.sound = sound == null ? "" : sound.trim();
        this.soundVolume = clamp(volume, 0.0f, 16.0f, 1.0f);
        this.soundPitch = clamp(pitch, 0.01f, 4.0f, 1.0f);
        this.soundIntervalTicks = Math.max(0, intervalTicks);
        this.soundElapsedTicks = 0;
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

    public String getAnimationController() {
        return animationController;
    }

    public String getDisplaySound() {
        return sound;
    }

    public void setDisplayAnimation(String animation, int intervalTicks) {
        this.animation = animation == null ? "" : animation.trim();
        this.animationIntervalTicks = Math.max(0, intervalTicks);
        this.animationElapsedTicks = 0;
        broadcastAnimation();
    }

    public void setAnimationController(String controller) {
        this.animationController = controller == null || controller.trim().isEmpty()
                ? "__runtime_controller" : controller.trim();
        broadcastAnimation();
    }

    public void playDisplaySound(String sound, float volume, float pitch) {
        if (sound == null || sound.trim().isEmpty()) return;
        String event = sound.trim();
        for (Player player : Server.getInstance().getOnlinePlayers().values()) {
            if (player.getLevel() != this.getLevel() || player.distance(this) > 64.0) continue;
            PlaySoundPacket packet = new PlaySoundPacket();
            packet.name = event;
            packet.x = (int) Math.floor(this.x);
            packet.y = (int) Math.floor(this.y);
            packet.z = (int) Math.floor(this.z);
            packet.volume = clamp(volume, 0.0f, 16.0f, 1.0f);
            packet.pitch = clamp(pitch, 0.01f, 4.0f, 1.0f);
            player.dataPacket(packet);
        }
    }

    private void broadcastAnimation() {
        if (this.animation.isEmpty()) return;
        for (Player player : Server.getInstance().getOnlinePlayers().values()) {
            if (player.getLevel() == this.getLevel()
                    && this.hasSpawned.containsKey(player.getLoaderId())) {
                sendAnimation(player);
            }
        }
    }

    private void sendAnimation(Player player) {
        if (player.protocol < ProtocolInfo.v1_16_100 || this.animation.isEmpty()) return;
        AnimateEntityPacket packet = new AnimateEntityPacket();
        packet.animation = this.animation;
        packet.controller = this.animationController;
        packet.nextState = this.animationNextState;
        packet.stopExpression = this.animationStopExpression;
        packet.blendOutTime = this.animationBlendOut;
        packet.runtimeEntityIds.add(this.getId());
        player.dataPacket(packet);
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
        return false;
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        boolean updated = super.entityBaseTick(tickDiff);
        if (!updated || this.closed) return updated;

        int elapsed = Math.max(1, tickDiff);
        if (!this.animation.isEmpty() && this.animationIntervalTicks > 0) {
            this.animationElapsedTicks += elapsed;
            if (this.animationElapsedTicks >= this.animationIntervalTicks) {
                this.animationElapsedTicks %= this.animationIntervalTicks;
                broadcastAnimation();
            }
        }
        if (!this.sound.isEmpty() && this.soundIntervalTicks > 0) {
            this.soundElapsedTicks += elapsed;
            if (this.soundElapsedTicks >= this.soundIntervalTicks) {
                this.soundElapsedTicks %= this.soundIntervalTicks;
                playDisplaySound(this.sound, this.soundVolume, this.soundPitch);
            }
        }
        return updated;
    }

    @Override
    public void spawnTo(Player player) {
        boolean alreadySpawned = this.hasSpawned.containsKey(player.getLoaderId());
        super.spawnTo(player);
        if (!alreadySpawned && this.hasSpawned.containsKey(player.getLoaderId())) {
            sendAnimation(player);
        }
    }
}
