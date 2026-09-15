package com.yourname.yellowduck.entity;

import com.yourname.yellowduck.particle.ModParticles;
import com.yourname.yellowduck.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * 小樱召唤的布偶熊。
 *
 * 唯一动作：Anim-1，约 8 秒的连续打击动画。
 * HP <= 50% 后进入狂暴：攻击速度与伤害小幅提升，并持续产生血红粒子。
 */
public class ToyBearEntity extends PathfinderMob {
    public static final EntityDataAccessor<Boolean> RAGING =
            SynchedEntityData.defineId(ToyBearEntity.class, EntityDataSerializers.BOOLEAN);

    private static final double NORMAL_ATTACK_DAMAGE = 18.0D;
    private static final double RAGE_ATTACK_DAMAGE = 21.0D;
    private static final double NORMAL_ATTACK_SPEED = 1.20D;
    private static final double RAGE_ATTACK_SPEED = 1.40D;

    private static final int RAGE_PARTICLE_INTERVAL = 3;

    private UUID ownerSakura;
    private boolean rageBurstPlayed;
    private int rageParticleTimer;

    private final ServerBossEvent bossEvent =
            new ServerBossEvent(
                    Component.literal("布偶熊"),
                    BossEvent.BossBarColor.RED,
                    BossEvent.BossBarOverlay.PROGRESS
            );

    public ToyBearEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    public Component getName() {
        return Component.literal("布偶熊");
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    private void updateBossBar() {
        float hp = Math.max(0.0F, Math.min(1.0F, getHealth() / getMaxHealth()));
        bossEvent.setProgress(hp);
        bossEvent.setColor(entityData.get(RAGING)
                ? BossEvent.BossBarColor.RED
                : BossEvent.BossBarColor.PINK);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(RAGING, false);
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            return;
        }

        if (!hasLivingOwner()) {
            discard();
            return;
        }

        updateRageState();
        updateBossBar();
        tickRageParticles();
    }

    private boolean hasLivingOwner() {
        if (ownerSakura == null || !(level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        Entity owner = serverLevel.getEntity(ownerSakura);
        return owner instanceof SakurawitchEntity sakura
                && !sakura.isRemoved()
                && sakura.isAlive();
    }

    private void updateRageState() {
        boolean rage = getHealth() <= getMaxHealth() * 0.50F;
        boolean wasRaging = entityData.get(RAGING);

        if (rage == wasRaging) {
            return;
        }

        entityData.set(RAGING, rage);
        AttributeInstanceHelper.setBaseAttackDamage(this,
                rage ? RAGE_ATTACK_DAMAGE : NORMAL_ATTACK_DAMAGE);
        AttributeInstanceHelper.setBaseAttackSpeed(this,
                rage ? RAGE_ATTACK_SPEED : NORMAL_ATTACK_SPEED);

        if (rage && !rageBurstPlayed && level() instanceof ServerLevel serverLevel) {
            rageBurstPlayed = true;
            serverLevel.sendParticles(
                    ModParticles.SAKURA_BEAR_RAGE_BURST.get(),
                    getX(), getY() + 0.8D, getZ(),
                    22,
                    0.9D, 0.8D, 0.9D,
                    0.08D
            );
        }
    }

    private void tickRageParticles() {
        if (!entityData.get(RAGING) || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (rageParticleTimer > 0) {
            rageParticleTimer--;
            return;
        }
        rageParticleTimer = RAGE_PARTICLE_INTERVAL;

        double angle = random.nextDouble() * Math.PI * 2.0D;
        double radius = 0.65D + random.nextDouble() * 0.65D;
        double y = getY() + 0.15D + random.nextDouble() * 1.65D;

        serverLevel.sendParticles(
                ModParticles.SAKURA_BEAR_RAGE_MIST.get(),
                getX() + Math.cos(angle) * radius,
                y,
                getZ() + Math.sin(angle) * radius,
                1,
                0.0D, 0.025D, 0.0D,
                0.0D
        );

        if (tickCount % 6 == 0) {
            serverLevel.sendParticles(
                    ModParticles.SAKURA_BEAR_RAGE_SHARD.get(),
                    getX() + Math.cos(angle) * 0.9D,
                    getY() + 0.4D + random.nextDouble() * 1.3D,
                    getZ() + Math.sin(angle) * 0.9D,
                    1,
                    0.0D, 0.04D, 0.0D,
                    0.01D
            );
        }

        if (tickCount % 4 == 0) {
            serverLevel.sendParticles(
                    ModParticles.SAKURA_BEAR_RAGE_SPARK.get(),
                    getX() + (random.nextDouble() - 0.5D) * 1.8D,
                    getY() + random.nextDouble() * 1.8D,
                    getZ() + (random.nextDouble() - 0.5D) * 1.8D,
                    1,
                    0.0D, 0.03D, 0.0D,
                    0.0D
            );
        }

        if (tickCount % 5 == 0) {
            serverLevel.sendParticles(
                    ModParticles.SAKURA_BEAR_RAGE_RING.get(),
                    getX(), getY() + 0.05D, getZ(),
                    1,
                    0.0D, 0.0D, 0.0D,
                    0.0D
            );
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (!hit || level().isClientSide || !(level() instanceof ServerLevel serverLevel)) {
            return hit;
        }

        serverLevel.sendParticles(
                ModParticles.SAKURA_BEAR_RAGE_SLASH.get(),
                target.getX(),
                target.getY() + target.getBbHeight() * 0.55D,
                target.getZ(),
                1,
                0.0D, 0.0D, 0.0D,
                0.0D
        );

        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, amount);
    }

    public void setOwnerSakura(SakurawitchEntity sakura) {
        this.ownerSakura = sakura.getUUID();
    }

    public UUID getOwnerSakura() {
        return ownerSakura;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        bossEvent.removeAllPlayers();
        super.die(source);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerSakura != null) {
            tag.putUUID("OwnerSakura", ownerSakura);
        }
        tag.putBoolean("RageBurstPlayed", rageBurstPlayed);
        tag.putInt("RageParticleTimer", rageParticleTimer);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("OwnerSakura")) {
            ownerSakura = tag.getUUID("OwnerSakura");
        }
        rageBurstPlayed = tag.getBoolean("RageBurstPlayed");
        rageParticleTimer = tag.getInt("RageParticleTimer");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200000.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.ATTACK_DAMAGE, NORMAL_ATTACK_DAMAGE)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0D)
                .add(Attributes.ATTACK_SPEED, NORMAL_ATTACK_SPEED)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FOLLOW_RANGE, 35.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.85D);
    }

    /** Avoids depending on deprecated attribute instance APIs in the main entity logic. */
    private static final class AttributeInstanceHelper {
        private static void setBaseAttackDamage(ToyBearEntity entity, double value) {
            var instance = entity.getAttribute(Attributes.ATTACK_DAMAGE);
            if (instance != null) instance.setBaseValue(value);
        }

        private static void setBaseAttackSpeed(ToyBearEntity entity, double value) {
            var instance = entity.getAttribute(Attributes.ATTACK_SPEED);
            if (instance != null) instance.setBaseValue(value);
        }
    }
}
