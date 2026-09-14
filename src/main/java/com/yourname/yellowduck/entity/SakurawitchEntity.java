package com.yourname.yellowduck.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SakurawitchEntity extends PathfinderMob {

    // ===== 同步到客户端的状态（渲染器读这些决定播什么动画）=====
    public static final EntityDataAccessor<Boolean> IS_WALKING =
            SynchedEntityData.defineId(SakurawitchEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> ATTACK_INDEX =
            SynchedEntityData.defineId(SakurawitchEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> ATTACK_TIMER =
            SynchedEntityData.defineId(SakurawitchEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> IS_DYING =
            SynchedEntityData.defineId(SakurawitchEntity.class, EntityDataSerializers.BOOLEAN);
    // ==========================================================

    private int skillCooldown = 0;
    private int deathTimer = 0;

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.literal("小樱"),
            BossEvent.BossBarColor.PINK,
            BossEvent.BossBarOverlay.PROGRESS
    );

    public SakurawitchEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    public Component getName() {
        return Component.literal("小樱");
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_WALKING, false);
        this.entityData.define(ATTACK_INDEX, 0);
        this.entityData.define(ATTACK_TIMER, 0);
        this.entityData.define(IS_DYING, false);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !this.level().isClientSide) {
            // 普通攻击固定播 attack_01
            int idx = 1;
            this.entityData.set(ATTACK_INDEX, idx);
            this.entityData.set(ATTACK_TIMER, attackDurationTicks(idx));
            this.level().playSound(null, this.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.HOSTILE, 1.2F, 0.8F);
        }
        return hit;
    }

    private int attackDurationTicks(int idx) {
        switch (idx) {
            case 1: case 2: return 27;    // 1.33s
            case 3: return 30;            // 1.47s
            case 4: return 31;            // 1.53s
            case 5: return 165;           // 8.25s
            case 6: return 24;            // 1.20s
            default: return 27;
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            if (this.skillCooldown > 0) this.skillCooldown--;
            if (this.skillCooldown == 0 && this.random.nextInt(40) == 0) this.skillCooldown = 200;

            // 更新走路状态
            double dx = this.getX() - this.xo;
            double dz = this.getZ() - this.zo;
            boolean moving = (dx * dx + dz * dz) > 1.0E-5;
            this.entityData.set(IS_WALKING, moving);

            // 攻击动画计时
            int at = this.entityData.get(ATTACK_TIMER);
            if (at > 0) {
                this.entityData.set(ATTACK_TIMER, at - 1);
                if (at - 1 == 0) {
                    this.entityData.set(ATTACK_INDEX, 0);
                }
            }

            this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());

            // 死亡动画期间无敌、不能移动
            if (this.entityData.get(IS_DYING)) {
                this.setDeltaMovement(Vec3.ZERO);
                this.getNavigation().stop();
                this.deathTimer++;
                if (this.deathTimer >= 30) {
                    this.entityData.set(IS_DYING, false);
                    super.die(this.damageSources().generic());
                }
                return;
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.entityData.get(IS_DYING)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide && !this.entityData.get(IS_DYING)) {
            // 先播死亡动画，30 tick 后再真死
            this.entityData.set(IS_DYING, true);
            this.deathTimer = 0;
            this.setHealth(0.0F);
            this.setInvulnerable(true);
            return;
        }
        super.die(source);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("SkillCooldown", this.skillCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.skillCooldown = tag.getInt("SkillCooldown");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 50000.0D)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.ATTACK_DAMAGE, 15.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 35.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D);
    }
}
