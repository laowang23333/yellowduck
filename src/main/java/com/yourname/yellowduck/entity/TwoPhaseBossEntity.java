package com.yourname.yellowduck.entity;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
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
import net.minecraft.world.level.block.Blocks;
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

    // ================= 全局技能状态机 =================
    private int skillCooldown = 0;      // 全局技能冷却
    private int chargeTimer = 0;        // 冲撞蓄力计时器
    private int dashTimer = 0;          // 冲撞冲刺计时器
    private int slamTimer = 0;          // 撼地计时器
    private int recoveryTimer = 0;      // 地形恢复计时器
    
    private boolean isCharging = false; // 是否在冲撞蓄力
    private boolean isDashing = false;  // 是否在冲撞冲刺
    private boolean isSlamming = false; // 是否在撼地
    private boolean isRecovering = false; // 是否在地形恢复期
    
    private Vec3 dashDirection = Vec3.ZERO;    // 冲撞方向
    private Vec3 slamTargetPos = Vec3.ZERO;    // 撼地落点
    // =================================================

    // ================= 变身过渡状态机 =================
    private boolean isTransforming = false;
    private int transformTimer = 0;
    // ===================================================

    // ================= 原版Boss血条 =================
    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.literal("小黄鸭"),
            BossEvent.BossBarColor.YELLOW,
            BossEvent.BossBarOverlay.PROGRESS
    );
    // ===============================================

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

    private void startTransform() {
        this.isTransforming = true;
        this.transformTimer = 0;
        this.isCharging = false;
        this.isDashing = false;
        this.isSlamming = false;
        this.isRecovering = false;
        this.setDeltaMovement(Vec3.ZERO);
        this.getNavigation().stop();
        this.setTarget(null);
        this.setInvulnerable(true);
    }

    public void enterPhaseTwo() {
        if (this.entityData.get(IS_PHASE_TWO)) return;
        
        this.entityData.set(IS_PHASE_TWO, true);
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(5000000.0D);
        this.setHealth(5000000.0F);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.35D);
        this.bossEvent.setName(Component.literal("肌肉大鸭"));
        
        this.level().playSound(null, this.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0F, 1.0F);
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), 10, 1.0, 1.0, 1.0, 0.1);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isTransforming) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide && !this.entityData.get(IS_PHASE_TWO) && !this.isTransforming) {
            this.setHealth(1.0F);
            this.startTransform();
            return;
        }
        super.die(source);
    }

    @Override
    public void tick() {
        // ================= 变身期间 =================
        if (this.isTransforming) {
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);

            if (!this.level().isClientSide) {
                this.transformTimer++;
                if (this.level() instanceof ServerLevel serverLevel) {
                    double angle = this.transformTimer * 0.3;
                    double radius = 2.0;
                    double x = this.getX() + Math.cos(angle) * radius;
                    double z = this.getZ() + Math.sin(angle) * radius;
                    serverLevel.sendParticles(ParticleTypes.ENCHANT, x, this.getY() + 1.0, z, 5, 0, 0, 0, 0.1);
                    serverLevel.sendParticles(ParticleTypes.END_ROD, x, this.getY() + 1.5, z, 2, 0, 0, 0, 0);
                }

                if (this.transformTimer >= 100) {
                    this.isTransforming = false;
                    this.setInvulnerable(false);
                    
                    this.level().explode(this, this.getX(), this.getY(), this.getZ(), 3.0F, false, Level.ExplosionInteraction.NONE);

                    if (this.level() instanceof ServerLevel serverLevel) {
                        EntityType<?> type = this.getType();
                        Entity newEntity = type.create(serverLevel);
                        if (newEntity instanceof TwoPhaseBossEntity boss) {
                            boss.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
                            boss.enterPhaseTwo();
                            serverLevel.addFreshEntity(boss);

                            Component titleMsg = Component.literal("§4鸭神§e降临");
                            for (ServerPlayer player : serverLevel.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(50))) {
                                player.connection.send(new ClientboundSetTitleTextPacket(titleMsg));
                            }
                        }
                    }
                    this.discard();
                }
            }
            return;
        }

        super.tick();
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());

        if (!this.level().isClientSide) {
            // 全局技能冷却递减
            if (skillCooldown > 0) {
                skillCooldown--;
            }

            // ================= 全局技能触发判定 =================
            if (this.entityData.get(IS_PHASE_TWO) && skillCooldown <= 0 && !isCharging && !isDashing && !isSlamming && !isRecovering) {
                Player nearestPlayer = this.level().getNearestPlayer(this, 35.0D);
                if (nearestPlayer != null) {
                    // 加入随机判定：平均每2秒判定一次，防止冷却一到就立刻放技能，让Boss显得有“大脑”
                    if (this.random.nextInt(40) == 0) {
                        float healthRatio = this.getHealth() / this.getMaxHealth();
                        
                        // 在 100%-80% 阶段，冲撞和撼地随机二选一
                        if (healthRatio > 0.8f) {
                            if (this.random.nextBoolean()) {
                                startDash(nearestPlayer);
                            } else {
                                startSlam(nearestPlayer);
                            }
                        } else {
                            // 80% 以下阶段，同样随机二选一（后续可以加入更多技能）
                            if (this.random.nextBoolean()) {
                                startDash(nearestPlayer);
                            } else {
                                startSlam(nearestPlayer);
                            }
                        }
                    }
                }
            }

            // ================= 冲撞逻辑 =================
            if (this.isCharging) {
                this.chargeTimer++;
                this.getNavigation().stop();
                this.setDeltaMovement(Vec3.ZERO);

                if (this.chargeTimer % 10 == 0) {
                    if (this.level() instanceof ServerLevel serverLevel) {
                        this.level().playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0F, 1.0F);
                        for (int i = 0; i < 360; i += 45) {
                            double rad = Math.toRadians(i + this.chargeTimer * 5);
                            double radius = 1.5;
                            double x = this.getX() + Math.cos(rad) * radius;
                            double z = this.getZ() + Math.sin(rad) * radius;
                            serverLevel.sendParticles(ParticleTypes.FLAME, x, this.getY() + 0.5, z, 3, 0, 0, 0, 0.05);
                            serverLevel.sendParticles(ParticleTypes.LAVA, x, this.getY() + 0.5, z, 1, 0, 0, 0, 0.05);
                            serverLevel.sendParticles(ParticleTypes.CRIT, x, this.getY() + 1.0, z, 5, 0, 0, 0, 0.1);
                        }
                    }
                }

                if (this.chargeTimer >= 60) {
                    this.isCharging = false;
                    this.isDashing = true;
                    this.dashTimer = 0;
                    this.setDeltaMovement(this.dashDirection.x * 0.5, 0, this.dashDirection.z * 0.5);
                }
            }

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
                // 冲撞结束条件
                if (this.dashTimer >= 20 || this.horizontalCollision) {
                    this.isDashing = false;
                    this.setDeltaMovement(Vec3.ZERO);
                    // 【核心修改】冲撞结束后，才开始计算冷却（25秒 + 0~5秒随机）
                    this.skillCooldown = 500 + this.random.nextInt(100); 
                }
            }

            // ================= 撼地逻辑 =================
            if (this.isSlamming) {
                this.slamTimer++;
                
                if (this.slamTimer <= 40) { // 前2秒：升空
                    this.getNavigation().stop();
                    this.setDeltaMovement(0, 1.5, 0);
                    if (this.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRASS_BLOCK.defaultBlockState()), 
                            this.getX(), this.getY() + 1.0, this.getZ(), 5, 0.5, 0.5, 0.5, 0.1);
                    }
                } else if (this.slamTimer > 40 && this.slamTimer <= 45) { // 急速下坠
                    this.setDeltaMovement(0, -2.5, 0);
                } else if (this.slamTimer > 45) { // 落地
                    this.isSlamming = false;
                    this.isRecovering = true;
                    this.recoveryTimer = 0;

                    this.level().playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5F, 0.5F);

                    for (Entity entity : this.level().getEntities(this, this.getBoundingBox().inflate(5.0))) {
                        if (entity instanceof LivingEntity living && entity != this) {
                            living.hurt(this.damageSources().mobAttack(this), 24.0F);
                            living.setDeltaMovement(living.getDeltaMovement().x, 1.2, living.getDeltaMovement().z);
                        }
                    }
                }
            }

            // ================= 地形恢复逻辑 =================
            if (this.isRecovering) {
                this.recoveryTimer++;
                this.getNavigation().stop();
                this.setDeltaMovement(Vec3.ZERO);

                if (this.level() instanceof ServerLevel serverLevel) {
                    for (int i = 0; i < 360; i += 30) {
                        double rad = Math.toRadians(i + this.recoveryTimer * 10);
                        double radius = 3.0;
                        double x = this.getX() + Math.cos(rad) * radius;
                        double z = this.getZ() + Math.sin(rad) * radius;
                        serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRASS_BLOCK.defaultBlockState()), 
                            x, this.getY() + 0.2, z, 2, 0, 0, 0, 0.05);
                    }
                }

                if (this.recoveryTimer >= 60) {
                    this.isRecovering = false;
                    // 【核心修改】地形恢复结束后，才开始计算冷却（25秒 + 0~5秒随机）
                    this.skillCooldown = 500 + this.random.nextInt(100);
                }
            }
        }
    }

    // ================= 技能启动辅助方法 =================
    private void startDash(Player target) {
        this.isCharging = true;
        this.chargeTimer = 0;
        this.dashDirection = new Vec3(
                target.getX() - this.getX(),
                0,
                target.getZ() - this.getZ()
        ).normalize();
    }

    private void startSlam(Player target) {
        this.isSlamming = true;
        this.slamTimer = 0;
        this.slamTargetPos = new Vec3(target.getX(), target.getY(), target.getZ());
        this.triggerAnim("controller", "attack");
    }
    // ===================================================

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
        tag.putInt("SkillCooldown", this.skillCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(IS_PHASE_TWO, tag.getBoolean("IsPhaseTwo"));
        this.skillCooldown = tag.getInt("SkillCooldown");
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        this.triggerAnim("controller", "attack");
        return super.doHurtTarget(target);
    }
}
