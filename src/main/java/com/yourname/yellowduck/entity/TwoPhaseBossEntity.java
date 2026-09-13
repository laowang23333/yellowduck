package com.yourname.yellowduck.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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

    // ================= 修改 Jade 显示的名字 =================
    @Override
    public Component getName() {
        if (this.entityData.get(IS_PHASE_TWO)) {
            return Component.literal("肌肉大鸭");
        }
        return Component.literal("小黄鸭");
    }
    // ========================================================

    // ================= AI 行为（主动攻击 + 移动） =================
    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // 1.2D是追击速度
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
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
            // 一阶段血量低于50%切换二阶段
            if (this.getHealth() < this.getMaxHealth() * 0.5f && !this.entityData.get(IS_PHASE_TWO)) {
                this.entityData.set(IS_PHASE_TWO, true);
                
                // 1. 修改最大血量到500万
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(5000000.0D);
                // 2. 回满血
                this.setHealth(5000000.0F);
                
                // 3. 提升移动速度（二阶段狂暴）
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.35D);
                
                // 4. 播放切换音效和粒子
                this.level().playSound(null, this.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0F, 1.0F);
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), 10, 1.0, 1.0, 1.0, 0.1);
                }
            }
        }
    }

    // ================= 动画控制器 =================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 5, state -> {
            if (state.isMoving()) {
                return state.setAndContinue(RawAnimation.begin().thenLoop("walk"));
            } else {
                return state.setAndContinue(RawAnimation.begin().thenLoop("idle"));
            }
        }).triggerableAnim("attack", RawAnimation.begin().thenPlay("attack")));
    }
    // ================================================================

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ================= 基础属性（一阶段初始10万血） =================
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100000.0D)      // 一阶段：10万血
                .add(Attributes.ARMOR, 12.0D)               // 护甲12
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D) // 击退抗性0.8
                .add(Attributes.ATTACK_DAMAGE, 12.0D)       // 普攻伤害12
                .add(Attributes.MOVEMENT_SPEED, 0.28D)      // 一阶段移速
                .add(Attributes.FOLLOW_RANGE, 35.0D);
    }
    // ======================================================================

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

    // ================= 攻击时触发挥拳动画 + 强化击退与特效 =================
    @Override
    public boolean doHurtTarget(Entity target) {
        // 触发一次挥拳动画
        this.triggerAnim("controller", "attack");

        boolean success = super.doHurtTarget(target);

        if (success && target instanceof LivingEntity livingTarget) {
            // 强化击退效果
            livingTarget.knockback(1.5D, this.getX() - target.getX(), this.getZ() - target.getZ());
            
            // 如果是二阶段（肌肉大鸭），额外增加攻击特效
            if (this.entityData.get(IS_PHASE_TWO) && this.level() instanceof ServerLevel serverLevel) {
                // 模拟双拳重击的拳风粒子
                serverLevel.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0, target.getZ(), 15, 0.5, 0.5, 0.5, 0.2);
                serverLevel.playSound(null, target.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.6F, 1.5F);
            }
        }
        return success;
    }
    // ======================================================================
}
