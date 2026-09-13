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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TwoPhaseBossEntity extends PathfinderMob implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public static final EntityDataAccessor<Boolean> IS_PHASE_TWO =
            SynchedEntityData.defineId(TwoPhaseBossEntity.class, EntityDataSerializers.BOOLEAN);

    // ================= 全局技能状态机 =================
    private int skillCooldown = 0;
    private int chargeTimer = 0;
    private int dashTimer = 0;
    private int slamTimer = 0;
    private int recoveryTimer = 0;
    private int grabTimer = 0;
    
    private boolean isCharging = false;
    private boolean isDashing = false;
    private boolean isSlamming = false;
    private boolean isRecovering = false;
    private boolean isGrabbing = false;
    
    private Vec3 dashDirection = Vec3.ZERO;
    private double slamStartY = 0.0;
    private double slamTargetX = 0.0;
    private double slamTargetZ = 0.0;
    private double slamVelX = 0.0;
    private double slamVelZ = 0.0;
    private final List<Player> grabbedPlayers = new ArrayList<>();
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
        this.isGrabbing = false;
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
        if (this.isTransforming || this.isGrabbing) {
            return false; // 技能释放期间无敌，防止被打断
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
            if (skillCooldown > 0) {
                skillCooldown--;
            }

            // ================= 全局技能触发判定 =================
            if (this.entityData.get(IS_PHASE_TWO) && skillCooldown <= 0 && !isCharging && !isDashing && !isSlamming && !isRecovering && !isGrabbing) {
                Player nearestPlayer = this.level().getNearestPlayer(this, 50.0D);
                if (nearestPlayer != null) {
                    if (this.random.nextInt(40) == 0) {
                        float healthRatio = this.getHealth() / this.getMaxHealth();
                        
                        if (healthRatio > 0.8f) {
                            // 100%-80% 冲撞/撼地随机
                            if (this.random.nextBoolean()) {
                                startDash(nearestPlayer);
                            } else {
                                startSlam(nearestPlayer);
                            }
                        } else if (healthRatio > 0.5f) {
                            // 79%-50% 惯手 / 撼地 随机
                            if (this.random.nextBoolean()) {
                                startGrab();
                            } else {
                                startSlam(nearestPlayer);
                            }
                        } else {
                            // 50% 以下，全技能随机（目前是冲撞/撼地/惯手三选一）
                            int r = this.random.nextInt(3);
                            if (r == 0) {
                                startDash(nearestPlayer);
                            } else if (r == 1) {
                                startSlam(nearestPlayer);
                            } else {
                                startGrab();
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
                if (this.dashTimer >= 20 || this.horizontalCollision) {
                    this.isDashing = false;
                    this.setDeltaMovement(Vec3.ZERO);
                    this.skillCooldown = 500 + this.random.nextInt(100); 
                }
            }

            // ================= 撼地逻辑 =================
            if (this.isSlamming) {
                this.slamTimer++;
                
                if (this.slamTimer <= 60) {
                    this.getNavigation().stop();
                    if (this.getY() - this.slamStartY < 12.0) {
                        this.setDeltaMovement(0, 0.4, 0);
                    } else {
                        this.setDeltaMovement(0, 0, 0);
                    }
                    if (this.slamTimer % 10 == 0) {
                        this.triggerAnim("controller", "attack");
                        this.level().playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0F, 1.2F);
                    }
                } else if (this.slamTimer > 60 && this.slamTimer <= 360) {
                    this.setDeltaMovement(0, 0, 0);
                    
                    if (this.slamTimer % 20 == 0) {
                        Player nearestPlayer = this.level().getNearestPlayer(this, 50.0D);
                        if (nearestPlayer != null) {
                            this.slamTargetX = nearestPlayer.getX();
                            this.slamTargetZ = nearestPlayer.getZ();
                            
                            if (this.level() instanceof ServerLevel serverLevel) {
                                ItemEntity grassBlock = new ItemEntity(
                                    serverLevel,
                                    this.getX(), this.getY() + 2.0, this.getZ(),
                                    new ItemStack(Blocks.GRASS_BLOCK.asItem())
                                );
                                grassBlock.setNoGravity(true);
                                grassBlock.setPickUpDelay(Integer.MAX_VALUE);
                                grassBlock.addTag("boss_grass_projectile");
                                
                                Vec3 toPlayer = new Vec3(
                                    nearestPlayer.getX() - this.getX(),
                                    nearestPlayer.getY() + 1.0 - (this.getY() + 2.0),
                                    nearestPlayer.getZ() - this.getZ()
                                );
                                
                                double distance = toPlayer.length();
                                double speed = distance / 20.0;
                                if (speed > 1.5) speed = 1.5;
                                if (speed < 0.3) speed = 0.3;
                                
                                grassBlock.setDeltaMovement(toPlayer.normalize().scale(speed));
                                
                                serverLevel.addFreshEntity(grassBlock);
                                this.level().playSound(null, this.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.HOSTILE, 1.0F, 0.5F);
                            }
                        }
                    }
                    
                    if (this.slamTimer % 10 == 0) {
                        this.triggerAnim("controller", "attack");
                    }
                } else if (this.slamTimer > 360 && this.slamTimer <= 370) {
                    if (this.slamTimer == 361) {
                        double distanceX = this.slamTargetX - this.getX();
                        double distanceZ = this.slamTargetZ - this.getZ();
                        
                        double timeToLand = 10.0;
                        double velX = distanceX / timeToLand;
                        double velZ = distanceZ / timeToLand;
                        
                        double maxSpeed = 1.5;
                        double speedMag = Math.sqrt(velX * velX + velZ * velZ);
                        if (speedMag > maxSpeed) {
                            velX = (velX / speedMag) * maxSpeed;
                            velZ = (velZ / speedMag) * maxSpeed;
                        }
                        
                        this.slamVelX = velX;
                        this.slamVelZ = velZ;
                    }
                    
                    this.setDeltaMovement(this.slamVelX, -2.5, this.slamVelZ);
                    
                    if (this.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
                    }
                } else if (this.slamTimer > 370) {
                    this.isSlamming = false;
                    this.isRecovering = true;
                    this.recoveryTimer = 0;
                    this.setDeltaMovement(Vec3.ZERO);

                    this.level().playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5F, 0.5F);

                    for (Entity entity : this.level().getEntities(this, this.getBoundingBox().inflate(5.0))) {
                        if (entity instanceof LivingEntity living && entity != this) {
                            living.hurt(this.damageSources().mobAttack(this), 24.0F);
                            living.setDeltaMovement(living.getDeltaMovement().x, 1.2, living.getDeltaMovement().z);
                        }
                    }
                }
            }

            // ================= 惯手技能逻辑 =================
            if (this.isGrabbing) {
                this.grabTimer++;
                this.getNavigation().stop();
                this.setDeltaMovement(Vec3.ZERO);

                // 阶段1：拉人并禁锢1秒（前20 ticks）
                if (this.grabTimer <= 20) {
                    for (Player p : grabbedPlayers) {
                        // 强制将玩家拉回Boss身边
                        p.teleportTo(this.getX(), this.getY(), this.getZ());
                        p.setDeltaMovement(Vec3.ZERO);
                        // 施加缓慢效果（1秒，等级10）
                        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 10, false, false));
                    }
                }
                
                // 阶段2：1秒后释放玩家，但继续画火焰圈预警（第20到60 ticks）
                if (this.grabTimer == 21) {
                    for (Player p : grabbedPlayers) {
                        // 移除缓慢效果（已经过期了，这里是保险）
                        p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    }
                }

                // 火焰圈预警（每 5 ticks 画一次，持续 3 秒）
                if (this.grabTimer <= 60 && this.grabTimer % 5 == 0) {
                    if (this.level() instanceof ServerLevel serverLevel) {
                        for (int i = 0; i < 360; i += 10) {
                            double rad = Math.toRadians(i);
                            double x = this.getX() + Math.cos(rad) * 5.0;
                            double z = this.getZ() + Math.sin(rad) * 5.0;
                            serverLevel.sendParticles(ParticleTypes.FLAME, x, this.getY() + 0.5, z, 2, 0, 0, 0, 0.05);
                            serverLevel.sendParticles(ParticleTypes.LAVA, x, this.getY() + 0.5, z, 1, 0, 0, 0, 0.05);
                        }
                    }
                }

                // 阶段3：3秒后（60 ticks）引爆范围打击
                if (this.grabTimer > 60) {
                    this.isGrabbing = false;
                    this.skillCooldown = 600 + this.random.nextInt(100); // 30秒冷却
                    
                    if (this.level() instanceof ServerLevel serverLevel) {
                        // 爆炸音效
                        this.level().playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0F, 0.5F);
                        
                        // 范围伤害（5格内）
                        for (Entity entity : this.level().getEntities(this, this.getBoundingBox().inflate(5.0))) {
                            if (entity instanceof LivingEntity living && entity != this) {
                                living.hurt(this.damageSources().mobAttack(this), 20.0F);
                                living.setDeltaMovement(living.getDeltaMovement().x, 1.0, living.getDeltaMovement().z);
                            }
                        }
                        
                        // 爆炸粒子效果
                        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 1.0, this.getZ(), 3, 1.0, 1.0, 1.0, 0.1);
                    }
                }
            }

            // ================= 草方块实体跟踪与爆炸 =================
            if (this.level() instanceof ServerLevel serverLevel) {
                for (Entity entity : serverLevel.getEntities(this, this.getBoundingBox().inflate(50.0))) {
                    if (entity instanceof ItemEntity grassBlock && grassBlock.getTags().contains("boss_grass_projectile")) {
                        boolean shouldExplode = false;
                        
                        if (grassBlock.tickCount > 3 && (grassBlock.horizontalCollision || grassBlock.verticalCollision)) {
                            shouldExplode = true;
                        }
                        
                        if (!shouldExplode && grassBlock.tickCount > 3) {
                            for (Player p : serverLevel.getEntitiesOfClass(Player.class, grassBlock.getBoundingBox().inflate(1.5))) {
                                shouldExplode = true;
                                break;
                            }
                        }
                        
                        if (!shouldExplode && grassBlock.tickCount > 40) {
                            shouldExplode = true;
                        }
                        
                        if (shouldExplode) {
                            serverLevel.sendParticles(ParticleTypes.EXPLOSION, grassBlock.getX(), grassBlock.getY(), grassBlock.getZ(), 3, 0.2, 0.2, 0.2, 0.05);
                            serverLevel.playSound(null, grassBlock.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.0F, 1.5F);
                            
                            for (Entity nearby : serverLevel.getEntities(grassBlock, grassBlock.getBoundingBox().inflate(2.0))) {
                                if (nearby instanceof LivingEntity living && nearby != this) {
                                    living.hurt(this.damageSources().mobAttack(this), 8.0F);
                                }
                            }
                            
                            grassBlock.discard();
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
        this.slamStartY = this.getY();
        
        if (target != null) {
            this.slamTargetX = target.getX();
            this.slamTargetZ = target.getZ();
        } else {
            this.slamTargetX = this.getX();
            this.slamTargetZ = this.getZ();
        }
        
        this.triggerAnim("controller", "attack");
    }

    private void startGrab() {
        this.isGrabbing = true;
        this.grabTimer = 0;
        this.grabbedPlayers.clear();
        
        // 获取50格内所有玩家
        List<Player> players = this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(50.0));
        Collections.shuffle(players); // 随机打乱
        
        // 取前5名（如果没有5名，就有多少拿多少）
        int count = 0;
        for (Player p : players) {
            if (count >= 5) break;
            this.grabbedPlayers.add(p);
            count++;
        }
        
        this.triggerAnim("controller", "attack");
        this.level().playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5F, 0.5F);
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
