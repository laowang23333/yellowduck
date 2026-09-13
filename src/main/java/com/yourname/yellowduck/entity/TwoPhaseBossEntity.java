package com.yourname.yellowduck.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
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
    private int chargeTimer = 0;
    private int dashTimer = 0;
    private int dashCooldown = 0;
    private boolean isCharging = false;
    private boolean isDashing = false;
    private Vec3 dashDirection = Vec3.ZERO;
    // ========================================================

    // ================= 变身过渡状态机 =================
    private boolean isTransforming = false;
    private int transformTimer = 0;
    // ===================================================

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

    // ================= 触发过渡（死亡拦截） =================
    private void startTransform() {
        this.isTransforming = true;
        this.transformTimer = 0;
        this.isCharging = false;
        this.isDashing = false;
        this.setDeltaMovement(Vec3.ZERO);
        this.getNavigation().stop();
        this.setTarget(null);
        this.setInvulnerable(true); // 【新增】变身期间设置为无敌状态，确保绝对安全
    }

    public void enterPhaseTwo() {
        if (this.entityData.get(IS_PHASE_TWO)) return;
        
        this.entityData.set(IS_PHASE_TWO, true);
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(5000000.0D);
        this.setHealth(5000000.0F);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.35D);
        
        this.level().playSound(null, this.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0F, 1.0F);
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), 10, 1.0, 1.0, 1.0, 0.1);
        }
    }

    // ================= 【核心修复】伤害拦截 =================
    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 只要处于变身过渡状态，免疫一切伤害，防止被玩家乱刀砍死
        if (this.isTransforming) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        // 如果不是二阶段，且没有在变身中，拦截死亡
        if (!this.level().isClientSide && !this.entityData.get(IS_PHASE_TWO) && !this.isTransforming) {
            this.setHealth(1.0F); // 强行锁住 1 滴血
            this.startTransform(); // 开始 5 秒变身过渡
            return; // 阻止真正的死亡
        }
        super.die(source); // 否则，正常死亡
    }
    // =======================================================

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            // ================= 变身过渡逻辑 =================
            if (this.isTransforming) {
                this.transformTimer++;
                this.getNavigation().stop();
                this.setDeltaMovement(Vec3.ZERO);

                // 粒子环绕特效
                if (this.level() instanceof ServerLevel serverLevel) {
                    double angle = this.transformTimer * 0.3;
                    double radius = 2.0;
                    double x = this.getX() + Math.cos(angle) * radius;
                    double z = this.getZ() + Math.sin(angle) * radius;
                    
                    serverLevel.sendParticles(ParticleTypes.ENCHANT, x, this.getY() + 1.0, z, 5, 0, 0, 0, 0.1);
                    serverLevel.sendParticles(ParticleTypes.END_ROD, x, this.getY() + 1.5, z, 2, 0, 0, 0, 0);
                }

                // 5秒 = 100 ticks
                if (this.transformTimer >= 100) {
                    this.isTransforming = false;
                    this.setInvulnerable(false); // 取消无敌
                    
                    // 1. 制造不破坏方块的爆炸
                    this.level().explode(this, this.getX(), this.getY(), this.getZ(), 3.0F, false, Level.ExplosionInteraction.NONE);

                    // 2. 生成二阶段实体
                    if (this.level() instanceof ServerLevel serverLevel) {
                        EntityType<?> type = this.getType();
                        Entity newEntity = type.create(serverLevel);
                        if (newEntity instanceof TwoPhaseBossEntity boss) {
                            boss.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
                            boss.enterPhaseTwo();
                            serverLevel.addFreshEntity(boss);

                            // 3. 发送屏幕正中间标题（给50格内的玩家）
                            Component titleMsg = Component.literal("§4鸭神§e降临");
                            for (ServerPlayer player : serverLevel.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(50))) {
                                player.connection.send(new ClientboundSetTitleTextPacket(titleMsg));
                            }
                        }
                    }
                    this.discard(); // 删除一阶段实体
                }
                return; // 变身过程中跳过常规逻辑
            }
            // ===============================================

            // 冷却时间递减
            if (dashCooldown > 0) {
                dashCooldown--;
            }

            // 冲撞技能触发
            float healthRatio = this.getHealth() / this.getMaxHealth();
            if (this.entityData.get(IS_PHASE_TWO) && healthRatio <= 1.0f && healthRatio > 0.8f && dashCooldown <= 0 && !isCharging && !isDashing) {
                this.isCharging = true;
                this.chargeTimer = 0;
                this.dashCooldown = 500;
                
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

            // 蓄力阶段
            if (this.isCharging) {
                this.chargeTimer++;
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 1.0, this.getZ(), 5, 0.5, 0.5, 0.5, 0.1);
                }

                if (this.chargeTimer >= 40) {
                    this.isCharging = false;
                    this.isDashing = true;
                    this.dashTimer = 0;
                    this.setDeltaMovement(this.dashDirection.x * 0.5, 0, this.dashDirection.z * 0.5);
                }
            }

            // 冲刺阶段
            if (this.isDashing) {
                this.dashTimer++;
                this.setDeltaMovement(this.dashDirection.x * 0.5, this.getDeltaMovement().y, this.dashDirection.z * 0.5);
                
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.5, this.getZ(), 1, 0, 0, 0, 0);
                }

                for (Entity entity : this.level().getEntities(this, this.getBoundingBox().inflate(1.0))) {
                    if (entity instanceof LivingEntity living && entity != this) {
                        living.hurt(this.damageSources().mobAttack(this), 16.0F);
                    }
                }

                if (this.dashTimer >= 20 || this.horizontalCollision) {
                    this.isDashing = false;
                    this.setDeltaMovement(Vec3.ZERO);
                }
            }
        }
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

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100000.0D)
                .add(Attributes.ARMOR, 12.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.FOLLOW_RANGE, 35.0D);
    }

    public boolean isPhaseTwo() {
        return this.entityData.get(IS_PHASE_TWO);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("IsPhaseTwo", this.entityData.get(IS_PHASE_TWO));
        tag.putInt("DashCooldown", this.dashCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(IS_PHASE_TWO, tag.getBoolean("IsPhaseTwo"));
        this.dashCooldown = tag.getInt("DashCooldown");
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        this.triggerAnim("controller", "attack");
        return super.doHurtTarget(target);
    }
}
