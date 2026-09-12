package com.yourname.yellowduck.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class TwoPhaseBossEntity extends PathfinderMob implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public static final EntityDataAccessor<Boolean> IS_PHASE_TWO =
            SynchedEntityData.defineId(TwoPhaseBossEntity.class, EntityDataSerializers.BOOLEAN);

    public TwoPhaseBossEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    // ================= AI 行为（主动攻击 + 移动） =================
    @Override
    protected void registerGoals() {
        super.registerGoals();
        
        // 0. 浮在水面（防止淹死）
        this.goalSelector.addGoal(0, new FloatGoal(this));
        
        // 1. 近战攻击玩家（1.0D是追击速度）
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0D, true));
        
        // 2. 随机漫步
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        
        // 3. 看着玩家
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        
        // 4. 随机东张西望
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        // 5. 受到攻击时反击
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        
        // 6. 主动索敌（主动攻击最近的玩家）
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }
    // ==============================================================

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_PHASE_TWO, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            // 血量低于50%切换二阶段
            if (this.getHealth() < this.getMaxHealth() * 0.5f && !this.entityData.get(IS_PHASE_TWO)) {
                this.entityData.set(IS_PHASE_TWO, true);
                this.level().playSound(null, this.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0F, 1.0F);
                this.setHealth(this.getMaxHealth() * 0.75f); // 切换阶段恢复血量
            }
        }
    }

    // ================= 动画控制器（行走、待机、攻击） =================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 5, state -> {
            if (state.isMoving()) {
                return state.setAndContinue(RawAnimation.begin().thenLoop("walk"));
            } else {
                return state.setAndContinue(RawAnimation.begin().thenLoop("idle"));
            }
        }).triggerableAnim("attack", RawAnimation.begin().thenPlay("attack"))); // 攻击动画触发器
    }
    // ================================================================

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 600.0D)
                .add(Attributes.ATTACK_DAMAGE, 14.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 35.0D);
    }

    public boolean isPhaseTwo() {
        return this.entityData.get(IS_PHASE_TWO);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("IsPhaseTwo", this.entityData.get(IS_PHASE_TWO));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(IS_PHASE_TWO, tag.getBoolean("IsPhaseTwo"));
    }

    // ================= 攻击时触发挥拳动画 =================
    @Override
    public boolean doHurtTarget(Entity target) {
        this.triggerAnim("controller", "attack");
        return super.doHurtTarget(target);
    }
    // ======================================================
}
