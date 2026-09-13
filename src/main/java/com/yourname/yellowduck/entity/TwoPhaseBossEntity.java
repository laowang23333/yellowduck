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
import net.minecraft.world.phys.Vec3;
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

    // ================= 冲撞技能状态机变量 =================
    private int chargeTimer = 0;       // 蓄力计时器
    private int dashTimer = 0;         // 冲刺计时器
    private int dashCooldown = 0;      // 【新增】冲撞技能冷却计时器
    private boolean isCharging = false; // 是否在蓄力
    private boolean isDashing = false;  // 是否在冲刺
    private Vec3 dashDirection = Vec3.ZERO; // 冲刺方向
    // ========================================================

    public TwoPhaseBossEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public Component getName() {
        if (this.entityData.get(IS_PHASE_TWO)) {
            return Component.literal("肌肉大鸭");
        }
        return Component.literal("小黄鸭");
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_PHASE_TWO, false);
    }

    @Override
    public void tick() {
        super.tick();

        // ================= 冲撞技能逻辑 =================
        if (!this.level().isClientSide) {
            // 【新增】冷却时间递减
            if (dashCooldown > 0) {
                dashCooldown--;
            }

            float healthRatio = this.getHealth() / this.getMaxHealth();

            // 触发条件：二阶段 + 血量在 80% 到 100% 之间 + 冷却结束 + 不在蓄力/冲刺状态
            if (this.entityData.get(IS_PHASE_TWO) && healthRatio <= 1.0f && healthRatio > 0.8f && dashCooldown <= 0 && !isCharging && !isDashing) {
                this.isCharging = true;
                this.chargeTimer = 0;
                this.dashCooldown = 500; // 【重要】一旦触发，立刻进入 25 秒冷却（25秒 * 20 ticks = 500）
                
                // 锁定当前目标的方向
                if (this.getTarget() != null) {
                    this.dashDirection = new Vec3(
                            this.getTarget().getX() - this.getX(),
                            0,
                            this.getTarget().getZ() - this.getZ()
                    ).normalize();
                } else {
                    this.dashDirection = new Vec3(
                            this.getLookAngle().x,
                            0,
                            this.getLookAngle().z
                    ).normalize();
                }
            }

            // 蓄力阶段（2秒 = 40 ticks）
            if (this.isCharging) {
                this.chargeTimer++;
                // 蓄力期间粒子提示（黄色暴击粒子环绕）
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 1.0, this.getZ(), 5, 0.5, 0.5, 0.5, 0.1);
                }

                // 蓄力完毕，开始冲刺
                if (this.chargeTimer >= 40) {
                    this.isCharging = false;
                    this.isDashing = true;
                    this.dashTimer = 0;
                    // 冲刺初速度：0.5格/刻
                    this.setDeltaMovement(this.dashDirection.x * 0.5, 0, this.dashDirection.z * 0.5);
                }
            }

            // 冲刺阶段（持续 20 ticks = 1秒，刚好约10格距离）
            if (this.isDashing) {
                this.dashTimer++;
                
                // 持续施加向前的速度
                this.setDeltaMovement(this.dashDirection.x * 0.5, this.getDeltaMovement().y, this.dashDirection.z * 0.5);
                
                // 冲刺时的粒子效果（爆炸粒子拖尾）
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.5, this.getZ(), 1, 0, 0, 0, 0);
                }

                // 对路径上的实体造成伤害
                for (Entity entity : this.level().getEntities(this, this.getBoundingBox().inflate(1.0))) {
                    if (entity instanceof LivingEntity living && entity != this) {
                        living.hurt(this.damageSources().mobAttack(this), 16.0F);
                    }
                }

                // 冲刺结束条件：时间到 或 撞到方块
                if (this.dashTimer >= 20 || this.horizontalCollision) {
                    this.isDashing = false;
                    this.setDeltaMovement(Vec3.ZERO); // 撞墙或冲刺结束后立刻停止移动
                }
            }
        }
        // ===============================================
    }

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

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ================= 属性调整（完全免疫击退） =================
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100000.0D)      // 一阶段10万血
                .add(Attributes.ARMOR, 12.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D) // 100%免疫击退
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
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
        tag.putInt("DashCooldown", this.dashCooldown); // 【新增】保存冷却数据，防止重载游戏后冷却重置
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(IS_PHASE_TWO, tag.getBoolean("IsPhaseTwo"));
        this.dashCooldown = tag.getInt("DashCooldown"); // 【新增】读取冷却数据
    }

    // ================= 攻击时触发挥拳动画（无击退） =================
    @Override
    public boolean doHurtTarget(Entity target) {
        this.triggerAnim("controller", "attack");
        boolean success = super.doHurtTarget(target);
        return success; // 玩家不再被击退
    }
    // ======================================================================
}
