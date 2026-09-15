package com.yourname.yellowduck.entity;

import com.yourname.yellowduck.particle.ModParticles;
import com.yourname.yellowduck.registry.ModEffects;
import net.minecraft.core.BlockPos;
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
 * 魔女小樱
 *
 * 精英暮色钟楼·魔女小樱机制：
 *
 * 第一阶段 100% ~ 80%
 * 1. 普通魔法攻击：攻击最近玩家，并叠加魔法易伤。
 * 2. 火焰喷射：约每 30 秒一次，随机点名玩家，
 *    蓄力 8 秒后朝目标进行扇形火焰攻击。
 *    被攻击玩家可以通过抱团降低喷射伤害。
 *
 * 第二阶段 80% ~ 50%
 * 继承第一阶段全部技能。
 * 3. 火焰蓄能：每 2 秒增加 1 层火焰元素。
 * 4. 10 层后释放火焰爆炸。
 *    玩家需要分散，避免 5 格内队友带来的额外余烬伤害。
 *
 * 第三阶段 50% ~ 0%
 * 继承前两个阶段。
 * 5. 火焰喷发：每 8 秒随机点名一名玩家，
 *    在玩家脚下生成火焰区域，5 秒后爆炸。
 */
public class SakurawitchEntity extends PathfinderMob {

    // =========================================================
    // 同步数据
    // =========================================================

    public static final EntityDataAccessor<Boolean> IS_WALKING =
            SynchedEntityData.defineId(
                    SakurawitchEntity.class,
                    EntityDataSerializers.BOOLEAN
            );

    public static final EntityDataAccessor<Integer> ATTACK_INDEX =
            SynchedEntityData.defineId(
                    SakurawitchEntity.class,
                    EntityDataSerializers.INT
            );

    public static final EntityDataAccessor<Integer> ATTACK_TIMER =
            SynchedEntityData.defineId(
                    SakurawitchEntity.class,
                    EntityDataSerializers.INT
            );

    public static final EntityDataAccessor<Boolean> IS_DYING =
            SynchedEntityData.defineId(
                    SakurawitchEntity.class,
                    EntityDataSerializers.BOOLEAN
            );

    public static final EntityDataAccessor<Integer> PHASE =
            SynchedEntityData.defineId(
                    SakurawitchEntity.class,
                    EntityDataSerializers.INT
            );

    public static final EntityDataAccessor<Integer> FIRE_MARK_STACKS =
            SynchedEntityData.defineId(
                    SakurawitchEntity.class,
                    EntityDataSerializers.INT
            );

    public static final EntityDataAccessor<Integer> SKILL_STATE =
            SynchedEntityData.defineId(
                    SakurawitchEntity.class,
                    EntityDataSerializers.INT
            );

    // =========================================================
    // 技能状态
    // =========================================================

    private static final int IDLE = 0;

    private static final int SPRAY_CHARGE = 1;

    private static final int SPRAY_CAST = 2;

    private static final int ERUPTION = 3;

    // =========================================================
    // 时间参数
    // =========================================================

    /**
     * 普通攻击动画持续时间
     */
    private static final int ATTACK_LENGTH = 27;

    /**
     * 普通魔法攻击间隔
     */
    private static final int NORMAL_ATTACK_INTERVAL = 40;

    /**
     * 火焰喷射间隔
     *
     * 600 tick = 30 秒
     */
    private static final int SPRAY_CD = 600;

    /**
     * 火焰喷射蓄力
     *
     * 160 tick = 8 秒
     */
    private static final int SPRAY_CHARGE_TICKS = 160;

    /**
     * 火焰喷射释放阶段
     */
    private static final int SPRAY_CAST_TICKS = 20;

    /**
     * 二阶段火焰元素增加间隔
     *
     * 40 tick = 2 秒
     */
    private static final int FIRE_CHARGE_INTERVAL = 40;

    /**
     * 三阶段点名间隔
     *
     * 160 tick = 8 秒
     */
    private static final int ERUPTION_INTERVAL = 160;

    /**
     * 火焰喷发倒计时
     *
     * 100 tick = 5 秒
     */
    private static final int ERUPTION_DELAY = 100;

    // =========================================================
    // 技能伤害
    // =========================================================

    private static final float NORMAL_MAGIC_DAMAGE = 12.0F;

    private static final float SPRAY_DAMAGE = 30.0F;

    private static final float FIRE_EXPLOSION_DAMAGE = 40.0F;

    private static final float EMBER_DAMAGE = 40.0F;

    private static final float ERUPTION_DAMAGE = 60.0F;

    // =========================================================
    // 技能运行变量
    // =========================================================

    /**
     * 火焰喷射 CD
     */
    private int sprayCD = 200;

    /**
     * 火焰喷射当前蓄力时间
     */
    private int sprayTimer = 0;

    /**
     * 火焰喷射目标
     */
    private Player sprayTarget;

    /**
     * 普通魔法攻击 CD
     */
    private int normalAttackTimer = 20;

    /**
     * 火焰蓄能计时
     */
    private int fireChargeTimer = 0;

    /**
     * 三阶段火焰喷发 CD
     *
     * 从进入第三阶段开始计算。
     */
    private int eruptionCD = ERUPTION_INTERVAL;

    /**
     * 当前火焰喷发倒计时
     */
    private int eruptionTimer = 0;

    /**
     * 火焰喷发位置
     */
    private BlockPos eruptionPos;

    /**
     * 火焰喷发目标
     */
    private Player eruptionTarget;

    /**
     * 死亡动画计时
     */
    private int deathTimer = 0;

    // =========================================================
    // Boss 血条
    // =========================================================

    private final ServerBossEvent bossEvent =
            new ServerBossEvent(
                    Component.literal("小樱"),
                    BossEvent.BossBarColor.PINK,
                    BossEvent.BossBarOverlay.PROGRESS
            );

    // =========================================================
    // 构造
    // =========================================================

    public SakurawitchEntity(
            EntityType<? extends PathfinderMob> type,
            Level level
    ) {
        super(type, level);
    }

    @Override
    public Component getName() {
        return Component.literal("小樱");
    }

    // =========================================================
    // Boss 玩家显示
    // =========================================================

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

    // =========================================================
    // AI
    // =========================================================

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(
                0,
                new FloatGoal(this)
        );

        /*
         * 小樱是魔法 Boss。
         *
         * 不使用 MeleeAttackGoal，
         * 避免她像普通僵尸一样贴脸砍人。
         */
        goalSelector.addGoal(
                5,
                new WaterAvoidingRandomStrollGoal(
                        this,
                        0.8D
                )
        );

        goalSelector.addGoal(
                6,
                new LookAtPlayerGoal(
                        this,
                        Player.class,
                        20.0F
                )
        );

        goalSelector.addGoal(
                7,
                new RandomLookAroundGoal(this)
        );

        targetSelector.addGoal(
                1,
                new HurtByTargetGoal(this)
        );

        targetSelector.addGoal(
                2,
                new NearestAttackableTargetGoal<>(
                        this,
                        Player.class,
                        false
                )
        );
    }

    // =========================================================
    // 同步数据定义
    // =========================================================

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();

        entityData.define(
                IS_WALKING,
                false
        );

        entityData.define(
                ATTACK_INDEX,
                0
        );

        entityData.define(
                ATTACK_TIMER,
                0
        );

        entityData.define(
                IS_DYING,
                false
        );

        entityData.define(
                PHASE,
                1
        );

        entityData.define(
                FIRE_MARK_STACKS,
                0
        );

        entityData.define(
                SKILL_STATE,
                IDLE
        );
    }

    // =========================================================
    // Tick
    // =========================================================

    @Override
    public void tick() {
        super.tick();

        /*
         * 客户端：
         * 技能粒子由服务器 sendParticles() 统一发送，
         * 因此这里不再自己重复生成。
         */
        if (level().isClientSide) {
            return;
        }

        /*
         * 死亡动画阶段
         */
        if (entityData.get(IS_DYING)) {
            tickSakuraDeath();
            return;
        }

        updateTarget();

        updatePhase();

        updateBossBar();

        tickAttackTimer();

        updateWalking();

        /*
         * 普通魔法攻击始终存在。
         */
        if (entityData.get(SKILL_STATE) == IDLE) {
            tickNormalMagicAttack();
        }

        /*
         * 火焰喷射。
         */
        tickSpraySkill();

        /*
         * 第二阶段以后：
         * 火焰元素蓄能不会因为其他技能暂时停止。
         */
        tickFireCharge();

        /*
         * 第三阶段火焰喷发。
         */
        tickEruptionSkill();
    }

    // =========================================================
    // 目标
    // =========================================================

    private void updateTarget() {
        if (tickCount % 10 != 0) {
            return;
        }

        Entity current = getTarget();

        if (current != null
                && current.isAlive()
                && !current.isRemoved()
                && distanceToSqr(current) <= 35.0D * 35.0D) {
            return;
        }

        Player nearest =
                level().getNearestPlayer(
                        this,
                        35.0D
                );

        if (valid(nearest)) {
            setTarget(nearest);
        }
    }

    // =========================================================
    // 行走动画
    // =========================================================

    private void updateWalking() {
        double dx = getX() - xo;

        double dz = getZ() - zo;

        boolean moving =
                dx * dx + dz * dz > 1.0E-5
                        && entityData.get(SKILL_STATE) == IDLE
                        && entityData.get(ATTACK_TIMER) <= 0;

        entityData.set(
                IS_WALKING,
                moving
        );
    }

    // =========================================================
    // 攻击动画计时
    // =========================================================

    private void tickAttackTimer() {
        int timer =
                entityData.get(ATTACK_TIMER);

        if (timer <= 0) {
            return;
        }

        timer--;

        entityData.set(
                ATTACK_TIMER,
                timer
        );

        if (timer <= 0) {
            entityData.set(
                    ATTACK_INDEX,
                    0
            );
        }
    }

    // =========================================================
    // Boss 血条
    // =========================================================

    private void updateBossBar() {
        float hp =
                getHealth() / getMaxHealth();

        hp =
                Math.max(
                        0.0F,
                        Math.min(
                                1.0F,
                                hp
                        )
                );

        bossEvent.setProgress(hp);

        int phase =
                entityData.get(PHASE);

        if (phase == 1) {
            bossEvent.setColor(
                    BossEvent.BossBarColor.GREEN
            );
        } else if (phase == 2) {
            bossEvent.setColor(
                    BossEvent.BossBarColor.YELLOW
            );
        } else {
            bossEvent.setColor(
                    BossEvent.BossBarColor.RED
            );
        }
    }

    // =========================================================
    // 阶段切换
    // =========================================================

    private void updatePhase() {
        float ratio =
                getHealth() / getMaxHealth();

        int nextPhase;

        if (ratio > 0.80F) {
            nextPhase = 1;
        } else if (ratio > 0.50F) {
            nextPhase = 2;
        } else {
            nextPhase = 3;
        }

        int oldPhase =
                entityData.get(PHASE);

        if (nextPhase == oldPhase) {
            return;
        }

        entityData.set(
                PHASE,
                nextPhase
        );

        /*
         * 阶段切换时取消当前施法动作，
         * 但不清除二阶段火焰元素。
         */
        cancelCurrentSkill();

        level().playSound(
                null,
                blockPosition(),
                nextPhase == 2
                        ? SoundEvents.BLAZE_SHOOT
                        : SoundEvents.WITHER_SPAWN,
                SoundSource.HOSTILE,
                2.0F,
                nextPhase == 2
                        ? 0.65F
                        : 1.1F
        );

        announce(
                "§c⚠ 小樱进入第"
                        + nextPhase
                        + "阶段！"
        );

        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ModParticles.SAKURA_MAGIC.get(),
                    getX(),
                    getY() + 1.0D,
                    getZ(),
                    35,
                    1.5D,
                    1.0D,
                    1.5D,
                    0.08D
            );

            serverLevel.sendParticles(
                    ModParticles.SAKURA_PETAL.get(),
                    getX(),
                    getY() + 1.2D,
                    getZ(),
                    25,
                    1.8D,
                    1.2D,
                    1.8D,
                    0.03D
            );
        }

        /*
         * 进入第三阶段时，
         * 从新的 8 秒周期开始计算火焰喷发。
         */
        if (nextPhase == 3) {
            eruptionCD = ERUPTION_INTERVAL;
        }
    }

    // =========================================================
    // 普通魔法攻击
    // =========================================================

    private void tickNormalMagicAttack() {
        if (entityData.get(SKILL_STATE) != IDLE) {
            return;
        }

        if (entityData.get(ATTACK_TIMER) > 0) {
            return;
        }

        if (normalAttackTimer > 0) {
            normalAttackTimer--;
            return;
        }

        Player target = getNearestCombatPlayer();

        if (!valid(target)) {
            normalAttackTimer = 20;
            return;
        }

        /*
         * 小樱普通攻击不是近战砍人，
         * 而是魔法攻击。
         */
        lookAt(
                target.position().add(
                        0.0D,
                        1.0D,
                        0.0D
                )
        );

        entityData.set(
                ATTACK_INDEX,
                1
        );

        entityData.set(
                ATTACK_TIMER,
                ATTACK_LENGTH
        );

        getNavigation().stop();

        setDeltaMovement(
                Vec3.ZERO
        );

        magicDamage(
                target,
                NORMAL_MAGIC_DAMAGE
        );

        addMagicVulnerability(target);

        /*
         * 普通魔法攻击视觉。
         */
        if (level() instanceof ServerLevel serverLevel) {
            Vec3 start =
                    position().add(
                            0.0D,
                            1.45D,
                            0.0D
                    );

            Vec3 direction =
                    target.position()
                            .add(
                                    0.0D,
                                    1.0D,
                                    0.0D
                            )
                            .subtract(start)
                            .normalize();

            for (int i = 1; i <= 8; i++) {
                Vec3 pos =
                        start.add(
                                direction.scale(
                                        i * 0.65D
                                )
                        );

                serverLevel.sendParticles(
                        ModParticles.SAKURA_MAGIC.get(),
                        pos.x,
                        pos.y,
                        pos.z,
                        1,
                        0.03D,
                        0.03D,
                        0.03D,
                        0.0D
                );
            }

            serverLevel.sendParticles(
                    ModParticles.SAKURA_MAGIC.get(),
                    target.getX(),
                    target.getY() + 1.0D,
                    target.getZ(),
                    10,
                    0.35D,
                    0.45D,
                    0.35D,
                    0.03D
            );
        }

        level().playSound(
                null,
                blockPosition(),
                SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.HOSTILE,
                1.2F,
                0.8F
        );

        normalAttackTimer =
                NORMAL_ATTACK_INTERVAL;
    }

    // =========================================================
    // 魔法易伤
    // =========================================================

    private void addMagicVulnerability(Player player) {
        if (!valid(player)) {
            return;
        }

        MobEffectInstance old =
                player.getEffect(
                        ModEffects.MAGIC_VULNERABILITY.get()
                );

        int amplifier;

        if (old == null) {
            amplifier = 0;
        } else {
            amplifier =
                    Math.min(
                            9,
                            old.getAmplifier() + 1
                    );
        }

        /*
         * 10 层上限。
         */
        player.addEffect(
                new MobEffectInstance(
                        ModEffects.MAGIC_VULNERABILITY.get(),
                        200,
                        amplifier,
                        false,
                        true,
                        true
                )
        );
    }

    // =========================================================
    // 火焰喷射
    // =========================================================

    private void tickSpraySkill() {
        int state =
                entityData.get(SKILL_STATE);

        /*
         * 蓄力阶段
         */
        if (state == SPRAY_CHARGE) {
            tickSprayCharge();
            return;
        }

        /*
         * 释放阶段
         */
        if (state == SPRAY_CAST) {
            tickSprayCast();
            return;
        }

        /*
         * 只有空闲状态才能开始新的火焰喷射。
         */
        if (state != IDLE) {
            return;
        }

        if (sprayCD > 0) {
            sprayCD--;
            return;
        }

        List<Player> players =
                players(30.0D);

        if (players.isEmpty()) {
            sprayCD = 40;
            return;
        }

        /*
         * 随机点名。
         */
        sprayTarget =
                players.get(
                        random.nextInt(
                                players.size()
                        )
                );

        entityData.set(
                SKILL_STATE,
                SPRAY_CHARGE
        );

        /*
         * attack_02 = 火焰喷射蓄力动作
         */
        entityData.set(
                ATTACK_INDEX,
                2
        );

        entityData.set(
                ATTACK_TIMER,
                SPRAY_CHARGE_TICKS
                        + SPRAY_CAST_TICKS
                        + 10
        );

        sprayTimer = 0;

        getNavigation().stop();

        setDeltaMovement(
                Vec3.ZERO
        );

        if (valid(sprayTarget)) {
            tell(
                    sprayTarget,
                    "§c⚠ 小樱正在锁定你！"
                            + " 火焰喷射即将释放！"
            );
        }

        announce(
                "§d✦ 小樱开始蓄力火焰喷射！"
        );

        level().playSound(
                null,
                blockPosition(),
                SoundEvents.BLAZE_SHOOT,
                SoundSource.HOSTILE,
                2.0F,
                0.55F
        );
    }

    private void tickSprayCharge() {
        sprayTimer++;

        getNavigation().stop();

        setDeltaMovement(
                Vec3.ZERO
        );

        if (valid(sprayTarget)) {
            lookAt(
                    sprayTarget.position().add(
                            0.0D,
                            1.0D,
                            0.0D
                    )
            );
        }

        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        /*
         * 小樱自身蓄力魔法粒子。
         */
        for (int i = 0; i < 10; i++) {
            double angle =
                    random.nextDouble()
                            * Math.PI * 2.0D;

            double radius =
                    0.45D
                            + random.nextDouble()
                            * 0.9D;

            serverLevel.sendParticles(
                    ModParticles.SAKURA_MAGIC.get(),
                    getX()
                            + Math.cos(angle)
                            * radius,
                    getY()
                            + 0.15D
                            + random.nextDouble()
                            * 1.2D,
                    getZ()
                            + Math.sin(angle)
                            * radius,
                    1,
                    0.0D,
                    0.025D,
                    0.0D,
                    0.0D
            );
        }

        /*
         * 被点名玩家脚下持续出现警告粒子。
         */
        if (valid(sprayTarget)) {
            serverLevel.sendParticles(
                    ModParticles.SAKURA_WARNING.get(),
                    sprayTarget.getX(),
                    sprayTarget.getY() + 0.05D,
                    sprayTarget.getZ(),
                    4,
                    0.35D,
                    0.03D,
                    0.35D,
                    0.01D
            );
        }

        /*
         * 蓄力完成。
         */
        if (sprayTimer >= SPRAY_CHARGE_TICKS) {
            entityData.set(
                    SKILL_STATE,
                    SPRAY_CAST
            );

            sprayTimer = 0;

            castSpray();
        }
    }

    private void tickSprayCast() {
        sprayTimer++;

        getNavigation().stop();

        setDeltaMovement(
                Vec3.ZERO
        );

        /*
         * 释放期间继续朝向目标。
         */
        if (valid(sprayTarget)) {
            lookAt(
                    sprayTarget.position().add(
                            0.0D,
                            1.0D,
                            0.0D
                    )
            );
        }

        /*
         * 释放阶段的持续火焰粒子。
         */
        if (level() instanceof ServerLevel serverLevel) {
            Vec3 direction =
                    getLookAngle().normalize();

            Vec3 start =
                    position().add(
                            0.0D,
                            1.35D,
                            0.0D
                    );

            for (int i = 0; i < 8; i++) {
                double distance =
                        1.0D + i * 1.5D;

                Vec3 pos =
                        start.add(
                                direction.scale(
                                        distance
                                )
                        );

                serverLevel.sendParticles(
                        ModParticles.SAKURA_FLAME.get(),
                        pos.x,
                        pos.y,
                        pos.z,
                        4,
                        0.35D,
                        0.35D,
                        0.35D,
                        0.02D
                );
            }
        }

        if (sprayTimer >= SPRAY_CAST_TICKS) {
            cancelCurrentSkill();

            sprayTarget = null;

            /*
             * 从一次喷射结束后开始重新计算 30 秒。
             */
            sprayCD = SPRAY_CD;
        }
    }

    // =========================================================
    // 火焰喷射实际伤害
    // =========================================================

    private void castSpray() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 start =
                position().add(
                        0.0D,
                        1.35D,
                        0.0D
                );

        Vec3 direction;

        if (valid(sprayTarget)) {
            direction =
                    sprayTarget.position()
                            .add(
                                    0.0D,
                                    1.0D,
                                    0.0D
                            )
                            .subtract(start)
                            .normalize();
        } else {
            direction =
                    getLookAngle()
                            .normalize();
        }

        List<Player> hit =
                new ArrayList<>();

        /*
         * 15 格射程。
         *
         * dot >= cos(60°)
         * 即前方约 120° 扇形。
         */
        for (Player player :
                serverLevel.getEntitiesOfClass(
                        Player.class,
                        getBoundingBox()
                                .inflate(15.0D)
                )) {

            if (!valid(player)) {
                continue;
            }

            Vec3 targetPoint =
                    player.position()
                            .add(
                                    0.0D,
                                    1.0D,
                                    0.0D
                            );

            Vec3 delta =
                    targetPoint.subtract(start);

            double distance =
                    delta.length();

            if (distance <= 0.01D
                    || distance > 15.0D) {
                continue;
            }

            Vec3 normalized =
                    delta.normalize();

            if (normalized.dot(direction) >= 0.5D) {
                hit.add(player);
            }
        }

        /*
         * 火焰喷射的核心机制：
         *
         * 多名玩家抱团时，
         * 火焰喷射伤害降低 50%。
         *
         * 这就是奶块攻略里常见的
         * “抱团吃喷火减伤”机制。
         */
        boolean grouped =
                hit.size() >= 2;

        float damage =
                grouped
                        ? SPRAY_DAMAGE * 0.5F
                        : SPRAY_DAMAGE;

        for (Player player : hit) {
            magicDamage(
                    player,
                    damage
            );
        }

        if (grouped) {
            announce(
                    "§a✔ 多名玩家靠近，火焰喷射伤害降低！"
            );
        }

        if (valid(sprayTarget)
                && !hit.contains(sprayTarget)) {

            tell(
                    sprayTarget,
                    "§a✔ 你成功躲开了火焰喷射！"
            );
        }

        /*
         * 扇形火焰视觉。
         */
        for (int ring = 1; ring <= 6; ring++) {
            double distance =
                    ring * 2.0D;

            double halfAngle =
                    Math.toRadians(60.0D);

            int count =
                    9 + ring * 2;

            for (int i = 0; i < count; i++) {
                double ratio =
                        count <= 1
                                ? 0.0D
                                : (double) i
                                / (count - 1);

                double angle =
                        -halfAngle
                                + ratio
                                * halfAngle
                                * 2.0D;

                Vec3 forward =
                        rotateHorizontal(
                                direction,
                                angle
                        );

                Vec3 pos =
                        start.add(
                                forward.scale(
                                        distance
                                )
                        );

                serverLevel.sendParticles(
                        ModParticles.SAKURA_FLAME.get(),
                        pos.x,
                        pos.y,
                        pos.z,
                        1,
                        0.15D,
                        0.25D,
                        0.15D,
                        0.01D
                );
            }
        }

        serverLevel.sendParticles(
                ModParticles.SAKURA_EXPLOSION.get(),
                start.x,
                start.y,
                start.z,
                18,
                0.8D,
                0.6D,
                0.8D,
                0.04D
        );

        level().playSound(
                null,
                blockPosition(),
                SoundEvents.BLAZE_SHOOT,
                SoundSource.HOSTILE,
                2.5F,
                0.7F
        );
    }

    // =========================================================
    // 火焰蓄能
    // =========================================================

    private void tickFireCharge() {
        if (entityData.get(PHASE) < 2) {
            return;
        }

        fireChargeTimer++;

        /*
         * 环绕小樱的火焰元素视觉。
         */
        if (fireChargeTimer % 5 == 0
                && level() instanceof ServerLevel serverLevel) {

            int stacks =
                    entityData.get(
                            FIRE_MARK_STACKS
                    );

            double radius =
                    1.1D
                            + stacks * 0.08D;

            for (int i = 0; i < 6; i++) {
                double angle =
                        random.nextDouble()
                                * Math.PI * 2.0D;

                serverLevel.sendParticles(
                        ModParticles.SAKURA_FLAME.get(),
                        getX()
                                + Math.cos(angle)
                                * radius,
                        getY()
                                + 0.15D
                                + random.nextDouble()
                                * 1.5D,
                        getZ()
                                + Math.sin(angle)
                                * radius,
                        1,
                        0.0D,
                        0.02D,
                        0.0D,
                        0.0D
                );
            }
        }

        /*
         * 每 2 秒增加一层。
         */
        if (fireChargeTimer < FIRE_CHARGE_INTERVAL) {
            return;
        }

        fireChargeTimer = 0;

        int stacks =
                entityData.get(
                        FIRE_MARK_STACKS
                );

        stacks =
                Math.min(
                        10,
                        stacks + 1
                );

        entityData.set(
                FIRE_MARK_STACKS,
                stacks
        );

        level().playSound(
                null,
                blockPosition(),
                SoundEvents.FIRECHARGE_USE,
                SoundSource.HOSTILE,
                0.8F,
                0.9F + stacks * 0.03F
        );

        if (level() instanceof ServerLevel serverLevel) {

            /*
             * 每增加一层明显闪一下。
             */
            serverLevel.sendParticles(
                    ModParticles.SAKURA_MAGIC.get(),
                    getX(),
                    getY() + 1.0D,
                    getZ(),
                    8,
                    0.5D,
                    0.6D,
                    0.5D,
                    0.02D
            );
        }

        /*
         * 10 层爆炸。
         */
        if (stacks >= 10) {
            entityData.set(
                    FIRE_MARK_STACKS,
                    0
            );

            explodeFireCharge();
        }
    }

    // =========================================================
    // 火焰爆炸
    // =========================================================

    private void explodeFireCharge() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        announce(
                "§4☠ 小樱释放了火焰爆炸！"
        );

        level().playSound(
                null,
                blockPosition(),
                SoundEvents.GENERIC_EXPLODE,
                SoundSource.HOSTILE,
                3.0F,
                0.65F
        );

        serverLevel.sendParticles(
                ModParticles.SAKURA_EXPLOSION.get(),
                getX(),
                getY() + 1.0D,
                getZ(),
                35,
                2.0D,
                1.2D,
                2.0D,
                0.08D
        );

        List<Player> players =
                players(30.0D);

        /*
         * 全队基础魔法伤害。
         */
        for (Player player : players) {

            if (!valid(player)) {
                continue;
            }

            magicDamage(
                    player,
                    FIRE_EXPLOSION_DAMAGE
            );
        }

        /*
         * 5 格内队友产生额外余烬伤害。
         *
         * 所以玩家应该分散。
         */
        for (Player player : players) {

            if (!valid(player)) {
                continue;
            }

            int nearby =
                    0;

            for (Player other : players) {

                if (other == player) {
                    continue;
                }

                if (other.distanceToSqr(player) <= 25.0D) {
                    nearby++;
                }
            }

            if (nearby <= 0) {
                continue;
            }

            /*
             * 每个过近队友增加一次余烬伤害。
             */
            magicDamage(
                    player,
                    EMBER_DAMAGE * nearby
            );

            tell(
                    player,
                    "§c⚠ 你与队友距离过近！"
                            + " 受到额外余烬伤害！"
            );
        }
    }

    // =========================================================
    // 火焰喷发
    // =========================================================

    private void tickEruptionSkill() {

        if (entityData.get(PHASE) < 3) {
            return;
        }

        /*
         * 当前已经在进行一次火焰喷发。
         */
        if (entityData.get(SKILL_STATE) == ERUPTION) {
            tickEruptionCharge();
            return;
        }

        if (eruptionCD > 0) {
            eruptionCD--;
            return;
        }

        /*
         * 如果小樱正在火焰喷射蓄力，
         * 不强行打断当前技能。
         */
        if (entityData.get(SKILL_STATE) != IDLE) {
            return;
        }

        List<Player> players =
                players(30.0D);

        if (players.isEmpty()) {
            eruptionCD = 40;
            return;
        }

        /*
         * 随机点名。
         */
        eruptionTarget =
                players.get(
                        random.nextInt(
                                players.size()
                        )
                );

        eruptionPos =
                eruptionTarget.blockPosition();

        eruptionTimer =
                ERUPTION_DELAY;

        entityData.set(
                SKILL_STATE,
                ERUPTION
        );

        /*
         * attack_04 用作火焰喷发动画。
         */
        entityData.set(
                ATTACK_INDEX,
                4
        );

        entityData.set(
                ATTACK_TIMER,
                ERUPTION_DELAY + ATTACK_LENGTH
        );

        getNavigation().stop();

        setDeltaMovement(
                Vec3.ZERO
        );

        /*
         * 注意：
         *
         * CD 在“开始点名”时就重新设置。
         * 所以点名间隔是严格 8 秒，
         * 而不是爆炸以后再等 8 秒。
         */
        eruptionCD =
                ERUPTION_INTERVAL;

        tell(
                eruptionTarget,
                "§c⚠ 你脚下出现了火焰！"
                        + " 5 秒后爆发，快离开！"
        );

        announce(
                "§d✦ 小樱释放火焰喷发！"
        );

        level().playSound(
                null,
                eruptionPos,
                SoundEvents.FIRECHARGE_USE,
                SoundSource.HOSTILE,
                1.5F,
                0.8F
        );
    }

    private void tickEruptionCharge() {

        getNavigation().stop();

        setDeltaMovement(
                Vec3.ZERO
        );

        if (eruptionPos == null) {
            cancelCurrentSkill();
            return;
        }

        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        double centerX =
                eruptionPos.getX() + 0.5D;

        double centerY =
                eruptionPos.getY() + 0.05D;

        double centerZ =
                eruptionPos.getZ() + 0.5D;

        /*
         * 圆形火焰警告区域。
         */
        int ringParticles =
                20;

        for (int i = 0; i < ringParticles; i++) {

            double angle =
                    i
                            * Math.PI
                            * 2.0D
                            / ringParticles;

            double radius =
                    1.6D;

            serverLevel.sendParticles(
                    ModParticles.SAKURA_WARNING.get(),
                    centerX
                            + Math.cos(angle)
                            * radius,
                    centerY,
                    centerZ
                            + Math.sin(angle)
                            * radius,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D
            );
        }

        /*
         * 倒计时最后 2 秒，
         * 中心火焰明显增强。
         */
        if (eruptionTimer <= 40) {

            double scale =
                    0.5D
                            + (40 - eruptionTimer)
                            * 0.025D;

            serverLevel.sendParticles(
                    ModParticles.SAKURA_FLAME.get(),
                    centerX,
                    centerY + 0.15D,
                    centerZ,
                    10,
                    scale,
                    0.15D,
                    scale,
                    0.03D
            );
        }

        eruptionTimer--;

        /*
         * 5 秒结束，爆炸。
         */
        if (eruptionTimer <= 0) {

            explodeEruption();

            eruptionPos = null;

            eruptionTarget = null;

            cancelCurrentSkill();
        }
    }

    // =========================================================
    // 火焰喷发爆炸
    // =========================================================

    private void explodeEruption() {

        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (eruptionPos == null) {
            return;
        }

        BlockPos pos =
                eruptionPos;

        double centerX =
                pos.getX() + 0.5D;

        double centerY =
                pos.getY() + 0.5D;

        double centerZ =
                pos.getZ() + 0.5D;

        level().playSound(
                null,
                pos,
                SoundEvents.GENERIC_EXPLODE,
                SoundSource.HOSTILE,
                2.2F,
                0.9F
        );

        serverLevel.sendParticles(
                ModParticles.SAKURA_ERUPTION.get(),
                centerX,
                centerY,
                centerZ,
                35,
                1.5D,
                0.7D,
                1.5D,
                0.08D
        );

        serverLevel.sendParticles(
                ModParticles.SAKURA_EXPLOSION.get(),
                centerX,
                centerY,
                centerZ,
                20,
                1.0D,
                0.6D,
                1.0D,
                0.05D
        );

        /*
         * 3 格爆炸范围。
         */
        AABB box =
                new AABB(pos)
                        .inflate(3.0D);

        for (Player player :
                serverLevel.getEntitiesOfClass(
                        Player.class,
                        box
                )) {

            if (!valid(player)) {
                continue;
            }

            double dx =
                    player.getX()
                            - centerX;

            double dz =
                    player.getZ()
                            - centerZ;

            /*
             * 只判断水平距离。
             */
            if (dx * dx + dz * dz <= 9.0D) {

                magicDamage(
                        player,
                        ERUPTION_DAMAGE
                );
            }
        }
    }

    // =========================================================
    // 取消当前技能
    // =========================================================

    private void cancelCurrentSkill() {

        entityData.set(
                SKILL_STATE,
                IDLE
        );

        entityData.set(
                ATTACK_INDEX,
                0
        );

        entityData.set(
                ATTACK_TIMER,
                0
        );

        sprayTimer = 0;
    }

    // =========================================================
    // 魔法伤害
    // =========================================================

    private void magicDamage(
            Player player,
            float baseDamage
    ) {

        if (!valid(player)) {
            return;
        }

        MobEffectInstance effect =
                player.getEffect(
                        ModEffects.MAGIC_VULNERABILITY.get()
                );

        /*
         * 魔法易伤每层让受到的小樱魔法伤害
         * 增加 5%。
         *
         * 最高 10 层。
         */
        int stacks =
                effect == null
                        ? 0
                        : Math.min(
                                10,
                                effect.getAmplifier() + 1
                        );

        float damage =
                baseDamage
                        * (
                        1.0F
                                + 0.05F
                                * stacks
                );

        player.hurt(
                damageSources().indirectMagic(
                        this,
                        this
                ),
                damage
        );
    }

    // =========================================================
    // 玩家列表
    // =========================================================

    private List<Player> players(
            double radius
    ) {

        List<Player> result =
                new ArrayList<>(
                        level().getEntitiesOfClass(
                                Player.class,
                                getBoundingBox()
                                        .inflate(radius)
                        )
                );

        result.removeIf(
                player -> !valid(player)
        );

        return result;
    }

    private Player getNearestCombatPlayer() {

        Player nearest =
                level().getNearestPlayer(
                        this,
                        35.0D
                );

        return valid(nearest)
                ? nearest
                : null;
    }

    // =========================================================
    // 玩家有效性
    // =========================================================

    private boolean valid(
            Player player
    ) {

        return player != null
                && player.isAlive()
                && !player.isRemoved()
                && !player.isCreative()
                && !player.isSpectator();
    }

    // =========================================================
    // 玩家提示
    // =========================================================

    private void tell(
            Player player,
            String text
    ) {

        if (player instanceof ServerPlayer serverPlayer) {

            serverPlayer.displayClientMessage(
                    Component.literal(text),
                    true
            );
        }
    }

    private void announce(
            String text
    ) {

        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        for (ServerPlayer player :
                serverLevel.getEntitiesOfClass(
                        ServerPlayer.class,
                        getBoundingBox().inflate(35.0D)
                )) {

            if (!player.isAlive()
                    || player.isSpectator()) {
                continue;
            }

            player.displayClientMessage(
                    Component.literal(text),
                    true
            );
        }
    }

    // =========================================================
    // 朝向目标
    // =========================================================

    private void lookAt(
            Vec3 target
    ) {

        Vec3 delta =
                target.subtract(
                        position().add(
                                0.0D,
                                getEyeHeight(),
                                0.0D
                        )
                );

        double horizontal =
                Math.sqrt(
                        delta.x * delta.x
                                + delta.z * delta.z
                );

        float yaw =
                (float) Math.toDegrees(
                        Math.atan2(
                                -delta.x,
                                delta.z
                        )
                );

        float pitch =
                (float) -Math.toDegrees(
                        Math.atan2(
                                delta.y,
                                horizontal
                        )
                );

        setYRot(yaw);

        setXRot(pitch);

        yHeadRot = yaw;

        yBodyRot = yaw;
    }

    // =========================================================
    // 水平向量旋转
    // =========================================================

    private Vec3 rotateHorizontal(
            Vec3 vector,
            double angle
    ) {

        double cos =
                Math.cos(angle);

        double sin =
                Math.sin(angle);

        double x =
                vector.x * cos
                        - vector.z * sin;

        double z =
                vector.x * sin
                        + vector.z * cos;

        return new Vec3(
                x,
                vector.y,
                z
        ).normalize();
    }

    // =========================================================
    // 受到伤害
    // =========================================================

    @Override
    public boolean hurt(
            DamageSource source,
            float amount
    ) {

        if (entityData.get(IS_DYING)) {
            return false;
        }

        return super.hurt(
                source,
                amount
        );
    }

    // =========================================================
    // 死亡
    // =========================================================

    @Override
    public void die(
            DamageSource source
    ) {

        if (level().isClientSide) {
            super.die(source);
            return;
        }

        if (entityData.get(IS_DYING)) {
            return;
        }

        entityData.set(
                IS_DYING,
                true
        );

        entityData.set(
                SKILL_STATE,
                IDLE
        );

        entityData.set(
                ATTACK_INDEX,
                0
        );

        entityData.set(
                ATTACK_TIMER,
                0
        );

        setInvulnerable(true);

        setHealth(0.0F);

        getNavigation().stop();

        setDeltaMovement(
                Vec3.ZERO
        );

        deathTimer = 0;

        bossEvent.removeAllPlayers();

        level().playSound(
                null,
                blockPosition(),
                SoundEvents.WITHER_DEATH,
                SoundSource.HOSTILE,
                2.0F,
                1.0F
        );
    }

    // =========================================================
    // 小樱死亡动画
    // =========================================================

    private void tickSakuraDeath() {

        setInvulnerable(true);

        setDeltaMovement(
                Vec3.ZERO
        );

        getNavigation().stop();

        deathTimer++;

        if (level() instanceof ServerLevel serverLevel
                && deathTimer % 3 == 0) {

            serverLevel.sendParticles(
                    ModParticles.SAKURA_FLAME.get(),
                    getX(),
                    getY() + 1.0D,
                    getZ(),
                    10,
                    0.8D,
                    0.8D,
                    0.8D,
                    0.03D
            );

            serverLevel.sendParticles(
                    ModParticles.SAKURA_PETAL.get(),
                    getX(),
                    getY() + 1.0D,
                    getZ(),
                    5,
                    0.8D,
                    0.8D,
                    0.8D,
                    0.02D
            );
        }

        /*
         * 给 death 动画留出时间。
         */
        if (deathTimer >= 35) {

            setInvulnerable(false);

            super.die(
                    damageSources().generic()
            );
        }
    }

    // =========================================================
    // 不远距离消失
    // =========================================================

    @Override
    public boolean removeWhenFarAway(
            double distance
    ) {

        return false;
    }

    // =========================================================
    // NBT 保存
    // =========================================================

    @Override
    public void addAdditionalSaveData(
            CompoundTag tag
    ) {

        super.addAdditionalSaveData(tag);

        tag.putInt(
                "FireSprayCD",
                sprayCD
        );

        tag.putInt(
                "FireChargeTimer",
                fireChargeTimer
        );

        tag.putInt(
                "FireEruptionCD",
                eruptionCD
        );

        tag.putInt(
                "NormalAttackTimer",
                normalAttackTimer
        );

        tag.putInt(
                "FireMarkStacks",
                entityData.get(
                        FIRE_MARK_STACKS
                )
        );

        tag.putInt(
                "Phase",
                entityData.get(
                        PHASE
                )
        );
    }

    // =========================================================
    // NBT 读取
    // =========================================================

    @Override
    public void readAdditionalSaveData(
            CompoundTag tag
    ) {

        super.readAdditionalSaveData(tag);

        sprayCD =
                tag.getInt(
                        "FireSprayCD"
                );

        fireChargeTimer =
                tag.getInt(
                        "FireChargeTimer"
                );

        eruptionCD =
                tag.getInt(
                        "FireEruptionCD"
                );

        normalAttackTimer =
                tag.getInt(
                        "NormalAttackTimer"
                );

        entityData.set(
                FIRE_MARK_STACKS,
                Math.max(
                        0,
                        Math.min(
                                10,
                                tag.getInt(
                                        "FireMarkStacks"
                                )
                        )
                )
        );

        entityData.set(
                PHASE,
                Math.max(
                        1,
                        Math.min(
                                3,
                                tag.getInt(
                                        "Phase"
                                )
                        )
                )
        );
    }

    // =========================================================
    // 属性
    // =========================================================

    public static AttributeSupplier.Builder createAttributes() {

        return PathfinderMob.createMobAttributes()

                .add(
                        Attributes.MAX_HEALTH,
                        50000.0D
                )

                .add(
                        Attributes.ARMOR,
                        10.0D
                )

                .add(
                        Attributes.ATTACK_DAMAGE,
                        15.0D
                )

                .add(
                        Attributes.MOVEMENT_SPEED,
                        0.3D
                )

                .add(
                        Attributes.FOLLOW_RANGE,
                        35.0D
                )

                .add(
                        Attributes.KNOCKBACK_RESISTANCE,
                        1.0D
                );
    }
}
