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
    private int laserTimer = 0;
    
    private boolean isCharging = false;
    private boolean isDashing = false;
    private boolean isSlamming = false;
    private boolean isRecovering = false;
    private boolean isGrabbing = false;
    private boolean isLasing = false;
    
    private Vec3 dashDirection = Vec3.ZERO;
    private double slamStartY = 0.0;
    private double slamTargetX = 0.0;
    private double slamTargetZ = 0.0;
    private double slamVelX = 0.0;
    private double slamVelZ = 0.0;
    private final List<Player> grabbedPlayers = new ArrayList<>();
    // =================================================

    // ================= 激光技能专用变量 =================
    private Player laserTarget = null;    // 激光锁定的玩家
    private Vec3 laserDir = Vec3.ZERO;    // 激光当前方向（用于缓慢追踪）
    // ====================================================

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
        this.isLasing = false;
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
        if (this.isTransforming || this.isGrabbing || this.isLasing) {
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
            if (skillCooldown > 0) {
                skillCooldown--;
            }

            // ================= 全局技能触发判定 =================
            if (this.entityData.get(IS_PHASE_TWO) && skillCooldown <= 0 && !isCharging && !isDashing && !isSlamming && !isRecovering && !isGrabbing && !isLasing) {
                Player nearestPlayer = this.level().getNearestPlayer(this, 50.0D);
                if (nearestPlayer != null) {
                    if (this.random.nextInt(40) == 0) {
                        float healthRatio = this.getHealth() / this.getMaxHealth();
                        
                        if (healthRatio > 0.8f) {
                            // 100%-80%：冲撞 / 撼地 随机
                            if (this.random.nextBoolean()) {
                                startDash(nearestPlayer);
                            } else {
                                startSlam(nearestPlayer);
                            }
                        } else {
                            // 80% 以下：撼地 / 惯手 / 毁灭光束 随机三选一
                            int r = this.random.nextInt(3);
                            if (r == 0) {
                                startSlam(nearestPlayer);
                            } else if (r == 1) {
                                startGrab();
                            } else {
                                startLaser();
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

                if (this.grabTimer <= 20) {
                    for (Player p : grabbedPlayers) {
                        p.teleportTo(this.getX(), this.getY(), this.getZ());
                        p.setDeltaMovement(Vec3.ZERO);
                        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 10, false, false));
                    }
                }
                
                if (this.grabTimer == 21) {
                    for (Player p : grabbedPlayers) {
                        p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    }
                }

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

                if (this.grabTimer > 60) {
                    this.isGrabbing = false;
                    this.skillCooldown = 600 + this.random.nextInt(100);
                    
                    if (this.level() instanceof ServerLevel serverLevel) {
                        this.level().playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0F, 0.5F);
                        
                        for (Entity entity : this.level().getEntities(this, this.getBoundingBox().inflate(5.0))) {
                            if (entity instanceof LivingEntity living && entity != this) {
                                float damage = 20.0F;
                                if (living instanceof Player p && grabbedPlayers.contains(p)) {
                                    damage = 40.0F;
                                }
                                living.hurt(this.damageSources().mobAttack(this), damage);
                                living.setDeltaMovement(living.getDeltaMovement().x, 1.0, living.getDeltaMovement().z);
                            }
                        }
                        
                        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 1.0, this.getZ(), 3, 1.0, 1.0, 1.0, 0.1);
                    }
                    
                    this.grabbedPlayers.clear();
                }
            }

            // ================= 毁灭光束技能逻辑 =================
            if (this.isLasing) {
                this.laserTimer++;
                this.getNavigation().stop();
                this.setDeltaMovement(Vec3.ZERO);
                
                if (this.level() instanceof ServerLevel serverLevel) {
                    // 阶段1：蓄力 5 秒（前 100 ticks）
                    if (this.laserTimer <= 100) {
                        if (this.laserTimer == 1) {
                            // 随机选一个有玩家的方向
                            List<Player> players = this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(50.0));
                            if (!players.isEmpty()) {
                                this.laserTarget = players.get(this.random.nextInt(players.size()));
                            } else {
                                this.laserTarget = this.level().getNearestPlayer(this, 50.0D);
                            }
                            this.laserDir = Vec3.ZERO;
                            this.level().playSound(null, this.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.0F, 0.5F);
                        }
                        
                        // 粒子在 Boss 面前汇聚
                        if (this.laserTimer % 3 == 0 && this.laserTarget != null) {
                            Vec3 toTarget = this.laserTarget.position().add(0, 1, 0).subtract(this.position().add(0, 1.5, 0)).normalize();
                            Vec3 spawnPos = this.position().add(0, 1.5, 0).add(toTarget.scale(2));
                            serverLevel.sendParticles(ParticleTypes.END_ROD, spawnPos.x, spawnPos.y, spawnPos.z, 12, 0.8, 0.8, 0.8, 0.2);
                            serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, spawnPos.x, spawnPos.y, spawnPos.z, 8, 0.6, 0.6, 0.6, 0.15);
                        }
                        
                        if (this.laserTimer == 100) {
                            this.level().playSound(null, this.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.0F, 0.5F);
                        }
                    }
                    // 阶段2：发射激光 10 秒（100 到 300 ticks）
                    else if (this.laserTimer <= 300) {
                        // 如果目标死了/跑了，重新随机选一个
                        if (this.laserTarget == null || !this.laserTarget.isAlive() || this.laserTarget.distanceTo(this) > 50) {
                            List<Player> players = this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(50.0));
                            if (!players.isEmpty()) {
                                this.laserTarget = players.get(this.random.nextInt(players.size()));
                            } else {
                                this.laserTarget = null;
                            }
                        }
                        
                        if (this.laserTarget != null) {
                            Vec3 laserStart = this.position().add(0, 1.5, 0);
                            Vec3 targetDir = this.laserTarget.position().add(0, 1, 0).subtract(laserStart).normalize();
                            
                            // 缓慢追踪（每 tick 只向目标方向转动 5%）
                            if (this.laserDir == Vec3.ZERO) {
                                this.laserDir = targetDir;
                            } else {
                                this.laserDir = this.laserDir.scale(0.95).add(targetDir.scale(0.05)).normalize();
                            }
                            
                            // 渲染粗激光束
                            renderLaserBeam(serverLevel, laserStart, this.laserDir, 40);
                            
                            // 每 10 ticks（0.5 秒）造成一次伤害
                            if (this.laserTimer % 10 == 0) {
                                damageEntitiesInBeam(serverLevel, laserStart, this.laserDir, 40);
                            }
                            
                            // 每 5 ticks 播放一次音效
                            if (this.laserTimer % 5 == 0) {
                                this.level().playSound(null, this.blockPosition(), SoundEvents.GUARDIAN_ATTACK, SoundSource.HOSTILE, 1.5F, 0.5F);
                            }
                        }
                    }
                    // 阶段3：结束
                    else {
                        this.isLasing = false;
                        this.laserTarget = null;
                        this.laserDir = Vec3.ZERO;
                        this.skillCooldown = 400 + this.random.nextInt(100); // 20 秒冷却
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

    // ================= 渲染粗激光束 =================
    private void renderLaserBeam(ServerLevel level, Vec3 start, Vec3 dir, double length) {
        Vec3 up = new Vec3(0, 1, 0);
        if (Math.abs(dir.dot(up)) > 0.99) up = new Vec3(1, 0, 0);
        Vec3 right = dir.cross(up).normalize();
        Vec3 upPerp = right.cross(dir).normalize();
        
        // 1. 内芯：亮白色实心光束（END_ROD）
        for (double d = 0; d < length; d += 0.4) {
            Vec3 p = start.add(dir.scale(d));
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.01);
        }
        
        // 2. 外层：紫色能量环（DRAGON_BREATH），形成光束的“粗”感（半径 1 格 = 粗 2 格）
        for (double d = 0; d < length; d += 0.5) {
            Vec3 center = start.add(dir.scale(d));
            for (int angle = 0; angle < 360; angle += 45) {
                double rad = Math.toRadians(angle + d * 30);
                double radius = 1.0; // 光束半径 1 格，直径 2 格
                double x = center.x + right.x * Math.cos(rad) * radius + upPerp.x * Math.sin(rad) * radius;
                double y = center.y + right.y * Math.cos(rad) * radius + upPerp.y * Math.sin(rad) * radius;
                double z = center.z + right.z * Math.cos(rad) * radius + upPerp.z * Math.sin(rad) * radius;
                level.sendParticles(ParticleTypes.DRAGON_BREATH, x, y, z, 1, 0, 0, 0, 0.02);
            }
        }
        
        // 3. 点缀：灵魂火粒子（蓝白色），让光束更有层次
        for (double d = 0; d < length; d += 1.0) {
            Vec3 p = start.add(dir.scale(d));
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.02);
        }
    }

    // ================= 激光束伤害判定 =================
    private void damageEntitiesInBeam(ServerLevel level, Vec3 start, Vec3 dir, double length) {
        for (Entity entity : level.getEntities(this, this.getBoundingBox().inflate(length))) {
            if (entity instanceof LivingEntity living && entity != this) {
                Vec3 toEntity = entity.position().add(0, entity.getBbHeight() / 2, 0).subtract(start);
                double proj = toEntity.dot(dir);
                if (proj > 0 && proj < length) {
                    Vec3 closestPoint = start.add(dir.scale(proj));
                    double dist = entity.position().distanceTo(closestPoint);
                    if (dist < 1.5) { // 光束半径 1.5 格内（比视觉略大，方便命中）
                        living.hurt(this.damageSources().mobAttack(this), 25.0F);
                        living.setDeltaMovement(living.getDeltaMovement().x, 0.3, living.getDeltaMovement().z);
                    }
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
        
        List<Player> players = this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(50.0));
        Collections.shuffle(players);
        
        int count = 0;
        for (Player p : players) {
            if (count >= 5) break;
            this.grabbedPlayers.add(p);
            count++;
        }
        
        this.triggerAnim("controller", "attack");
        this.level().playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5F, 0.5F);
    }

    private void startLaser() {
        this.isLasing = true;
        this.laserTimer = 0;
        this.laserTarget = null;
        this.laserDir = Vec3.ZERO;
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
