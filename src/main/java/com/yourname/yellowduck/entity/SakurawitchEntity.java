package com.yourname.yellowduck.entity;

import com.yourname.yellowduck.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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

import java.util.ArrayList;
import java.util.List;

public class SakurawitchEntity extends PathfinderMob {

    public static final EntityDataAccessor<Boolean> IS_WALKING =
            SynchedEntityData.defineId(SakurawitchEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> ATTACK_INDEX =
            SynchedEntityData.defineId(SakurawitchEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> ATTACK_TIMER =
            SynchedEntityData.defineId(SakurawitchEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> IS_DYING =
            SynchedEntityData.defineId(SakurawitchEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> PHASE =
            SynchedEntityData.defineId(SakurawitchEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> FIRE_MARK_STACKS =
            SynchedEntityData.defineId(SakurawitchEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> SKILL_STATE =
            SynchedEntityData.defineId(SakurawitchEntity.class, EntityDataSerializers.INT);

    private static final int STATE_IDLE = 0;
    private static final int STATE_CHARGE_SPRAY = 1;
    private static final int STATE_CAST_SPRAY = 2;

    private int skillCooldown = 0;
    private int deathTimer = 0;
    private int stateTimer = 0;

    private int fireSprayCD = 200;
    private Player sprayTarget = null;

    private int fireMarkTimer = 0;

    private int fireEruptionCD = 160;
    private final List<Eruption> eruptions = new ArrayList<>();

    private static class Eruption {
        BlockPos pos;
        int ticksLeft;
        Eruption(BlockPos p, int t) { pos = p; ticksLeft = t; }
    }

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.literal("小樱"),
            BossEvent.BossBarColor.PINK,
            BossEvent.BossBarOverlay.PROGRESS
    );

    public SakurawitchEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    public Component getName() { return Component.literal("小樱"); }

    @Override
    public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player); bossEvent.addPlayer(player); }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); bossEvent.removePlayer(player); }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // mustSee = false：35 格内不要求视线就能锁定
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_WALKING, false);
        this.entityData.define(ATTACK_INDEX, 0);
        this.entityData.define(ATTACK_TIMER, 0);
        this.entityData.define(IS_DYING, false);
        this.entityData.define(PHASE, 1);
        this.entityData.define(FIRE_MARK_STACKS, 0);
        this.entityData.define(SKILL_STATE, STATE_IDLE);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (this.entityData.get(SKILL_STATE) != STATE_IDLE) return false;
        boolean hit = super.doHurtTarget(target);
        if (hit && !this.level().isClientSide && target instanceof Player p) {
            MobEffectInstance cur = p.getEffect(ModEffects.MAGIC_VULNERABILITY.get());
            int lvl = (cur == null ? 0 : cur.getAmplifier() + 1);
            if (lvl < 10) {
                p.addEffect(new MobEffectInstance(ModEffects.MAGIC_VULNERABILITY.get(),
                        200, lvl, false, true));
            } else {
                p.addEffect(new MobEffectInstance(ModEffects.MAGIC_VULNERABILITY.get(),
                        200, 9, false, true));
            }
            this.entityData.set(ATTACK_INDEX, 1);
            this.entityData.set(ATTACK_TIMER, 27);
            this.level().playSound(null, this.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.HOSTILE, 1.2F, 0.8F);
        }
        return hit;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            if (this.entityData.get(SKILL_STATE) == STATE_CAST_SPRAY) {
                Vec3 look = this.getLookAngle();
                Vec3 start = this.position().add(0, 1.5, 0);
                for (double d = 0; d < 12; d += 0.4) {
                    Vec3 p = start.add(look.scale(d));
                    this.level().addParticle(ParticleTypes.FLAME, p.x, p.y, p.z, 0, 0, 0);
                }
            }
            return;
        }

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

        // ===== 临时诊断日志：每 40 tick 打印一次 =====
        if (this.tickCount % 40 == 0) {
            Entity t = this.getTarget();
            System.out.println("[小樱 debug] tick=" + this.tickCount
                    + " target=" + (t == null ? "null" : t.getName().getString())
                    + " pos=" + this.blockPosition());
        }
        // ==============================================

        if (skillCooldown > 0) skillCooldown--;
        attackTimerDecay();
        updateWalkingState();
        updatePhase();
        updateBossBar();

        if (this.isDeadOrDying()) return;

        updateFireSpray();
        updateFireMark();
        updateFireEruption();
    }

    private void attackTimerDecay() {
        int at = this.entityData.get(ATTACK_TIMER);
        if (at > 0) {
            this.entityData.set(ATTACK_TIMER, at - 1);
            if (at - 1 == 0) this.entityData.set(ATTACK_INDEX, 0);
        }
    }

    private void updateWalkingState() {
        double dx = this.getX() - this.xo;
        double dz = this.getZ() - this.zo;
        this.entityData.set(IS_WALKING, (dx * dx + dz * dz) > 1.0E-5);
    }

    private void updateBossBar() {
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
    }

    private void updatePhase() {
        float ratio = this.getHealth() / this.getMaxHealth();
        int newPhase = ratio > 0.8F ? 1 : (ratio > 0.5F ? 2 : 3);
        if (newPhase != this.entityData.get(PHASE)) {
            this.entityData.set(PHASE, newPhase);
            if (newPhase == 2) {
                this.level().playSound(null, this.blockPosition(), SoundEvents.BLAZE_SHOOT,
                        SoundSource.HOSTILE, 2.0F, 0.6F);
            } else if (newPhase == 3) {
                this.level().playSound(null, this.blockPosition(), SoundEvents.WITHER_SPAWN,
                        SoundSource.HOSTILE, 1.5F, 1.2F);
            }
        }
    }

    private void updateFireSpray() {
        int state = this.entityData.get(SKILL_STATE);

        if (state == STATE_IDLE) {
            if (fireSprayCD > 0) fireSprayCD--;
            if (fireSprayCD <= 0 && this.getTarget() != null) {
                List<Player> ps = this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(30));
                ps.removeIf(p -> p.isCreative() || p.isSpectator() || !p.isAlive());
                if (!ps.isEmpty()) {
                    this.sprayTarget = ps.get(this.random.nextInt(ps.size()));
                    this.entityData.set(SKILL_STATE, STATE_CHARGE_SPRAY);
                    this.stateTimer = 0;
                    this.getNavigation().stop();
                    this.setDeltaMovement(Vec3.ZERO);
                    if (this.sprayTarget instanceof ServerPlayer sp) {
                        sp.displayClientMessage(Component.literal("§c⚠ 你被小樱点名，火焰喷射即将到来！"), true);
                    }
                    this.level().playSound(null, this.blockPosition(), SoundEvents.BLAZE_SHOOT,
                            SoundSource.HOSTILE, 2.0F, 0.5F);
                } else {
                    fireSprayCD = 100;
                }
            }
        } else if (state == STATE_CHARGE_SPRAY) {
            this.stateTimer++;
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);

            if (this.sprayTarget != null && this.sprayTarget.isAlive()) {
                Vec3 dir = this.sprayTarget.position().subtract(this.position());
                float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
                this.setYRot(yaw);
                this.yHeadRot = yaw;
                this.yBodyRot = yaw;

                if (this.level() instanceof ServerLevel sl) {
                    Vec3 tp = this.sprayTarget.position();
                    for (int i = 0; i < 6; i++) {
                        double ang = this.random.nextDouble() * Math.PI * 2;
                        double r = this.random.nextDouble() * 0.8;
                        sl.sendParticles(ParticleTypes.FLAME,
                                tp.x + Math.cos(ang) * r, tp.y + 0.1, tp.z + Math.sin(ang) * r,
                                1, 0, 0, 0, 0);
                    }
                }
            }

            if (this.stateTimer >= 160) {
                this.entityData.set(SKILL_STATE, STATE_CAST_SPRAY);
                this.stateTimer = 0;
                castFireSpray();
            }
        } else if (state == STATE_CAST_SPRAY) {
            this.stateTimer++;
            if (this.stateTimer >= 20) {
                this.entityData.set(SKILL_STATE, STATE_IDLE);
                this.stateTimer = 0;
                this.sprayTarget = null;
                this.fireSprayCD = 600;
            }
        }
    }

    private void castFireSpray() {
        if (!(this.level() instanceof ServerLevel sl)) return;
        Vec3 look = this.getLookAngle();
        Vec3 start = this.position().add(0, 1.5, 0);

        List<Player> hit = new ArrayList<>();
        for (Player p : sl.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(15))) {
            if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
            Vec3 toP = p.position().add(0, 1, 0).subtract(start).normalize();
            double dot = toP.dot(look);
            if (dot > 0.5) hit.add(p);
        }

        float baseDmg = 30.0F;
        float dmg = hit.size() >= 2 ? baseDmg * 0.5F : baseDmg;

        for (Player p : hit) {
            applyMagicDamage(p, dmg);
        }

        if (sprayTarget instanceof ServerPlayer sp && !hit.contains(sp)) {
            sp.displayClientMessage(Component.literal("§a✔ 你躲开了火焰喷射"), true);
        }

        this.level().playSound(null, this.blockPosition(), SoundEvents.BLAZE_SHOOT,
                SoundSource.HOSTILE, 2.0F, 0.8F);
    }

    private void updateFireMark() {
        int phase = this.entityData.get(PHASE);
        if (phase < 2) return;

        fireMarkTimer++;
        if (fireMarkTimer >= 40) {
            fireMarkTimer = 0;
            int stacks = this.entityData.get(FIRE_MARK_STACKS) + 1;
            this.entityData.set(FIRE_MARK_STACKS, stacks);
            if (this.level() instanceof ServerLevel sl) {
                for (int i = 0; i < 20; i++) {
                    double ang = i * Math.PI * 2 / 20;
                    double r = 1.5;
                    sl.sendParticles(ParticleTypes.FLAME,
                            this.getX() + Math.cos(ang) * r, this.getY() + 0.2, this.getZ() + Math.sin(ang) * r,
                            1, 0, 0, 0, 0);
                }
            }
            this.level().playSound(null, this.blockPosition(), SoundEvents.FIRECHARGE_USE,
                    SoundSource.HOSTILE, 1.0F, 1.2F);

            if (stacks >= 10) {
                this.entityData.set(FIRE_MARK_STACKS, 0);
                explodeFireMark();
            }
        }
    }

    private void explodeFireMark() {
        if (!(this.level() instanceof ServerLevel sl)) return;

        for (ServerPlayer sp : sl.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(30))) {
            sp.displayClientMessage(Component.literal("§4☠ 火焰爆炸！"), true);
        }
        this.level().playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE,
                SoundSource.HOSTILE, 3.0F, 0.6F);

        List<Player> all = sl.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(30));
        for (Player p : all) {
            if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;

            long nearby = all.stream()
                    .filter(o -> o != p && o.isAlive()
                            && !o.isCreative() && !o.isSpectator()
                            && o.distanceToSqr(p) < 25.0)
                    .count();

            float dmg = 40.0F * (1.0F + nearby);
            applyMagicDamage(p, dmg);
        }
    }

    private void updateFireEruption() {
        int phase = this.entityData.get(PHASE);
        if (phase < 3) return;

        fireEruptionCD--;
        if (fireEruptionCD <= 0) {
            fireEruptionCD = 160;
            List<Player> ps = this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(30));
            ps.removeIf(p -> p.isCreative() || p.isSpectator() || !p.isAlive());
            if (!ps.isEmpty()) {
                Player t = ps.get(this.random.nextInt(ps.size()));
                BlockPos bp = t.blockPosition();
                this.eruptions.add(new Eruption(bp, 100));
                if (t instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.literal("§c⚠ 你脚下出现火焰，快离开！"), true);
                }
                this.level().playSound(null, bp, SoundEvents.FIRECHARGE_USE,
                        SoundSource.HOSTILE, 1.5F, 0.8F);
            }
        }

        if (!(this.level() instanceof ServerLevel sl)) return;
        for (int i = eruptions.size() - 1; i >= 0; i--) {
            Eruption e = eruptions.get(i);

            for (int j = 0; j < 10; j++) {
                double ang = j * Math.PI * 2 / 10;
                double r = 2.0;
                sl.sendParticles(ParticleTypes.FLAME,
                        e.pos.getX() + 0.5 + Math.cos(ang) * r,
                        e.pos.getY() + 0.1,
                        e.pos.getZ() + 0.5 + Math.sin(ang) * r,
                        1, 0, 0, 0, 0);
            }

            e.ticksLeft--;
            if (e.ticksLeft <= 0) {
                this.level().playSound(null, e.pos, SoundEvents.GENERIC_EXPLODE,
                        SoundSource.HOSTILE, 2.0F, 1.0F);
                sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                        e.pos.getX() + 0.5, e.pos.getY() + 0.5, e.pos.getZ() + 0.5,
                        3, 1.5, 0.5, 1.5, 0.1);

                for (Player p : sl.getEntitiesOfClass(Player.class,
                        new net.minecraft.world.phys.AABB(e.pos).inflate(3.0))) {
                    if (p.isCreative() || p.isSpectator()) continue;
                    if (p.position().distanceToSqr(e.pos.getX() + 0.5, p.getY(), e.pos.getZ() + 0.5) < 9.0) {
                        applyMagicDamage(p, 60.0F);
                    }
                }
                eruptions.remove(i);
            }
        }
    }

    private void applyMagicDamage(Player p, float baseDmg) {
        MobEffectInstance eff = p.getEffect(ModEffects.MAGIC_VULNERABILITY.get());
        int stacks = eff == null ? 0 : eff.getAmplifier() + 1;
        float mult = 1.0F + 0.05F * stacks;
        float finalDmg = baseDmg * mult;

        p.hurt(this.damageSources().indirectMagic(this, this), finalDmg);
        this.level().playSound(null, p.blockPosition(), SoundEvents.FIRECHARGE_USE,
                SoundSource.HOSTILE, 0.8F, 1.0F);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.entityData.get(IS_DYING)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide && !this.entityData.get(IS_DYING)) {
            this.entityData.set(IS_DYING, true);
            this.deathTimer = 0;
            this.setHealth(0.0F);
            this.setInvulnerable(true);
            return;
        }
        super.die(source);
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("SkillCooldown", skillCooldown);
        tag.putInt("FireSprayCD", fireSprayCD);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.skillCooldown = tag.getInt("SkillCooldown");
        this.fireSprayCD = tag.getInt("FireSprayCD");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 50000.0D)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.ATTACK_DAMAGE, 15.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 35.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }
}
