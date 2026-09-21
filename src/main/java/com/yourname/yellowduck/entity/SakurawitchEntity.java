package com.yourname.yellowduck.entity;

import com.yourname.yellowduck.particle.ModParticles;
import com.yourname.yellowduck.registry.ModEffects;
import com.yourname.yellowduck.registry.ModBlocks;
import com.yourname.yellowduck.registry.ModEntities;
import com.yourname.yellowduck.registry.ModSounds;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    /** 布偶熊被击败后，小樱进入伙伴死亡狂暴。 */
    public static final EntityDataAccessor<Boolean> PARTNER_RAGE =
            SynchedEntityData.defineId(
                    SakurawitchEntity.class,
                    EntityDataSerializers.BOOLEAN
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

    /** NetCraft：进入 2.5 格才开始普通攻击。 */
    private static final double NORMAL_ATTACK_RANGE = 2.5D;

    /** NetCraft：15 tick 后结算时，目标必须仍在 3.5 格内。 */
    private static final double NORMAL_DAMAGE_RANGE = 3.5D;

    /** NetCraft：攻击动作开始后 15 tick 才真正造成伤害。 */
    private static final int NORMAL_DAMAGE_DELAY = 15;

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

    private static final float NORMAL_MAGIC_DAMAGE = 110.0F;

    private static final float SPRAY_DAMAGE = 30.0F;

    private static final float FIRE_EXPLOSION_DAMAGE = 40.0F;

    private static final float EMBER_DAMAGE = 40.0F;

    private static final float ERUPTION_DAMAGE = 60.0F;

    // NetCraft 风格三类防御 + 固定减伤。
    // T2 Boss 近战额外减 5%，随后减去对应防御，最后再做固定减伤。
    private static final float MELEE_DEFENSE = 60.0F;
    private static final float RANGED_DEFENSE = 10.0F;
    private static final float MAGIC_DEFENSE = 10.0F;
    private static final float FIXED_DAMAGE_REDUCTION = 0.50F;
    private static final float T2_MELEE_REDUCTION = 0.05F;

    // 布偶熊先倒下后，小樱进入永久强化。
    private static final float PARTNER_RAGE_DAMAGE_MULTIPLIER = 1.25F;
    private static final int PARTNER_RAGE_NORMAL_ATTACK_INTERVAL = 30;
    private static final int PARTNER_RAGE_SPRAY_CD = 450;
    private static final int PARTNER_RAGE_FIRE_CHARGE_INTERVAL = 30;
    private static final int PARTNER_RAGE_ERUPTION_INTERVAL = 120;

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

    private UUID pendingNormalAttackTargetUUID;
    private int pendingNormalAttackDamageTicks = 0;

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

    /** Stargazer 原版地火 PNG 标记实际放置的位置。 */
    private BlockPos eruptionMarkerPos;

    /**
     * 死亡动画计时
     */
    private int deathTimer = 0;

    // 小樱专属音效只触发一次
    private boolean playedPhaseTwoSound = false;
    private boolean playedDeathSound = false;

    // 布偶熊：第二阶段召唤一次，之后独立战斗。
    private boolean toyBearSummoned = false;
    private UUID toyBearUUID;
    private boolean partnerRage = false;

    // NetCraft 1.4.18：仇恨、出生点限制、脱战回位和回血。
    private final SakuraHatredManager hatredManager = new SakuraHatredManager(this);
    private Vec3 netcraftSpawnPosition;
    private boolean netcraftSpawnPositionSet = false;

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
        // 使用客户端 NetCraft 风格 HUD，避免同时出现原版 Boss 血条。
        // bossEvent.addPlayer(player);
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
         * NetCraft 小樱不使用 vanilla 的 HurtByTargetGoal /
         * NearestAttackableTargetGoal，也不随机游走。
         * 锁敌完全由 SakuraHatredManager 决定；无仇恨时返回出生点。
         */
        goalSelector.addGoal(
                6,
                new LookAtPlayerGoal(
                        this,
                        Player.class,
                        20.0F
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

        entityData.define(
                PARTNER_RAGE,
                false
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

        ensureNetcraftSpawnPosition();
        hatredManager.tick();

        // NetCraft 普攻冷却从攻击开始就持续计时，不等动画结束。
        if (normalAttackTimer > 0) {
            normalAttackTimer--;
        }
        tickPendingNormalAttackDamage();

        updatePhase();

        updateBossBar();

        tickAttackTimer();

        updateWalking();

        /*
         * NetCraft：没有有效仇恨目标时不进入攻击/技能循环。
         * 仇恨控制器会负责回出生点并在脱战后回满血。
         */
        if (!hatredManager.hasCurrentTarget()) {
            return;
        }

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
    // NetCraft 出生点 / 仇恨
    // =========================================================

    private void ensureNetcraftSpawnPosition() {
        if (!netcraftSpawnPositionSet) {
            netcraftSpawnPosition = position();
            netcraftSpawnPositionSet = true;
        }
    }

    Vec3 getNetcraftSpawnPosition() {
        return netcraftSpawnPosition;
    }

    /**
     * SakuraHatredManager 脱战时调用。
     * fullReset=true 表示已回到出生点并完成回血。
     */
    void onNetcraftDisengage(boolean fullReset) {
        getNavigation().stop();
        setTarget(null);
        cancelCurrentSkill();

        sprayTarget = null;
        eruptionTarget = null;
        removeEruptionMarker();
        eruptionPos = null;
        eruptionTimer = 0;
        pendingNormalAttackTargetUUID = null;
        pendingNormalAttackDamageTicks = 0;

        if (!fullReset) {
            return;
        }

        // 回满血后视为一次完整重置，避免旧阶段技能继续残留。
        entityData.set(PHASE, 1);
        entityData.set(FIRE_MARK_STACKS, 0);
        fireChargeTimer = 0;
        sprayCD = 200;
        normalAttackTimer = 20;
        eruptionCD = currentEruptionInterval();
        playedPhaseTwoSound = false;
        partnerRage = false;
        entityData.set(PARTNER_RAGE, false);

        removeToyBear();
        toyBearSummoned = false;
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
        if (nextPhase == 2 && !playedPhaseTwoSound) {
            playedPhaseTwoSound = true;
            level().playSound(
                    null,
                    blockPosition(),
                    ModSounds.SAKURA_XIONG.get(),
                    SoundSource.HOSTILE,
                    2.0F,
                    1.0F
            );
        }

        if (nextPhase == 2) {
            summonToyBear();
        }

        if (nextPhase == 3) {
            eruptionCD = currentEruptionInterval();
        }
    }

    // =========================================================
    // 布偶熊召唤
    // =========================================================

    private void summonToyBear() {
        if (toyBearSummoned || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ToyBearEntity bear = ModEntities.TOY_BEAR.get().create(serverLevel);
        if (bear == null) {
            return;
        }

        double angle = random.nextDouble() * Math.PI * 2.0D;
        double radius = 1.8D;

        bear.moveTo(
                getX() + Math.cos(angle) * radius,
                getY(),
                getZ() + Math.sin(angle) * radius,
                getYRot() + 180.0F,
                0.0F
        );
        bear.setOwnerSakura(this);
        serverLevel.addFreshEntity(bear);
        toyBearUUID = bear.getUUID();
        toyBearSummoned = true;

        serverLevel.sendParticles(
                ModParticles.SAKURA_BEAR_RAGE_BURST.get(),
                bear.getX(),
                bear.getY() + 0.8D,
                bear.getZ(),
                18,
                0.7D,
                0.6D,
                0.7D,
                0.06D
        );
    }

    /** 供布偶熊继承小樱当前仇恨目标。 */
    public Player getBearCombatTarget() {
        Player target = hatredManager.getCurrentTarget();
        return valid(target) ? target : null;
    }

    /** 布偶熊先被击败：小樱进入永久伙伴死亡狂暴。 */
    public void onToyBearDefeated() {
        if (level().isClientSide || partnerRage || entityData.get(IS_DYING)) return;

        partnerRage = true;
        entityData.set(PARTNER_RAGE, true);
        toyBearUUID = null;

        // 让下一轮技能更快到来，但不强行打断当前技能。
        normalAttackTimer = Math.min(normalAttackTimer, 10);
        sprayCD = Math.min(sprayCD, 120);
        eruptionCD = Math.min(eruptionCD, 60);

        announce("§4⚠ 布偶熊被击败，小樱陷入狂暴！");
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ModParticles.SAKURA_EXPLOSION.get(),
                    getX(), getY() + 1.0D, getZ(),
                    40, 1.4D, 1.0D, 1.4D, 0.08D
            );
            serverLevel.sendParticles(
                    ModParticles.SAKURA_MAGIC.get(),
                    getX(), getY() + 1.2D, getZ(),
                    45, 1.7D, 1.1D, 1.7D, 0.10D
            );
        }
        level().playSound(null, blockPosition(), SoundEvents.WITHER_SPAWN,
                SoundSource.HOSTILE, 1.8F, 1.25F);
    }

    /** 小樱死亡时不删除熊；让熊进入最终狂暴，副本需两者都结束才结算。 */
    private void notifyToyBearOwnerDeath() {
        if (toyBearUUID == null || !(level() instanceof ServerLevel serverLevel)) return;
        Entity entity = serverLevel.getEntity(toyBearUUID);
        if (entity instanceof ToyBearEntity bear && !bear.isRemoved() && bear.isAlive()) {
            bear.onOwnerSakuraDeath();
        }
    }

    private int currentNormalAttackInterval() {
        return partnerRage ? PARTNER_RAGE_NORMAL_ATTACK_INTERVAL : NORMAL_ATTACK_INTERVAL;
    }

    private int currentSprayCooldown() {
        return partnerRage ? PARTNER_RAGE_SPRAY_CD : SPRAY_CD;
    }

    private int currentFireChargeInterval() {
        return partnerRage ? PARTNER_RAGE_FIRE_CHARGE_INTERVAL : FIRE_CHARGE_INTERVAL;
    }

    private int currentEruptionInterval() {
        return partnerRage ? PARTNER_RAGE_ERUPTION_INTERVAL : ERUPTION_INTERVAL;
    }

    private void removeToyBear() {
        if (toyBearUUID == null || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Entity entity = serverLevel.getEntity(toyBearUUID);
        if (entity instanceof ToyBearEntity bear && !bear.isRemoved()) {
            bear.discard();
        }
        toyBearUUID = null;
    }

    // =========================================================
    // 普通魔法攻击
    // =========================================================

    private void tickNormalMagicAttack() {
        if (entityData.get(SKILL_STATE) != IDLE) {
            return;
        }

        if (entityData.get(ATTACK_TIMER) > 0 || normalAttackTimer > 0) {
            return;
        }

        Player target = getNearestCombatPlayer();
        if (!valid(target)) {
            return;
        }

        /*
         * NetCraft SakuraWitchAttackGoal：
         * - 始终看向当前仇恨目标；
         * - 超过 2.5 格时以 1.0 导航倍率追击；
         * - 进入 2.5 格才停下并开始攻击动画。
         */
        getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (distanceTo(target) > NORMAL_ATTACK_RANGE) {
            getNavigation().moveTo(target, 1.0D);
            return;
        }

        getNavigation().stop();
        lookAt(target.position().add(0.0D, 1.0D, 0.0D));

        entityData.set(ATTACK_INDEX, 1);
        entityData.set(ATTACK_TIMER, ATTACK_LENGTH);
        setDeltaMovement(Vec3.ZERO);

        normalAttackTimer = currentNormalAttackInterval();
        pendingNormalAttackTargetUUID = target.getUUID();
        pendingNormalAttackDamageTicks = NORMAL_DAMAGE_DELAY;

        // NetCraft 的 faceTargetForAttack() 会刷新“攻击动作”时间。
        hatredManager.notifyAttackAction();

        /* 普通魔法攻击视觉。 */
        if (level() instanceof ServerLevel serverLevel) {
            Vec3 start = position().add(0.0D, 1.45D, 0.0D);
            Vec3 direction = target.position()
                    .add(0.0D, 1.0D, 0.0D)
                    .subtract(start)
                    .normalize();

            for (int i = 1; i <= 8; i++) {
                Vec3 pos = start.add(direction.scale(i * 0.65D));
                serverLevel.sendParticles(
                        ModParticles.SAKURA_MAGIC.get(),
                        pos.x, pos.y, pos.z,
                        1,
                        0.03D, 0.03D, 0.03D,
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
                ModSounds.SAKURA_ATT.get(),
                SoundSource.HOSTILE,
                1.2F,
                1.0F
        );
    }

    /**
     * NetCraft：普攻动画开始 15 tick 后才结算；
     * 此时目标如果已经离开 3.5 格，则本次攻击落空。
     */
    private void tickPendingNormalAttackDamage() {
        if (pendingNormalAttackDamageTicks <= 0) {
            return;
        }

        pendingNormalAttackDamageTicks--;
        if (pendingNormalAttackDamageTicks > 0) {
            return;
        }

        UUID targetId = pendingNormalAttackTargetUUID;
        pendingNormalAttackTargetUUID = null;
        if (targetId == null) {
            return;
        }

        Player target = level().getPlayerByUUID(targetId);
        if (!valid(target) || distanceTo(target) > NORMAL_DAMAGE_RANGE) {
            return;
        }

        magicDamage(target, NORMAL_MAGIC_DAMAGE);
        addMagicVulnerability(target);
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
        hatredManager.notifyAttackAction();

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
            sprayCD = currentSprayCooldown();
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
        if (fireChargeTimer < currentFireChargeInterval()) {
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

        // Stargazer 原版：在点名位置铺一张地火 PNG 标记。
        placeEruptionMarker();

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

        hatredManager.notifyAttackAction();
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
                currentEruptionInterval();

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

            // 原版先清除地面 PNG 标记，再结算爆炸。
            removeEruptionMarker();
            explodeEruption();

            eruptionPos = null;

            eruptionTarget = null;

            cancelCurrentSkill();
        }
    }

    /**
     * Stargazer 原版地火预警不是纯粒子，而是 1/16 格高的透明 PNG 地面标记。
     * 只占用空气位置，绝不覆盖副本建筑方块；若脚下位置不可用则尝试上一格。
     */
    private void placeEruptionMarker() {
        removeEruptionMarker();

        if (eruptionPos == null || level().isClientSide) {
            return;
        }

        BlockPos candidate = eruptionPos;
        if (!level().getBlockState(candidate).isAir()) {
            candidate = candidate.above();
        }
        if (!level().getBlockState(candidate).isAir()) {
            return;
        }

        level().setBlock(
                candidate,
                ModBlocks.SAKURA_ERUPTION_MARKER.get().defaultBlockState(),
                3
        );
        eruptionMarkerPos = candidate.immutable();
    }

    private void removeEruptionMarker() {
        if (eruptionMarkerPos == null || level().isClientSide) {
            eruptionMarkerPos = null;
            return;
        }

        if (level().getBlockState(eruptionMarkerPos)
                .is(ModBlocks.SAKURA_ERUPTION_MARKER.get())) {
            level().removeBlock(eruptionMarkerPos, false);
        }
        eruptionMarkerPos = null;
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
        removeEruptionMarker();
        pendingNormalAttackTargetUUID = null;
        pendingNormalAttackDamageTicks = 0;
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
                )
                        * (partnerRage ? PARTNER_RAGE_DAMAGE_MULTIPLIER : 1.0F);

        /*
         * NetCraft 小樱：hurt() 前保存目标速度，伤害后立即恢复。
         * 所以会正常受伤，但不会被这次攻击击退。
         * 你现有的普攻、喷火、爆炸、喷发都走 magicDamage()，
         * 因此统一获得同样的无击退效果。
         */
        Vec3 oldMotion = player.getDeltaMovement();

        player.hurt(
                damageSources().indirectMagic(
                        this,
                        this
                ),
                damage
        );

        player.setDeltaMovement(oldMotion);
        hatredManager.notifyAttackAction();
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
        Player target = hatredManager.getCurrentTarget();
        return valid(target) ? target : null;
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

        float before = getHealth();
        float adjusted = applyNetcraftIncomingDamage(source, amount);
        boolean damaged = super.hurt(source, adjusted);

        if (damaged && !level().isClientSide) {
            float dealt = Math.max(0.0F, before - getHealth());
            Player player = resolvePlayerAttacker(source);
            if (player != null && dealt > 0.0F) {
                hatredManager.addDamageHatred(player, dealt);
            }
        }

        return damaged;
    }

    /** NetCraft 风格：近战/远程/魔法分别减防，再进行 T2 / 固定减伤。 */
    private float applyNetcraftIncomingDamage(DamageSource source, float amount) {
        float damage = Math.max(0.0F, amount);
        DamageClass type = classifyIncomingDamage(source);

        if (type == DamageClass.MELEE) {
            damage *= 1.0F - T2_MELEE_REDUCTION;
            damage -= MELEE_DEFENSE;
        } else if (type == DamageClass.RANGED) {
            damage -= RANGED_DEFENSE;
        } else {
            damage -= MAGIC_DEFENSE;
        }

        damage = Math.max(0.0F, damage);
        damage *= 1.0F - FIXED_DAMAGE_REDUCTION;
        return Math.max(0.1F, damage);
    }

    private DamageClass classifyIncomingDamage(DamageSource source) {
        if (source.getDirectEntity() instanceof Projectile) return DamageClass.RANGED;
        String id = source.getMsgId().toLowerCase(java.util.Locale.ROOT);
        if (id.contains("magic") || id.contains("wither") || id.contains("dragonbreath")
                || id.contains("dragon_breath") || id.contains("sonic")) {
            return DamageClass.MAGIC;
        }
        return DamageClass.MELEE;
    }

    private Player resolvePlayerAttacker(DamageSource source) {
        if (source.getEntity() instanceof Player player) return player;
        if (source.getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof Player player) return player;
        return null;
    }

    private enum DamageClass { MELEE, RANGED, MAGIC }

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
        removeEruptionMarker();

        hatredManager.clearAll();
        notifyToyBearOwnerDeath();

        bossEvent.removeAllPlayers();

        if (!playedDeathSound) {
            playedDeathSound = true;
            level().playSound(
                    null,
                    blockPosition(),
                    ModSounds.SAKURA_END.get(),
                    SoundSource.HOSTILE,
                    2.0F,
                    1.0F
            );
        }
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

        if (netcraftSpawnPositionSet && netcraftSpawnPosition != null) {
            tag.putDouble("SpawnX", netcraftSpawnPosition.x);
            tag.putDouble("SpawnY", netcraftSpawnPosition.y);
            tag.putDouble("SpawnZ", netcraftSpawnPosition.z);
            tag.putBoolean("SpawnPositionSet", true);
        }

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

        tag.putBoolean("PlayedPhaseTwoSound", playedPhaseTwoSound);
        tag.putBoolean("PlayedDeathSound", playedDeathSound);
        tag.putBoolean("ToyBearSummoned", toyBearSummoned);
        tag.putBoolean("PartnerRage", partnerRage);
        if (toyBearUUID != null) {
            tag.putUUID("ToyBearUUID", toyBearUUID);
        }
    }

    // =========================================================
    // NBT 读取
    // =========================================================

    @Override
    public void readAdditionalSaveData(
            CompoundTag tag
    ) {

        super.readAdditionalSaveData(tag);

        if (tag.getBoolean("SpawnPositionSet")) {
            netcraftSpawnPosition = new Vec3(
                    tag.getDouble("SpawnX"),
                    tag.getDouble("SpawnY"),
                    tag.getDouble("SpawnZ")
            );
            netcraftSpawnPositionSet = true;
        }

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

        playedPhaseTwoSound = tag.getBoolean("PlayedPhaseTwoSound");
        playedDeathSound = tag.getBoolean("PlayedDeathSound");
        toyBearSummoned = tag.getBoolean("ToyBearSummoned");
        partnerRage = tag.getBoolean("PartnerRage");
        entityData.set(PARTNER_RAGE, partnerRage);
        toyBearUUID = tag.hasUUID("ToyBearUUID") ? tag.getUUID("ToyBearUUID") : null;
    }

    // =========================================================
    // 属性
    // =========================================================

    public static AttributeSupplier.Builder createAttributes() {

        return PathfinderMob.createMobAttributes()

                .add(
                        Attributes.MAX_HEALTH,
                        200000.0D
                )

                .add(
                        Attributes.ARMOR,
                        10.0D
                )

                .add(
                        Attributes.ATTACK_DAMAGE,
                        110.0D
                )

                .add(
                        Attributes.ATTACK_KNOCKBACK,
                        0.0D
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
