package com.yourname.yellowduck.entity;

import com.yourname.yellowduck.registry.ModSounds;
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
import net.minecraft.world.item.Items;
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
import java.util.Iterator;
import java.util.List;

public class TwoPhaseBossEntity extends PathfinderMob implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // ================= 所有需要同步到客户端的动画状态 =================
    public static final EntityDataAccessor<Boolean> IS_PHASE_TWO =
            SynchedEntityData.defineId(TwoPhaseBossEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> IS_TRANSFORMING =
            SynchedEntityData.defineId(TwoPhaseBossEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> IS_EMERGING =
            SynchedEntityData.defineId(TwoPhaseBossEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> IS_CHARGING =
            SynchedEntityData.defineId(TwoPhaseBossEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> IS_DASHING =
            SynchedEntityData.defineId(TwoPhaseBossEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> IS_SLAMMING =
            SynchedEntityData.defineId(TwoPhaseBossEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> SLAM_PHASE =
            SynchedEntityData.defineId(TwoPhaseBossEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> IS_ATTACKING =
            SynchedEntityData.defineId(TwoPhaseBossEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> IS_DASH_RECOVERING =
            SynchedEntityData.defineId(TwoPhaseBossEntity.class, EntityDataSerializers.BOOLEAN);
    // ================================================================

    // ================= 服务端技能状态机 =================
    private int skillCooldown = 0;
    private int chargeTimer = 0;
    private int dashTimer = 0;
    private int slamTimer = 0;
    private int recoveryTimer = 0;
    private int grabTimer = 0;
    private int laserTimer = 0;
    private int meteorTimer = 0;
    private int blackHoleTimer = 0;
    private int gazeTimer = 0;
    private int groundSlamTimer = 0;
    private int eggThrowTimer = 0;
    private int attackAnimTimer = 0;
    private int recoverAnimTimer = 0;

    // ================= 音效冷却 =================
    private int niganmaCooldown = 0;
    private boolean isPlayingNiganma = false;
    // ===========================================

    private boolean isGrabbing = false;
    private boolean isLasing = false;
    private boolean isMeteorShower = false;
    private boolean isBlackHole = false;
    private boolean isDeathGaze = false;
    private boolean isGroundSlam = false;
    private boolean isRecovering = false;
    private boolean isRageMode = false;

    private Vec3 dashDirection = Vec3.ZERO;
    private double slamStartY = 0.0;
    private double slamTargetX = 0.0;
    private double slamTargetZ = 0.0;
    private double slamVelX = 0.0;
    private double slamVelZ = 0.0;
    private final List<Player> grabbedPlayers = new ArrayList<>();
    private final List<ItemEntity> thrownEggs = new ArrayList<>();

    private Player laserTarget = null;
    private Vec3 laserDir = Vec3.ZERO;
    private Vec3 gazeDir = Vec3.ZERO;
    private Player gazeTarget = null;

    private int comboStep = 0;
    private int comboTimer = 0;
    private LivingEntity comboTarget = null;

    private int transformTimer = 0;
    private int emergeTimer = 0;
    // =================================================

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.literal("小黄鸭"),
            BossEvent.BossBarColor.YELLOW,
            BossEvent.BossBarOverlay.PROGRESS
    );

    public TwoPhaseBossEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public Component getName() {
        if (this.entityData.get(IS_PHASE_TWO)) return Component.literal("肌肉大鸭");
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
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 20.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_PHASE_TWO, false);
        this.entityData.define(IS_TRANSFORMING, false);
        this.entityData.define(IS_EMERGING, false);
        this.entityData.define(IS_CHARGING, false);
        this.entityData.define(IS_DASHING, false);
        this.entityData.define(IS_SLAMMING, false);
        this.entityData.define(SLAM_PHASE, 0);
        this.entityData.define(IS_ATTACKING, false);
        this.entityData.define(IS_DASH_RECOVERING, false);
    }

    private void playAttackAnim() {
        if (this.entityData.get(IS_PHASE_TWO)) {
            this.attackAnimTimer = 36;
        } else {
            this.attackAnimTimer = 30;
        }
        this.entityData.set(IS_ATTACKING, true);
    }

    private void startTransform() {
        this.entityData.set(IS_TRANSFORMING, true);
        this.transformTimer = 0;
        this.entityData.set(IS_CHARGING, false);
        this.entityData.set(IS_DASHING, false);
        this.entityData.set(IS_SLAMMING, false);
        this.entityData.set(SLAM_PHASE, 0);
        this.isRecovering = false;
        this.isGrabbing = false;
        this.isLasing = false;
        this.isMeteorShower = false;
        this.isBlackHole = false;
        this.isDeathGaze = false;
        this.isGroundSlam = false;
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

        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.removeAllGoals(goal -> true);
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));

        this.entityData.set(IS_EMERGING, true);
        this.emergeTimer = 0;
        this.setInvulnerable(true);

        this.level().playSound(null, this.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.entityData.get(IS_TRANSFORMING) || this.entityData.get(IS_EMERGING) || this.isGrabbing || this.isLasing || this.isDeathGaze || this.isBlackHole) {
            return false;
        }
        boolean result = super.hurt(source, amount);

        // ========== 一阶段受伤播放 niganma.ogg，带 3 秒冷却 ==========
        if (result && !this.level().isClientSide && !this.entityData.get(IS_PHASE_TWO)) {
            if (!this.isPlayingNiganma && this.niganmaCooldown <= 0) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        ModSounds.NIGANMA.get(), this.getSoundSource(), 1.0F, 1.0F);
                this.isPlayingNiganma = true;
                // 音频时长 + 3 秒冷却，约 80 tick（4秒）。若音效较长，把 80 改大
                this.niganmaCooldown = 80;
            }
        }
        // ==========================================================

        return result;
    }

    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide && !this.entityData.get(IS_PHASE_TWO) && !this.entityData.get(IS_TRANSFORMING)) {
            this.setHealth(1.0F);
            this.startTransform();
            return;
        }
        super.die(source);
    }

    private void dealDamage(LivingEntity target, float amount) {
        if (target == null || target == this || !target.isAlive()) return;

        Vec3 oldMotion = target.getDeltaMovement();
        boolean wasAlive = target.isAlive();

        target.hurt(this.damageSources().mobAttack(this), amount);
        target.setDeltaMovement(oldMotion);

        if (wasAlive && !target.isAlive() && target instanceof Player && isRageMode) {
            this.heal(500.0F);
            if (this.level() instanceof ServerLevel serverLevel) {
                this.level().playSound(null, this.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.HOSTILE, 1.5F, 1.0F);
                serverLevel.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 2.5, this.getZ(), 12, 0.6, 0.6, 0.6, 0.1);
            }
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (comboStep > 0) return false;
        if (!(target instanceof LivingEntity living)) return false;

        this.playAttackAnim();

        // ========== 一阶段近战攻击播放 jijiji.ogg ==========
        if (!this.level().isClientSide && !this.entityData.get(IS_PHASE_TWO)) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.JIJIJI.get(), this.getSoundSource(), 1.0F, 1.0F);
        }
        // ==================================================

        this.comboStep = 1;
        this.comboTimer = 0;
        this.comboTarget = living;
        return true;
    }

    private void tickCombo() {
        if (comboStep <= 0) return;
        comboTimer++;

        if (comboTarget == null || !comboTarget.isAlive() || this.distanceTo(comboTarget) > 5.0) {
            comboStep = 0;
            comboTarget = null;
            comboTimer = 0;
            return;
        }

        if (comboStep == 1 && comboTimer >= 8) {
            dealDamage(comboTarget, 12.0F);
            comboStep = 2;
            comboTimer = 0;
        } else if (comboStep == 2 && comboTimer >= 8) {
            dealDamage(comboTarget, 12.0F);
            comboStep = 3;
            comboTimer = 0;
        } else if (comboStep == 3 && comboTimer >= 11) {
            dealDamage(comboTarget, 18.0F);
            comboStep = 0;
            comboTarget = null;
            comboTimer = 0;
        }
    }

    @Override
    public void tick() {
        // 动画计时器递减（服务端）
        if (!this.level().isClientSide) {
            if (attackAnimTimer > 0) {
                attackAnimTimer--;
                if (attackAnimTimer <= 0) this.entityData.set(IS_ATTACKING, false);
            }
            if (recoverAnimTimer > 0) {
                recoverAnimTimer--;
                if (recoverAnimTimer <= 0) this.entityData.set(IS_DASH_RECOVERING, false);
            }
            // ========== 受伤音效冷却 ==========
            if (this.niganmaCooldown > 0) {
                this.niganmaCooldown--;
                if (this.niganmaCooldown <= 0) {
                    this.isPlayingNiganma = false;
                }
            }
            // ==================================
        }

        // ================= 一阶段变身期间 =================
        if (this.entityData.get(IS_TRANSFORMING)) {
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
                    this.entityData.set(IS_TRANSFORMING, false);
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

        // ================= 二阶段出场动画期间 =================
        if (this.entityData.get(IS_EMERGING)) {
            this.emergeTimer++;
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);

            if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
                for (int i = 0; i < 360; i += 60) {
                    double rad = Math.toRadians(i);
                    double x = this.getX() + Math.cos(rad) * 2.0;
                    double z = this.getZ() + Math.sin(rad) * 2.0;
                    serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRASS_BLOCK.defaultBlockState()),
                            x, this.getY() + 0.2, z, 3, 0, 0, 0, 0.05);
                }
            }

            if (this.emergeTimer >= 35) {
                this.entityData.set(IS_EMERGING, false);
                this.setInvulnerable(false);
                this.level().playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.5F);
            }

            super.tick();
            this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
            return;
        }

        super.tick();
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());

        if (!this.level().isClientSide) {
            if (skillCooldown > 0) skillCooldown--;

            this.tickCombo();

            // ================= 一阶段：投掷鸡蛋 =================
            if (!this.entityData.get(IS_PHASE_TWO)) {
                this.eggThrowTimer++;

                if (this.eggThrowTimer >= 30) {
                    this.eggThrowTimer = 0;
                    Player target = this.level().getNearestPlayer(this, 20.0D);
                    if (target != null && this.level() instanceof ServerLevel serverLevel) {
                        // 用 ItemEntity 替代 ThrownEgg：不会生成小鸡
                        ItemEntity egg = new ItemEntity(serverLevel, this.getX(), this.getY() + 1.5, this.getZ(), new ItemStack(Items.EGG));
                        egg.setNoGravity(true);
                        egg.setPickUpDelay(Integer.MAX_VALUE);
                        egg.addTag("boss_egg_projectile");

                        double dx = target.getX() - this.getX();
                        double dy = target.getY() + 0.5 - (this.getY() + 1.5);
                        double dz = target.getZ() - this.getZ();
                        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (dist > 0.01) {
                            double speed = 1.0;
                            egg.setDeltaMovement(dx / dist * speed, dy / dist * speed + 0.15, dz / dist * speed);
                        }

                        serverLevel.addFreshEntity(egg);
                        this.thrownEggs.add(egg);
                        this.level().playSound(null, this.blockPosition(), SoundEvents.EGG_THROW, SoundSource.HOSTILE, 1.0F, 1.0F);
                        // 扔鸡蛋时播放 jijiji.ogg
                        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                                ModSounds.JIJIJI.get(), this.getSoundSource(), 1.0F, 1.0F);
                        this.playAttackAnim();
                    }
                }

                Iterator<ItemEntity> it = this.thrownEggs.iterator();
                while (it.hasNext()) {
                    ItemEntity egg = it.next();
                    if (!egg.isAlive() || egg.tickCount > 60) {
                        egg.discard();
                        it.remove();
                        continue;
                    }
                    if (this.level() instanceof ServerLevel serverLevel) {
                        boolean hitPlayer = false;
                        for (Player p : serverLevel.getEntitiesOfClass(Player.class, egg.getBoundingBox().inflate(1.0))) {
                            dealDamage(p, 8.0F);
                            serverLevel.sendParticles(ParticleTypes.CRIT, egg.getX(), egg.getY(), egg.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
                            this.level().playSound(null, egg.blockPosition(), SoundEvents.EGG_THROW, SoundSource.HOSTILE, 1.0F, 1.5F);
                            egg.discard();
                            it.remove();
                            hitPlayer = true;
                            break;
                        }
                        if (hitPlayer) continue;
                    }
                    if (egg.horizontalCollision || egg.verticalCollision) {
                        egg.discard();
                        it.remove();
                    }
                }
            }

            // ================= 狂暴形态 =================
            float healthRatio = this.getHealth() / this.getMaxHealth();
            if (this.entityData.get(IS_PHASE_TWO) && healthRatio < 0.5f && !isRageMode) {
                this.isRageMode = true;
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.42D);
                this.level().playSound(null, this.blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 1.5F, 1.5F);
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER, this.getX(), this.getY() + 1.5, this.getZ(), 30, 1.0, 1.0, 1.0, 0.2);
                }
            }

            if (isRageMode && this.tickCount % 5 == 0 && this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.getX(), this.getY() + 1.5, this.getZ(), 8, 0.5, 0.5, 0.5, 0.05);
            }

            // ================= 全局技能触发 =================
            if (this.entityData.get(IS_PHASE_TWO) && skillCooldown <= 0 && !isBusy() && comboStep == 0) {
                Player nearestPlayer = this.level().getNearestPlayer(this, 50.0D);
                if (nearestPlayer != null && this.random.nextInt(15) == 0) {
                    if (healthRatio > 0.8f) {
                        if (this.random.nextBoolean()) startDash(nearestPlayer);
                        else startSlam(nearestPlayer);
                    } else if (healthRatio > 0.5f) {
                        int r = this.random.nextInt(3);
                        if (r == 0) startSlam(nearestPlayer);
                        else if (r == 1) startGrab();
                        else startLaser();
                    } else {
                        int r = this.random.nextInt(7);
                        switch (r) {
                            case 0: startSlam(nearestPlayer); break;
                            case 1: startGrab(); break;
                            case 2: startLaser(); break;
                            case 3: startMeteorShower(); break;
                            case 4: startBlackHole(); break;
                            case 5: startDeathGaze(); break;
                            default: startGroundSlam(); break;
                        }
                    }
                }
            }

            // ================= 冲撞 =================
            if (this.entityData.get(IS_CHARGING)) {
                this.chargeTimer++;
                this.getNavigation().stop();
                this.setDeltaMovement(Vec3.ZERO);

                if (this.chargeTimer % 10 == 0 && this.level() instanceof ServerLevel serverLevel) {
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

                if (this.chargeTimer >= 60) {
                    this.entityData.set(IS_CHARGING, false);
                    this.entityData.set(IS_DASHING, true);
                    this.dashTimer = 0;
                    this.setDeltaMovement(this.dashDirection.x * 0.5, 0, this.dashDirection.z * 0.5);
                }
            }

            if (this.entityData.get(IS_DASHING)) {
                this.dashTimer++;
                this.setDeltaMovement(this.dashDirection.x * 0.5, this.getDeltaMovement().y, this.dashDirection.z * 0.5);

                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.5, this.getZ(), 1, 0, 0, 0, 0);
                }
                for (Entity entity : this.level().getEntities(this, this.getBoundingBox().inflate(1.0))) {
                    if (entity instanceof LivingEntity living && entity != this) {
                        float dmg = isRageMode ? 32.0F : 16.0F;
                        dealDamage(living, dmg);
                    }
                }
                if (this.dashTimer >= 20 || this.horizontalCollision) {
                    this.entityData.set(IS_DASHING, false);
                    this.setDeltaMovement(Vec3.ZERO);
                    this.recoverAnimTimer = 16;
                    this.entityData.set(IS_DASH_RECOVERING, true);
                    this.skillCooldown = 500 + this.random.nextInt(100);
                }
            }

            // ================= 撼地 =================
            if (this.entityData.get(IS_SLAMMING)) {
                this.slamTimer++;

                if (this.slamTimer <= 60) {
                    this.entityData.set(SLAM_PHASE, 1);
                    this.getNavigation().stop();
                    if (this.getY() - this.slamStartY < 12.0) this.setDeltaMovement(0, 0.4, 0);
                    else this.setDeltaMovement(0, 0, 0);
                    if (this.slamTimer % 10 == 0) {
                        this.level().playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0F, 1.2F);
                    }
                } else if (this.slamTimer > 60 && this.slamTimer <= 360) {
                    this.entityData.set(SLAM_PHASE, 2);
                    this.setDeltaMovement(0, 0, 0);

                    if (this.slamTimer % 20 == 0) {
                        Player nearestPlayer = this.level().getNearestPlayer(this, 50.0D);
                        if (nearestPlayer != null) {
                            this.slamTargetX = nearestPlayer.getX();
                            this.slamTargetZ = nearestPlayer.getZ();
                            if (this.level() instanceof ServerLevel serverLevel) {
                                ItemEntity grassBlock = new ItemEntity(serverLevel, this.getX(), this.getY() + 2.0, this.getZ(), new ItemStack(Blocks.GRASS_BLOCK.asItem()));
                                grassBlock.setNoGravity(true);
                                grassBlock.setPickUpDelay(Integer.MAX_VALUE);
                                grassBlock.addTag("boss_grass_projectile");
                                Vec3 toPlayer = new Vec3(nearestPlayer.getX() - this.getX(), nearestPlayer.getY() + 1.0 - (this.getY() + 2.0), nearestPlayer.getZ() - this.getZ());
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
                    if (this.slamTimer % 10 == 0) this.playAttackAnim();
                } else if (this.slamTimer > 360 && this.slamTimer <= 370) {
                    if (this.slamTimer == 361) {
                        double distanceX = this.slamTargetX - this.getX();
                        double distanceZ = this.slamTargetZ - this.getZ();
                        double timeToLand = 10.0;
                        double velX = distanceX / timeToLand;
                        double velZ = distanceZ / timeToLand;
                        double maxSpeed = 1.5;
                        double speedMag = Math.sqrt(velX * velX + velZ * velZ);
                        if (speedMag > maxSpeed) { velX = (velX / speedMag) * maxSpeed; velZ = (velZ / speedMag) * maxSpeed; }
                        this.slamVelX = velX;
                        this.slamVelZ = velZ;
                    }
                    this.setDeltaMovement(this.slamVelX, -2.5, this.slamVelZ);
                } else if (this.slamTimer > 370) {
                    this.entityData.set(IS_SLAMMING, false);
                    this.entityData.set(SLAM_PHASE, 3);
                    this.isRecovering = true;
                    this.recoveryTimer = 0;
                    this.setDeltaMovement(Vec3.ZERO);
                    this.level().playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5F, 0.5F);
                    float slamDmg = isRageMode ? 40.0F : 24.0F;
                    for (Entity entity : this.level().getEntities(this, this.getBoundingBox().inflate(5.0))) {
                        if (entity instanceof LivingEntity living && entity != this) {
                            dealDamage(living, slamDmg);
                            living.setDeltaMovement(living.getDeltaMovement().x, 1.2, living.getDeltaMovement().z);
                        }
                    }
                }
            }

            // ================= 惯手 =================
            if (this.isGrabbing) {
                this.grabTimer++;
                this.getNavigation().stop();
                this.setDeltaMovement(Vec3.ZERO);

                if (this.grabTimer == 1) this.playAttackAnim();

                if (this.grabTimer <= 20) {
                    for (Player p : grabbedPlayers) {
                        p.teleportTo(this.getX(), this.getY(), this.getZ());
                        p.setDeltaMovement(Vec3.ZERO);
                        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 10, false, false));
                    }
                }
                if (this.grabTimer == 21) {
                    for (Player p : grabbedPlayers) p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                }
                if (this.grabTimer <= 60 && this.grabTimer % 5 == 0 && this.level() instanceof ServerLevel serverLevel) {
                    for (int i = 0; i < 360; i += 10) {
                        double rad = Math.toRadians(i);
                        double x = this.getX() + Math.cos(rad) * 5.0;
                        double z = this.getZ() + Math.sin(rad) * 5.0;
                        serverLevel.sendParticles(ParticleTypes.FLAME, x, this.getY() + 0.5, z, 2, 0, 0, 0, 0.05);
                        serverLevel.sendParticles(ParticleTypes.LAVA, x, this.getY() + 0.5, z, 1, 0, 0, 0, 0.05);
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
                                if (living instanceof Player p && grabbedPlayers.contains(p)) damage = 40.0F;
                                dealDamage(living, damage);
                                living.setDeltaMovement(living.getDeltaMovement().x, 1.0, living.getDeltaMovement().z);
                            }
                        }
                        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 1.0, this.getZ(), 3, 1.0, 1.0, 1.0, 0.1);
                    }
                    this.grabbedPlayers.clear();
                }
            }

            // ================= 毁灭光束 =================
            if (this.isLasing) {
                this.laserTimer++;
                this.getNavigation().stop();
                this.setDeltaMovement(Vec3.ZERO);

                if (this.level() instanceof ServerLevel serverLevel) {
                    if (this.laserTimer <= 100) {
                        if (this.laserTimer == 1) {
                            List<Player> players = this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(50.0));
                            if (!players.isEmpty()) this.laserTarget = players.get(this.random.nextInt(players.size()));
                            this.laserDir = Vec3.ZERO;
                            this.playAttackAnim();
                            this.level().playSound(null, this.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.0F, 0.5F);
                        }
                        if (this.laserTimer % 3 == 0 && this.laserTarget != null) {
                            Vec3 toTarget = this.laserTarget.position().add(0, 1, 0).subtract(this.position().add(0, 1.5, 0)).normalize();
                            Vec3 spawnPos = this.position().add(0, 1.5, 0).add(toTarget.scale(2));
                            serverLevel.sendParticles(ParticleTypes.END_ROD, spawnPos.x, spawnPos.y, spawnPos.z, 12, 0.8, 0.8, 0.8, 0.2);
                            serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, spawnPos.x, spawnPos.y, spawnPos.z, 8, 0.6, 0.6, 0.6, 0.15);
                        }
                        if (this.laserTimer == 100) {
                            this.level().playSound(null, this.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.0F, 0.5F);
                        }
                    } else if (this.laserTimer <= 300) {
                        if (this.laserTarget == null || !this.laserTarget.isAlive() || this.laserTarget.distanceTo(this) > 50) {
                            List<Player> players = this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(50.0));
                            this.laserTarget = players.isEmpty() ? null : players.get(this.random.nextInt(players.size()));
                        }
                        if (this.laserTarget != null) {
                            Vec3 laserStart = this.position().add(0, 1.5, 0);
                            Vec3 targetDir = this.laserTarget.position().add(0, 1, 0).subtract(laserStart).normalize();
                            if (this.laserDir == Vec3.ZERO) this.laserDir = targetDir;
                            else this.laserDir = this.laserDir.scale(0.95).add(targetDir.scale(0.05)).normalize();
                            renderLaserBeam(serverLevel, laserStart, this.laserDir, 40);
                            if (this.laserTimer % 10 == 0) damageEntitiesInBeam(serverLevel, laserStart, this.laserDir, 40, 25.0F);
                            if (this.laserTimer % 5 == 0) this.level().playSound(null, this.blockPosition(), SoundEvents.GUARDIAN_ATTACK, SoundSource.HOSTILE, 1.5F, 0.5F);
                        }
                    } else {
                        this.isLasing = false;
                        this.laserTarget = null;
                        this.laserDir = Vec3.ZERO;
                        this.skillCooldown = 250 + this.random.nextInt(100);
                    }
                }
            }

            // ================= 陨石雨 =================
            if (this.isMeteorShower) {
                this.meteorTimer++;
                this.getNavigation().stop();
                if (this.meteorTimer == 1) this.playAttackAnim();
                if (this.meteorTimer <= 60) {
                    if (this.getY() - this.slamStartY < 15.0) this.setDeltaMovement(0, 0.5, 0);
                    else this.setDeltaMovement(0, 0, 0);
                } else if (this.meteorTimer <= 220) {
                    this.setDeltaMovement(0, 0, 0);
                    if (this.meteorTimer % 8 == 0) {
                        List<Player> players = this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(50.0));
                        if (!players.isEmpty() && this.level() instanceof ServerLevel serverLevel) {
                            Player target = players.get(this.random.nextInt(players.size()));
                            ItemEntity meteor = new ItemEntity(serverLevel, this.getX(), this.getY(), this.getZ(), new ItemStack(Blocks.GRASS_BLOCK.asItem()));
                            meteor.setNoGravity(true);
                            meteor.setPickUpDelay(Integer.MAX_VALUE);
                            meteor.addTag("boss_meteor_projectile");
                            Vec3 toTarget = new Vec3(target.getX() - this.getX(), target.getY() + 1.0 - this.getY(), target.getZ() - this.getZ()).normalize().scale(1.5);
                            meteor.setDeltaMovement(toTarget);
                            serverLevel.addFreshEntity(meteor);
                            this.level().playSound(null, this.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 1.0F, 0.8F);
                        }
                    }
                } else if (this.meteorTimer > 220 && this.meteorTimer <= 230) {
                    this.setDeltaMovement(0, -2.5, 0);
                } else if (this.meteorTimer > 230) {
                    this.isMeteorShower = false;
                    this.isRecovering = true;
                    this.recoveryTimer = 0;
                    this.setDeltaMovement(Vec3.ZERO);
                    this.level().playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0F, 0.5F);
                    for (Entity entity : this.level().getEntities(this, this.getBoundingBox().inflate(6.0))) {
                        if (entity instanceof LivingEntity living && entity != this) {
                            dealDamage(living, isRageMode ? 30.0F : 18.0F);
                            living.setDeltaMovement(living.getDeltaMovement().x, 1.2, living.getDeltaMovement().z);
                        }
                    }
                    this.skillCooldown = 300 + this.random.nextInt(100);
                }
            }

            // ================= 黑洞 =================
            if (this.isBlackHole) {
                this.blackHoleTimer++;
                this.getNavigation().stop();
                this.setDeltaMovement(Vec3.ZERO);
                if (this.blackHoleTimer == 1) this.playAttackAnim();
                if (this.level() instanceof ServerLevel serverLevel) {
                    if (this.blackHoleTimer <= 60) {
                        for (int i = 0; i < 360; i += 20) {
                            double rad = Math.toRadians(i + this.blackHoleTimer * 8);
                            double x = this.getX() + Math.cos(rad) * 2.0;
                            double z = this.getZ() + Math.sin(rad) * 2.0;
                            serverLevel.sendParticles(ParticleTypes.SQUID_INK, x, this.getY() + 1.5, z, 3, 0, 0, 0, 0.05);
                            serverLevel.sendParticles(ParticleTypes.PORTAL, x, this.getY() + 1.5, z, 2, 0, 0, 0, 0.1);
                        }
                        if (this.blackHoleTimer == 1) this.level().playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.0F, 0.5F);
                        for (Player p : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(30.0))) {
                            Vec3 pull = this.position().add(0, 1, 0).subtract(p.position()).normalize().scale(0.3);
                            p.setDeltaMovement(p.getDeltaMovement().add(pull));
                            p.hurtMarked = true;
                        }
                    } else if (this.blackHoleTimer > 60 && this.blackHoleTimer <= 70) {
                        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 1.0, this.getZ(), 2, 1.0, 1.0, 1.0, 0.2);
                        if (this.blackHoleTimer == 61) {
                            this.level().playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0F, 0.5F);
                            for (Entity entity : this.level().getEntities(this, this.getBoundingBox().inflate(5.0))) {
                                if (entity instanceof LivingEntity living && entity != this) {
                                    dealDamage(living, isRageMode ? 45.0F : 30.0F);
                                    living.setDeltaMovement(living.getDeltaMovement().x, 1.5, living.getDeltaMovement().z);
                                }
                            }
                        }
                    } else if (this.blackHoleTimer > 70) {
                        this.isBlackHole = false;
                        this.skillCooldown = 350 + this.random.nextInt(100);
                    }
                }
            }

            // ================= 死亡凝视 =================
            if (this.isDeathGaze) {
                this.gazeTimer++;
                this.getNavigation().stop();
                this.setDeltaMovement(Vec3.ZERO);
                if (this.gazeTimer == 1) this.playAttackAnim();
                if (this.level() instanceof ServerLevel serverLevel) {
                    if (this.gazeTimer <= 40) {
                        if (this.gazeTimer == 1) {
                            List<Player> players = this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(50.0));
                            if (!players.isEmpty()) {
                                this.gazeTarget = players.get(this.random.nextInt(players.size()));
                                Vec3 gazeStart = this.position().add(0, 1.5, 0);
                                this.gazeDir = this.gazeTarget.position().add(0, 1, 0).subtract(gazeStart).normalize();
                            }
                            this.level().playSound(null, this.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.0F, 0.5F);
                        }
                        if (this.gazeTarget != null) {
                            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.gazeTarget.getX(), this.gazeTarget.getY() + 1.0, this.gazeTarget.getZ(), 8, 0.5, 1.0, 0.5, 0.1);
                            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 2.0, this.getZ(), 3, 0.2, 0.2, 0.2, 0.05);
                        }
                    } else if (this.gazeTimer <= 100) {
                        Vec3 gazeStart = this.position().add(0, 1.5, 0);
                        renderDeathGazeBeam(serverLevel, gazeStart, this.gazeDir, 40);
                        if (this.gazeTimer % 6 == 0) {
                            damageEntitiesInBeam(serverLevel, gazeStart, this.gazeDir, 40, 20.0F);
                            this.level().playSound(null, this.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.5F, 0.5F);
                        }
                    } else {
                        this.isDeathGaze = false;
                        this.gazeTarget = null;
                        this.gazeDir = Vec3.ZERO;
                        this.skillCooldown = 250 + this.random.nextInt(100);
                    }
                }
            }

            // ================= 裂地斩 =================
            if (this.isGroundSlam) {
                this.groundSlamTimer++;
                this.getNavigation().stop();
                this.setDeltaMovement(Vec3.ZERO);
                if (this.level() instanceof ServerLevel serverLevel) {
                    if (this.groundSlamTimer <= 30) {
                        if (this.groundSlamTimer == 1) {
                            this.playAttackAnim();
                            this.level().playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.5F);
                        }
                        for (int i = -60; i <= 60; i += 10) {
                            double rad = Math.toRadians(this.getYRot() + i);
                            for (int d = 1; d <= 8; d++) {
                                double x = this.getX() + Math.sin(rad) * d;
                                double z = this.getZ() + Math.cos(rad) * d;
                                serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, this.getY() + 0.2, z, 1, 0, 0, 0, 0.02);
                            }
                        }
                    } else if (this.groundSlamTimer > 30 && this.groundSlamTimer <= 40) {
                        if (this.groundSlamTimer == 31) {
                            this.level().playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0F, 0.5F);
                            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 1.0, this.getZ(), 2, 2.0, 0.5, 2.0, 0.1);
                            for (Entity entity : this.level().getEntities(this, this.getBoundingBox().inflate(8.0))) {
                                if (entity instanceof LivingEntity living && entity != this) {
                                    Vec3 toEntity = entity.position().subtract(this.position());
                                    double dist = toEntity.length();
                                    double dot = toEntity.normalize().dot(this.getLookAngle());
                                    if (dist < 8.0 && dot > 0.5) {
                                        dealDamage(living, isRageMode ? 45.0F : 30.0F);
                                        living.setDeltaMovement(living.getDeltaMovement().x, 1.0, living.getDeltaMovement().z);
                                    }
                                }
                            }
                        }
                    } else if (this.groundSlamTimer > 40) {
                        this.isGroundSlam = false;
                        this.skillCooldown = 200 + this.random.nextInt(100);
                    }
                }
            }

            // ================= 草方块爆炸 =================
            if (this.level() instanceof ServerLevel serverLevel) {
                for (Entity entity : serverLevel.getEntities(this, this.getBoundingBox().inflate(50.0))) {
                    if (entity instanceof ItemEntity grassBlock && (grassBlock.getTags().contains("boss_grass_projectile") || grassBlock.getTags().contains("boss_meteor_projectile"))) {
                        boolean isMeteor = grassBlock.getTags().contains("boss_meteor_projectile");
                        boolean shouldExplode = false;
                        if (grassBlock.tickCount > 3 && (grassBlock.horizontalCollision || grassBlock.verticalCollision)) shouldExplode = true;
                        if (!shouldExplode && grassBlock.tickCount > 3) {
                            for (Player p : serverLevel.getEntitiesOfClass(Player.class, grassBlock.getBoundingBox().inflate(1.5))) { shouldExplode = true; break; }
                        }
                        if (!shouldExplode && grassBlock.tickCount > 40) shouldExplode = true;
                        if (shouldExplode) {
                            serverLevel.sendParticles(ParticleTypes.EXPLOSION, grassBlock.getX(), grassBlock.getY(), grassBlock.getZ(), 3, 0.2, 0.2, 0.2, 0.05);
                            serverLevel.playSound(null, grassBlock.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.0F, 1.5F);
                            float dmg = isMeteor ? 15.0F : 8.0F;
                            for (Entity nearby : serverLevel.getEntities(grassBlock, grassBlock.getBoundingBox().inflate(2.0))) {
                                if (nearby instanceof LivingEntity living && nearby != this) dealDamage(living, dmg);
                            }
                            grassBlock.discard();
                        }
                    }
                }
            }

            // ================= 地形恢复 =================
            if (this.isRecovering) {
                this.recoveryTimer++;
                this.getNavigation().stop();
                this.setDeltaMovement(Vec3.ZERO);
                if (this.level() instanceof ServerLevel serverLevel) {
                    for (int i = 0; i < 360; i += 30) {
                        double rad = Math.toRadians(i + this.recoveryTimer * 10);
                        double x = this.getX() + Math.cos(rad) * 3.0;
                        double z = this.getZ() + Math.sin(rad) * 3.0;
                        serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRASS_BLOCK.defaultBlockState()), x, this.getY() + 0.2, z, 2, 0, 0, 0, 0.05);
                    }
                }
                if (this.recoveryTimer >= 60) {
                    this.isRecovering = false;
                    this.entityData.set(SLAM_PHASE, 0);
                    this.skillCooldown = 500 + this.random.nextInt(100);
                }
            }
        }
    }

    private boolean isBusy() {
        return this.entityData.get(IS_CHARGING) || this.entityData.get(IS_DASHING) || this.entityData.get(IS_SLAMMING) || isRecovering || isGrabbing
                || isLasing || isMeteorShower || isBlackHole || isDeathGaze || isGroundSlam;
    }

    private void renderLaserBeam(ServerLevel level, Vec3 start, Vec3 dir, double length) {
        Vec3 up = new Vec3(0, 1, 0);
        if (Math.abs(dir.dot(up)) > 0.99) up = new Vec3(1, 0, 0);
        Vec3 right = dir.cross(up).normalize();
        Vec3 upPerp = right.cross(dir).normalize();
        for (double d = 0; d < length; d += 0.4) {
            Vec3 p = start.add(dir.scale(d));
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.01);
        }
        for (double d = 0; d < length; d += 0.5) {
            Vec3 center = start.add(dir.scale(d));
            for (int angle = 0; angle < 360; angle += 45) {
                double rad = Math.toRadians(angle + d * 30);
                double radius = 1.0;
                double x = center.x + right.x * Math.cos(rad) * radius + upPerp.x * Math.sin(rad) * radius;
                double y = center.y + right.y * Math.cos(rad) * radius + upPerp.y * Math.sin(rad) * radius;
                double z = center.z + right.z * Math.cos(rad) * radius + upPerp.z * Math.sin(rad) * radius;
                level.sendParticles(ParticleTypes.DRAGON_BREATH, x, y, z, 1, 0, 0, 0, 0.02);
            }
        }
        for (double d = 0; d < length; d += 1.0) {
            Vec3 p = start.add(dir.scale(d));
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.02);
        }
    }

    private void renderDeathGazeBeam(ServerLevel level, Vec3 start, Vec3 dir, double length) {
        Vec3 up = new Vec3(0, 1, 0);
        if (Math.abs(dir.dot(up)) > 0.99) up = new Vec3(1, 0, 0);
        Vec3 right = dir.cross(up).normalize();
        Vec3 upPerp = right.cross(dir).normalize();
        for (double d = 0; d < length; d += 0.3) {
            Vec3 p = start.add(dir.scale(d));
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.01);
        }
        for (double d = 0; d < length; d += 0.4) {
            Vec3 center = start.add(dir.scale(d));
            for (int angle = 0; angle < 360; angle += 60) {
                double rad = Math.toRadians(angle + d * 20);
                double radius = 0.8;
                double x = center.x + right.x * Math.cos(rad) * radius + upPerp.x * Math.sin(rad) * radius;
                double y = center.y + right.y * Math.cos(rad) * radius + upPerp.y * Math.sin(rad) * radius;
                double z = center.z + right.z * Math.cos(rad) * radius + upPerp.z * Math.sin(rad) * radius;
                level.sendParticles(ParticleTypes.LAVA, x, y, z, 1, 0, 0, 0, 0.02);
            }
        }
    }

    private void damageEntitiesInBeam(ServerLevel level, Vec3 start, Vec3 dir, double length, float damage) {
        for (Entity entity : level.getEntities(this, this.getBoundingBox().inflate(length))) {
            if (entity instanceof LivingEntity living && entity != this) {
                Vec3 toEntity = entity.position().add(0, entity.getBbHeight() / 2, 0).subtract(start);
                double proj = toEntity.dot(dir);
                if (proj > 0 && proj < length) {
                    Vec3 closestPoint = start.add(dir.scale(proj));
                    double dist = entity.position().distanceTo(closestPoint);
                    if (dist < 1.5) dealDamage(living, damage);
                }
            }
        }
    }

    private void startDash(Player target) {
        this.entityData.set(IS_CHARGING, true);
        this.chargeTimer = 0;
        this.dashDirection = new Vec3(target.getX() - this.getX(), 0, target.getZ() - this.getZ()).normalize();
    }

    private void startSlam(Player target) {
        this.entityData.set(IS_SLAMMING, true);
        this.entityData.set(SLAM_PHASE, 1);
        this.slamTimer = 0;
        this.slamStartY = this.getY();
        if (target != null) {
            this.slamTargetX = target.getX();
            this.slamTargetZ = target.getZ();
        } else {
            this.slamTargetX = this.getX();
            this.slamTargetZ = this.getZ();
        }
    }

    private void startGrab() {
        this.isGrabbing = true;
        this.grabTimer = 0;
        this.grabbedPlayers.clear();
        List<Player> players = this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(50.0));
        Collections.shuffle(players);
        int count = 0;
        for (Player p : players) { if (count >= 5) break; this.grabbedPlayers.add(p); count++; }
        this.level().playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5F, 0.5F);
    }

    private void startLaser() {
        this.isLasing = true;
        this.laserTimer = 0;
        this.laserTarget = null;
        this.laserDir = Vec3.ZERO;
    }

    private void startMeteorShower() {
        this.isMeteorShower = true;
        this.meteorTimer = 0;
        this.slamStartY = this.getY();
        this.level().playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.0F, 0.5F);
    }

    private void startBlackHole() {
        this.isBlackHole = true;
        this.blackHoleTimer = 0;
        this.level().playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.0F, 0.3F);
    }

    private void startDeathGaze() {
        this.isDeathGaze = true;
        this.gazeTimer = 0;
        this.gazeTarget = null;
        this.gazeDir = Vec3.ZERO;
    }

    private void startGroundSlam() {
        this.isGroundSlam = true;
        this.groundSlamTimer = 0;
    }

    // ================= Controller：从 entityData 读状态，客户端渲染用 =================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 3, state -> {
            if (this.entityData.get(IS_TRANSFORMING)) {
                return state.setAndContinue(RawAnimation.begin().thenLoop("transform_charge"));
            }
            if (this.entityData.get(IS_EMERGING)) {
                return state.setAndContinue(RawAnimation.begin().thenLoop("transform_emerge"));
            }
            if (this.entityData.get(IS_CHARGING)) {
                return state.setAndContinue(RawAnimation.begin().thenLoop("charge_windup"));
            }
            if (this.entityData.get(IS_DASHING)) {
                return state.setAndContinue(RawAnimation.begin().thenLoop("charge_run"));
            }
            if (this.entityData.get(IS_DASH_RECOVERING)) {
                return state.setAndContinue(RawAnimation.begin().thenLoop("charge_recover"));
            }
            if (this.entityData.get(IS_SLAMMING)) {
                int phase = this.entityData.get(SLAM_PHASE);
                if (phase == 1) return state.setAndContinue(RawAnimation.begin().thenLoop("slam_windup"));
                if (phase == 2) return state.setAndContinue(RawAnimation.begin().thenLoop("slam_air"));
                if (phase == 3) return state.setAndContinue(RawAnimation.begin().thenLoop("slam_land"));
            }
            if (this.entityData.get(IS_ATTACKING)) {
                if (this.entityData.get(IS_PHASE_TWO)) {
                    return state.setAndContinue(RawAnimation.begin().thenLoop("combo"));
                } else {
                    return state.setAndContinue(RawAnimation.begin().thenLoop("bow_attack"));
                }
            }
            if (state.isMoving()) {
                return state.setAndContinue(RawAnimation.begin().thenLoop("walk"));
            } else {
                return state.setAndContinue(RawAnimation.begin().thenLoop("idle"));
            }
        }));
    }
    // ====================================================================================

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
        tag.putBoolean("IsRageMode", this.isRageMode);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(IS_PHASE_TWO, tag.getBoolean("IsPhaseTwo"));
        this.skillCooldown = tag.getInt("SkillCooldown");
        this.isRageMode = tag.getBoolean("IsRageMode");
    }
}
