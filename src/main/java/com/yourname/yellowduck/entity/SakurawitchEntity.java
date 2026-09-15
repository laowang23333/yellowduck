package com.yourname.yellowduck.entity;

import com.yourname.yellowduck.particle.ModParticles;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 魔女小樱 Boss
 *
 * 技能：
 * 1. 普通攻击：attack_01 ~ attack_04 循环
 * 2. 火焰喷射：attack_05（8.25 秒动画），点名 + 蓄力 + 扇形喷火
 * 3. 火焰元素：二阶段开始，每 2 秒叠 1 层，10 层爆炸
 * 4. 火焰喷发：三阶段点名地面，5 秒后爆发
 * 5. 死亡：attack_death
 */
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

    private static final int IDLE = 0;
    private static final int SPRAY_CHARGE = 1;
    private static final int SPRAY_CAST = 2;
    private static final int ERUPTION = 3;

    private static final int ATTACK_LENGTH = 27;              // 1.35 秒
    private static final int SPRAY_TOTAL_TICKS = 165;         // 8.25 秒 = attack_05
    private static final int SPRAY_CHARGE_TICKS = 145;
    private static final int SPRAY_CAST_TICKS = 20;
    private static final int SPRAY_CD = 600;                 // 30 秒
    private static final int FIRE_CHARGE_INTERVAL = 40;      // 2 秒
    private static final int ERUPTION_CD = 160;              // 8 秒
    private static final int ERUPTION_DELAY = 100;           // 5 秒

    private static final float SPRAY_DAMAGE = 30.0F;
    private static final float FIRE_EXPLOSION_DAMAGE = 40.0F;
    private static final float EMBER_DAMAGE = 40.0F;
    private static final float ERUPTION_DAMAGE = 60.0F;

    private int sprayCD = 200;
    private int sprayTimer;
    private Player sprayTarget;

    private int fireChargeTimer;

    private int eruptionCD = ERUPTION_CD;
    private int eruptionTimer;
    private BlockPos eruptionPos;
    private Player eruptionTarget;

    private int deathTimer;
    private int nextMeleeAnimation = 1;

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
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(IS_WALKING, false);
        entityData.define(ATTACK_INDEX, 0);
        entityData.define(ATTACK_TIMER, 0);
        entityData.define(IS_DYING, false);
        entityData.define(PHASE, 1);
        entityData.define(FIRE_MARK_STACKS, 0);
        entityData.define(SKILL_STATE, IDLE);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (entityData.get(IS_DYING)
                || entityData.get(SKILL_STATE) != IDLE
                || entityData.get(ATTACK_TIMER) > 0) {
            return false;
        }

        boolean hit = super.doHurtTarget(target);
        if (!hit) return false;

        entityData.set(ATTACK_INDEX, nextMeleeAnimation);
        entityData.set(ATTACK_TIMER, ATTACK_LENGTH);
        nextMeleeAnimation++;
        if (nextMeleeAnimation > 4) nextMeleeAnimation = 1;

        if (!level().isClientSide && target instanceof Player p) {
            addMagicVulnerability(p);
        }

        level().playSound(
                null, blockPosition(),
                SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.HOSTILE,
                1.2F, 0.8F
        );
        return true;
    }

    private void addMagicVulnerability(Player p) {
        MobEffectInstance old = p.getEffect(ModEffects.MAGIC_VULNERABILITY.get());
        int amp = old == null ? 0 : Math.min(9, old.getAmplifier() + 1);
        p.addEffect(new MobEffectInstance(
                ModEffects.MAGIC_VULNERABILITY.get(),
                200, amp, false, true, true
        ));
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) return;

        if (entityData.get(IS_DYING)) {
            tickDeath();
            return;
        }

        updateTarget();
        updatePhase();
        updateBossBar();
        tickAttackTimer();
        updateWalking();

        int state = entityData.get(SKILL_STATE);
        if (state == IDLE) {
            tickSpray();
            tickFireCharge();
            tickEruption();
        } else if (state == SPRAY_CHARGE) {
            tickSprayCharge();
        } else if (state == SPRAY_CAST) {
            tickSprayCast();
        } else if (state == ERUPTION) {
            tickEruptionCharge();
        }
    }

    private void updateTarget() {
        if (tickCount % 20 != 0) return;

        Entity t = getTarget();
        if (t != null && t.isAlive() && distanceToSqr(t) <= 1600) return;

        Player p = level().getNearestPlayer(this, 35);
        if (valid(p)) setTarget(p);
    }

    private void updateWalking() {
        double dx = getX() - xo;
        double dz = getZ() - zo;
        boolean moving = dx * dx + dz * dz > 1.0E-5
                && entityData.get(SKILL_STATE) == IDLE
                && entityData.get(ATTACK_TIMER) <= 0;

        entityData.set(IS_WALKING, moving);
    }

    private void tickAttackTimer() {
        int t = entityData.get(ATTACK_TIMER);
        if (t > 0) {
            t--;
            entityData.set(ATTACK_TIMER, t);
            if (t == 0) entityData.set(ATTACK_INDEX, 0);
        }
    }

    private void updateBossBar() {
        float hp = Math.max(0, Math.min(1, getHealth() / getMaxHealth()));
        bossEvent.setProgress(hp);

        int p = entityData.get(PHASE);
        bossEvent.setColor(
                p == 1 ? BossEvent.BossBarColor.GREEN :
                p == 2 ? BossEvent.BossBarColor.YELLOW :
                         BossEvent.BossBarColor.RED
        );
    }

    private void updatePhase() {
        float r = getHealth() / getMaxHealth();
        int next = r > .8F ? 1 : r > .5F ? 2 : 3;
        int old = entityData.get(PHASE);

        if (next == old) return;

        entityData.set(PHASE, next);
        cancelSkill();

        level().playSound(
                null, blockPosition(),
                next == 2 ? SoundEvents.BLAZE_SHOOT : SoundEvents.WITHER_SPAWN,
                SoundSource.HOSTILE,
                2F, next == 2 ? .6F : 1.2F
        );

        announce("§" + (next == 2 ? "6" : "c") + "⚠ 小樱进入第" + next + "阶段！");

        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(
                    ModParticles.SAKURA_MAGIC.get(),
                    getX(), getY() + 1, getZ(),
                    35, 1.5, 1.0, 1.5, .04
            );
            sl.sendParticles(
                    ModParticles.SAKURA_PETAL.get(),
                    getX(), getY() + 1, getZ(),
                    20, 1.8, 1.2, 1.8, .08
            );
        }
    }

    // ================= 火焰喷射 / attack_05 =================

    private void tickSpray() {
        if (sprayCD > 0) sprayCD--;
        if (sprayCD > 0) return;

        List<Player> ps = players(30);
        if (ps.isEmpty()) {
            sprayCD = 40;
            return;
        }

        sprayTarget = ps.get(random.nextInt(ps.size()));

        entityData.set(SKILL_STATE, SPRAY_CHARGE);
        entityData.set(ATTACK_INDEX, 5);
        entityData.set(ATTACK_TIMER, SPRAY_TOTAL_TICKS);

        sprayTimer = 0;
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);

        tell(sprayTarget, "§c⚠ 你被小樱点名！火焰喷射即将到来！");
        level().playSound(
                null, blockPosition(),
                SoundEvents.BLAZE_SHOOT,
                SoundSource.HOSTILE,
                2.0F, .55F
        );
    }

    private void tickSprayCharge() {
        sprayTimer++;

        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);

        if (valid(sprayTarget)) {
            lookAt(sprayTarget.position().add(0, 1, 0));
        }

        if (level() instanceof ServerLevel sl) {
            // 小樱本体蓄力
            for (int i = 0; i < 5; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = .6 + random.nextDouble() * 1.1;
                sl.sendParticles(
                        ModParticles.SAKURA_MAGIC.get(),
                        getX() + Math.cos(a) * r,
                        getY() + .3 + random.nextDouble() * 1.5,
                        getZ() + Math.sin(a) * r,
                        1, 0, .03, 0, 0
                );
            }

            // 樱花粒子围绕蓄力旋转
            if (sprayTimer % 3 == 0) {
                double a = sprayTimer * .22;
                sl.sendParticles(
                        ModParticles.SAKURA_PETAL.get(),
                        getX() + Math.cos(a) * 1.4,
                        getY() + 1.0 + Math.sin(a * .7) * .35,
                        getZ() + Math.sin(a) * 1.4,
                        1, 0, .01, 0, 0
                );
            }

            // 被点名玩家脚下持续警告
            if (valid(sprayTarget) && sprayTimer % 4 == 0) {
                sl.sendParticles(
                        ModParticles.SAKURA_WARNING.get(),
                        sprayTarget.getX(),
                        sprayTarget.getY() + .08,
                        sprayTarget.getZ(),
                        2, .25, .02, .25, 0
                );
            }
        }

        if (sprayTimer >= SPRAY_CHARGE_TICKS) {
            entityData.set(SKILL_STATE, SPRAY_CAST);
            sprayTimer = SPRAY_CHARGE_TICKS;
            castSpray();
        }
    }

    private void tickSprayCast() {
        sprayTimer++;

        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);

        if (valid(sprayTarget)) {
            lookAt(sprayTarget.position().add(0, 1, 0));
        }

        if (level() instanceof ServerLevel sl) {
            Vec3 look = getLookAngle().normalize();
            Vec3 start = position().add(0, 1.3, 0);

            // 连续火焰喷射束
            for (double d = 0; d < 15; d += .65) {
                Vec3 p = start.add(look.scale(d));
                sl.sendParticles(
                        ModParticles.SAKURA_FLAME.get(),
                        p.x, p.y, p.z,
                        1, look.x * .04, .02, look.z * .04, 0
                );
            }

            if (sprayTimer % 2 == 0) {
                sl.sendParticles(
                        ModParticles.SAKURA_FLAME.get(),
                        start.x, start.y, start.z,
                        4, .18, .12, .18, .08
                );
            }
        }

        if (sprayTimer >= SPRAY_TOTAL_TICKS) {
            cancelSkill();
            sprayTarget = null;
            sprayCD = SPRAY_CD;
        }
    }

    private void castSpray() {
        Vec3 look = getLookAngle().normalize();
        Vec3 start = position().add(0, 1.3, 0);

        List<Player> hit = new ArrayList<>();

        if (level() instanceof ServerLevel sl) {
            for (Player p : sl.getEntitiesOfClass(
                    Player.class,
                    getBoundingBox().inflate(15)
            )) {
                if (!valid(p)) continue;

                Vec3 d = p.position().add(0, 1, 0).subtract(start);
                if (d.lengthSqr() < .01 || d.normalize().dot(look) >= .5) {
                    hit.add(p);
                }
            }

            float damage = hit.size() >= 2
                    ? SPRAY_DAMAGE * .5F
                    : SPRAY_DAMAGE;

            for (Player p : hit) {
                magicDamage(p, damage);
            }

            if (valid(sprayTarget) && !hit.contains(sprayTarget)) {
                tell(sprayTarget, "§a✔ 你躲开了火焰喷射！");
            }

            sl.sendParticles(
                    ModParticles.SAKURA_EXPLOSION.get(),
                    start.x, start.y, start.z,
                    16, look.x * 2.5, .6, look.z * 2.5, .12
            );
        }

        level().playSound(
                null, blockPosition(),
                SoundEvents.BLAZE_SHOOT,
                SoundSource.HOSTILE,
                2.5F, .75F
        );
    }

    // ================= 二阶段火焰元素 =================

    private void tickFireCharge() {
        if (entityData.get(PHASE) < 2) return;

        fireChargeTimer++;

        if (fireChargeTimer % 10 == 0 && level() instanceof ServerLevel sl) {
            int stacks = entityData.get(FIRE_MARK_STACKS);
            double r = 1.2 + stacks * .08;

            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI * 2 / 8.0;
                sl.sendParticles(
                        ModParticles.SAKURA_FLAME.get(),
                        getX() + Math.cos(a) * r,
                        getY() + .15,
                        getZ() + Math.sin(a) * r,
                        1, 0, .02, 0, 0
                );
            }
        }

        if (fireChargeTimer >= FIRE_CHARGE_INTERVAL) {
            fireChargeTimer = 0;

            int stacks = entityData.get(FIRE_MARK_STACKS) + 1;
            entityData.set(FIRE_MARK_STACKS, stacks);

            level().playSound(
                    null, blockPosition(),
                    SoundEvents.FIRECHARGE_USE,
                    SoundSource.HOSTILE,
                    .8F, 1F + stacks * .03F
            );

            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(
                        ModParticles.SAKURA_MAGIC.get(),
                        getX(), getY() + 1, getZ(),
                        8 + stacks, 1.0, 1.0, 1.0, .06
                );
            }

            if (stacks >= 10) {
                entityData.set(FIRE_MARK_STACKS, 0);
                explodeFireCharge();
            }
        }
    }

    private void explodeFireCharge() {
        if (!(level() instanceof ServerLevel sl)) return;

        announce("§4☠ 火焰爆炸！10层火焰元素已释放！");

        level().playSound(
                null, blockPosition(),
                SoundEvents.GENERIC_EXPLODE,
                SoundSource.HOSTILE,
                3F, .65F
        );

        sl.sendParticles(
                ModParticles.SAKURA_EXPLOSION.get(),
                getX(), getY() + 1, getZ(),
                30, 2, 1.2, 2, .15
        );

        List<Player> ps = players(30);
        for (Player p : ps) {
            magicDamage(p, FIRE_EXPLOSION_DAMAGE);

            int nearby = 0;
            for (Player o : ps) {
                if (o != p && o.distanceToSqr(p) <= 25) {
                    nearby++;
                }
            }

            if (nearby > 0) {
                magicDamage(p, EMBER_DAMAGE * nearby);
                tell(p, "§c⚠ 你与队友距离过近，受到额外余烬伤害！");
            }
        }
    }

    // ================= 三阶段火焰喷发 =================

    private void tickEruption() {
        if (entityData.get(PHASE) < 3) return;

        if (eruptionCD > 0) eruptionCD--;
        if (eruptionCD > 0) return;

        List<Player> ps = players(30);
        if (ps.isEmpty()) {
            eruptionCD = 40;
            return;
        }

        eruptionTarget = ps.get(random.nextInt(ps.size()));
        eruptionPos = eruptionTarget.blockPosition();
        eruptionTimer = ERUPTION_DELAY;

        entityData.set(SKILL_STATE, ERUPTION);
        entityData.set(ATTACK_INDEX, 4);
        entityData.set(ATTACK_TIMER, ERUPTION_DELAY);

        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);

        tell(
                eruptionTarget,
                "§c⚠ 你脚下出现火焰！5秒后爆发，快离开！"
        );

        level().playSound(
                null, eruptionPos,
                SoundEvents.FIRECHARGE_USE,
                SoundSource.HOSTILE,
                1.5F, .8F
        );
    }

    private void tickEruptionCharge() {
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);

        if (eruptionPos == null) {
            cancelSkill();
            return;
        }

        if (level() instanceof ServerLevel sl) {
            double radius = 2.5 + (100 - eruptionTimer) * .006;

            // 樱花火焰预警圈
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI * 2 / 12.0;
                sl.sendParticles(
                        ModParticles.SAKURA_ERUPTION.get(),
                        eruptionPos.getX() + .5 + Math.cos(a) * radius,
                        eruptionPos.getY() + .08,
                        eruptionPos.getZ() + .5 + Math.sin(a) * radius,
                        1, 0, 0, 0, 0
                );
            }

            // 越接近爆炸，中心越亮
            if (eruptionTimer <= 40 && eruptionTimer % 2 == 0) {
                sl.sendParticles(
                        ModParticles.SAKURA_MAGIC.get(),
                        eruptionPos.getX() + .5,
                        eruptionPos.getY() + .25,
                        eruptionPos.getZ() + .5,
                        5, .45, .15, .45, .03
                );
            }

            if (eruptionTimer <= 20 && eruptionTimer % 2 == 0) {
                sl.sendParticles(
                        ModParticles.SAKURA_WARNING.get(),
                        eruptionPos.getX() + .5,
                        eruptionPos.getY() + .12,
                        eruptionPos.getZ() + .5,
                        2, .8, .02, .8, .02
                );
            }
        }

        eruptionTimer--;

        if (eruptionTimer <= 0) {
            explodeEruption();
            eruptionPos = null;
            eruptionTarget = null;
            eruptionCD = ERUPTION_CD;
            cancelSkill();
        }
    }

    private void explodeEruption() {
        if (!(level() instanceof ServerLevel sl) || eruptionPos == null) return;

        BlockPos pos = eruptionPos;

        level().playSound(
                null, pos,
                SoundEvents.GENERIC_EXPLODE,
                SoundSource.HOSTILE,
                2.2F, .9F
        );

        sl.sendParticles(
                ModParticles.SAKURA_EXPLOSION.get(),
                pos.getX() + .5,
                pos.getY() + .5,
                pos.getZ() + .5,
                40, 1.8, 1.0, 1.8, .18
        );

        // 爆发瞬间切到 attack_06
        entityData.set(ATTACK_INDEX, 6);
        entityData.set(ATTACK_TIMER, 24);

        for (Player p : sl.getEntitiesOfClass(
                Player.class,
                new AABB(pos).inflate(3)
        )) {
            if (!valid(p)) continue;

            double dx = p.getX() - (pos.getX() + .5);
            double dz = p.getZ() - (pos.getZ() + .5);

            if (dx * dx + dz * dz <= 9) {
                magicDamage(p, ERUPTION_DAMAGE);
            }
        }
    }

    private void cancelSkill() {
        entityData.set(SKILL_STATE, IDLE);
        entityData.set(ATTACK_INDEX, 0);
        entityData.set(ATTACK_TIMER, 0);
        sprayTimer = 0;
        eruptionTimer = 0;
    }

    private void magicDamage(Player p, float base) {
        if (!valid(p)) return;

        MobEffectInstance e =
                p.getEffect(ModEffects.MAGIC_VULNERABILITY.get());

        int stacks = e == null ? 0 : Math.min(10, e.getAmplifier() + 1);
        float damage = base * (1F + .05F * stacks);

        p.hurt(
                damageSources().indirectMagic(this, this),
                damage
        );
    }

    private List<Player> players(double radius) {
        List<Player> ps = level().getEntitiesOfClass(
                Player.class,
                getBoundingBox().inflate(radius)
        );
        ps.removeIf(p -> !valid(p));
        return ps;
    }

    private boolean valid(Player p) {
        return p != null
                && p.isAlive()
                && !p.isRemoved()
                && !p.isCreative()
                && !p.isSpectator();
    }

    private void tell(Player p, String s) {
        if (p instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.literal(s), true);
        }
    }

    private void announce(String s) {
        if (!(level() instanceof ServerLevel sl)) return;

        for (ServerPlayer p : sl.getEntitiesOfClass(
                ServerPlayer.class,
                getBoundingBox().inflate(35)
        )) {
            if (p.isAlive() && !p.isSpectator()) {
                p.displayClientMessage(Component.literal(s), true);
            }
        }
    }

    private void lookAt(Vec3 pos) {
        Vec3 d = pos.subtract(position().add(0, getEyeHeight(), 0));

        double h = Math.sqrt(d.x * d.x + d.z * d.z);

        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, h));

        setYRot(yaw);
        setXRot(pitch);
        yHeadRot = yaw;
        yBodyRot = yaw;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return entityData.get(IS_DYING)
                ? false
                : super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (level().isClientSide) {
            super.die(source);
            return;
        }

        if (entityData.get(IS_DYING)) return;

        entityData.set(IS_DYING, true);
        entityData.set(SKILL_STATE, IDLE);
        entityData.set(ATTACK_INDEX, 0);
        entityData.set(ATTACK_TIMER, 0);

        setInvulnerable(true);
        setHealth(0);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);

        deathTimer = 0;
        bossEvent.removeAllPlayers();

        level().playSound(
                null, blockPosition(),
                SoundEvents.WITHER_DEATH,
                SoundSource.HOSTILE,
                2F, 1F
        );

        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(
                    ModParticles.SAKURA_EXPLOSION.get(),
                    getX(), getY() + 1, getZ(),
                    30, 1.0, 1.2, 1.0, .08
            );
        }
    }

    private void tickDeath() {
        setInvulnerable(true);
        setDeltaMovement(Vec3.ZERO);
        getNavigation().stop();
        deathTimer++;

        if (level() instanceof ServerLevel sl && deathTimer % 3 == 0) {
            sl.sendParticles(
                    ModParticles.SAKURA_PETAL.get(),
                    getX(), getY() + 1, getZ(),
                    5, .8, .8, .8, .03
            );
            sl.sendParticles(
                    ModParticles.SAKURA_MAGIC.get(),
                    getX(), getY() + 1, getZ(),
                    3, .5, .7, .5, .02
            );
        }

        if (deathTimer >= 35) {
            setInvulnerable(false);
            super.die(damageSources().generic());
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        tag.putInt("FireSprayCD", sprayCD);
        tag.putInt("FireChargeTimer", fireChargeTimer);
        tag.putInt("FireEruptionCD", eruptionCD);
        tag.putInt("FireMarkStacks",
                entityData.get(FIRE_MARK_STACKS));
        tag.putInt("Phase", entityData.get(PHASE));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        sprayCD = tag.getInt("FireSprayCD");
        fireChargeTimer = tag.getInt("FireChargeTimer");
        eruptionCD = tag.getInt("FireEruptionCD");

        entityData.set(
                FIRE_MARK_STACKS,
                Math.max(0, Math.min(10, tag.getInt("FireMarkStacks")))
        );
        entityData.set(
                PHASE,
                Math.max(1, Math.min(3, tag.getInt("Phase")))
        );
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
