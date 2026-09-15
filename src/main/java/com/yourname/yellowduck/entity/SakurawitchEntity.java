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
import java.util.Iterator;
import java.util.List;

/**
 * 魔女小樱
 *
 * 精英小樱本体技能：
 *
 * P1 100% ~ 80%
 * 1. 普通魔法攻击：攻击最近玩家，并降低魔抗
 * 2. 火焰喷射：30秒一次，随机目标，蓄力8秒，扇形AOE
 *
 * P2 80% ~ 50%
 * 继承P1
 * 3. 火焰蓄能：每2秒获得1层火焰元素
 * 4. 火焰爆炸：10层后全团魔法伤害，5格内队友产生余烬伤害
 *
 * P3 50% ~ 0%
 * 继承P1/P2
 * 5. 火焰喷发：随机点名玩家，每8秒生成一次脚下火焰，
 *    火焰存在5秒后爆发
 */
public class SakurawitchEntity extends PathfinderMob {

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

    private static final int IDLE = 0;
    private static final int SPRAY_CHARGE = 1;

    /*
     * 时间：
     *
     * 20 tick = 1秒
     */
    private static final int NORMAL_ATTACK_INTERVAL = 27;

    private static final int SPRAY_CD = 600;              // 30秒
    private static final int SPRAY_CHARGE_TICKS = 160;    // 8秒
    private static final int SPRAY_CAST_TICKS = 20;       // 1秒

    private static final int FIRE_CHARGE_INTERVAL = 40;  // 2秒

    private static final int ERUPTION_INTERVAL = 160;    // 8秒
    private static final int ERUPTION_DELAY = 100;       // 5秒

    /*
     * 伤害数值先保持在当前版本附近。
     * 后面实际进游戏测试时再单独平衡。
     */
    private static final float NORMAL_MAGIC_DAMAGE = 15.0F;
    private static final float SPRAY_DAMAGE = 30.0F;
    private static final float FIRE_EXPLOSION_DAMAGE = 40.0F;
    private static final float EMBER_DAMAGE = 40.0F;
    private static final float ERUPTION_DAMAGE = 60.0F;

    /*
     * 普攻计时
     */
    private int normalAttackTimer;

    /*
     * 火焰喷射
     */
    private int sprayCD = SPRAY_CD;
    private int sprayTimer;
    private Player sprayTarget;

    /*
     * 火焰蓄能
     */
    private int fireChargeTimer;

    /*
     * 火焰喷发
     *
     * 这里不再使用单独一个 eruptionPos。
     * 每一个火焰都是独立对象，所以8秒一个火焰，
     * 前一个5秒倒计时还没结束也不会影响下一个。
     */
    private final List<EruptionMarker> eruptionMarkers = new ArrayList<>();

    /*
     * 死亡
     */
    private int deathTimer;

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.literal("小樱"),
            BossEvent.BossBarColor.PINK,
            BossEvent.BossBarOverlay.PROGRESS
    );

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
        goalSelector.addGoal(0, new FloatGoal(this));

        /*
         * 小樱不再使用 MeleeAttackGoal。
         *
         * 奶块的小樱普通攻击是魔法攻击，
         * 所以这里让她自己在tick里控制远程魔法攻击。
         */
        goalSelector.addGoal(
                5,
                new WaterAvoidingRandomStrollGoal(this, 0.8D)
        );

        goalSelector.addGoal(
                6,
                new LookAtPlayerGoal(this, Player.class, 24.0F)
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

    // =========================================================
    // Tick
    // =========================================================

    @Override
    public void tick() {
        super.tick();

        /*
         * 客户端只负责模型/基础视觉。
         * 技能逻辑全部服务器执行。
         */
        if (level().isClientSide) {
            return;
        }

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
         * 普通魔法攻击。
         */
        tickNormalMagicAttack();

        /*
         * 火焰喷射。
         */
        tickSpray();

        /*
         * P2开始火焰蓄能。
         */
        tickFireCharge();

        /*
         * P3开始火焰喷发。
         */
        tickEruption();

        /*
         * 处理已经生成的火焰。
         */
        tickEruptionMarkers();
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

        Entity target = getTarget();

        if (!(target instanceof Player player)) {
            return;
        }

        if (!valid(player)) {
            return;
        }

        if (distanceToSqr(player) > 35.0D * 35.0D) {
            return;
        }

        /*
         * 确保小樱面对目标。
         */
        lookAt(
                player.position().add(
                        0,
                        player.getBbHeight() * 0.5D,
                        0
                )
        );

        normalMagicAttack(player);
    }

    private void normalMagicAttack(Player player) {
        if (!valid(player)) {
            return;
        }

        /*
         * 普通魔法攻击。
         */
        magicDamage(player, NORMAL_MAGIC_DAMAGE);

        /*
         * 每次普通魔法攻击降低一次魔抗。
         * 最多10层。
         */
        addMagicVulnerability(player);

        /*
         * 动画：
         * attack_01
         */
        entityData.set(ATTACK_INDEX, 1);
        entityData.set(ATTACK_TIMER, NORMAL_ATTACK_INTERVAL);

        /*
         * 普攻计时重新开始。
         */
        normalAttackTimer = NORMAL_ATTACK_INTERVAL;

        /*
         * 视觉效果。
         */
        if (level() instanceof ServerLevel sl) {

            Vec3 start = position().add(
                    0,
                    getEyeHeight() * 0.8D,
                    0
            );

            Vec3 target = player.position().add(
                    0,
                    player.getBbHeight() * 0.55D,
                    0
            );

            Vec3 direction = target.subtract(start);

            if (direction.lengthSqr() > 0.001D) {
                direction = direction.normalize();

                for (int i = 0; i < 10; i++) {
                    double d = i * 0.8D;

                    sl.sendParticles(
                            ModParticles.SAKURA_MAGIC.get(),
                            start.x + direction.x * d,
                            start.y + direction.y * d,
                            start.z + direction.z * d,
                            1,
                            0,
                            0,
                            0,
                            0
                    );
                }
            }

            sl.sendParticles(
                    ModParticles.SAKURA_MAGIC.get(),
                    player.getX(),
                    player.getY() + 1.0D,
                    player.getZ(),
                    6,
                    0.25D,
                    0.5D,
                    0.25D,
                    0
            );
        }

        level().playSound(
                null,
                blockPosition(),
                SoundEvents.EVOKER_CAST_SPELL,
                SoundSource.HOSTILE,
                1.2F,
                1.15F
        );
    }

    private void addMagicVulnerability(Player player) {
        MobEffectInstance old =
                player.getEffect(ModEffects.MAGIC_VULNERABILITY.get());

        int amplifier;

        if (old == null) {
            amplifier = 0;
        } else {
            amplifier = Math.min(
                    9,
                    old.getAmplifier() + 1
            );
        }

        /*
         * 200 tick = 10秒。
         * 小樱持续攻击时会不断刷新。
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

    private void tickSpray() {

        /*
         * 喷射技能进行中。
         */
        if (entityData.get(SKILL_STATE) == SPRAY_CHARGE) {
            tickSprayCharge();
            return;
        }

        if (sprayCD > 0) {
            sprayCD--;
            return;
        }

        List<Player> players = players(30);

        if (players.isEmpty()) {
            sprayCD = 40;
            return;
        }

        /*
         * 随机点名一个玩家。
         */
        sprayTarget =
                players.get(
                        random.nextInt(players.size())
                );

        sprayTimer = 0;

        entityData.set(
                SKILL_STATE,
                SPRAY_CHARGE
        );

        /*
         * attack_05 是目前GLB里最长的技能动作，
         * 正好接近8秒火焰喷射蓄力。
         */
        entityData.set(
                ATTACK_INDEX,
                5
        );

        entityData.set(
                ATTACK_TIMER,
                SPRAY_CHARGE_TICKS + SPRAY_CAST_TICKS
        );

        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);

        tell(
                sprayTarget,
                "§c⚠ 小樱正在锁定你！8秒后释放火焰喷射！"
        );

        announce(
                "§6🔥 小樱开始蓄力火焰喷射！"
        );

        level().playSound(
                null,
                blockPosition(),
                SoundEvents.BLAZE_AMBIENT,
                SoundSource.HOSTILE,
                2.0F,
                0.65F
        );
    }

    private void tickSprayCharge() {

        sprayTimer++;

        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);

        if (valid(sprayTarget)) {
            lookAt(
                    sprayTarget.position().add(
                            0,
                            sprayTarget.getBbHeight() * 0.5D,
                            0
                    )
            );
        }

        if (!(level() instanceof ServerLevel sl)) {
            return;
        }

        /*
         * 小樱身边聚集魔法火焰。
         */
        for (int i = 0; i < 8; i++) {

            double angle =
                    random.nextDouble() *
                    Math.PI *
                    2.0D;

            double radius =
                    0.7D +
                    random.nextDouble() *
                    1.3D;

            sl.sendParticles(
                    ModParticles.SAKURA_FLAME.get(),
                    getX() + Math.cos(angle) * radius,
                    getY() + 0.2D +
                            random.nextDouble() * 1.2D,
                    getZ() + Math.sin(angle) * radius,
                    1,
                    0,
                    0.02D,
                    0,
                    0
            );
        }

        /*
         * 被点名玩家脚下出现警告。
         */
        if (valid(sprayTarget)) {

            sl.sendParticles(
                    ModParticles.SAKURA_WARNING.get(),
                    sprayTarget.getX(),
                    sprayTarget.getY() + 0.05D,
                    sprayTarget.getZ(),
                    4,
                    0.35D,
                    0.05D,
                    0.35D,
                    0
            );
        }

        /*
         * 8秒蓄力结束。
         */
        if (sprayTimer >= SPRAY_CHARGE_TICKS) {

            castSpray();

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
            sprayTarget = null;

            /*
             * 下一次火焰喷射30秒后。
             */
            sprayCD = SPRAY_CD;
        }
    }

    private void castSpray() {

        if (!(level() instanceof ServerLevel sl)) {
            return;
        }

        Vec3 start =
                position().add(
                        0,
                        getEyeHeight() * 0.75D,
                        0
                );

        Vec3 look =
                getLookAngle().normalize();

        /*
         * 扇形角度。
         *
         * dot >= 0.55
         * 大约对应一个较明显的扇形。
         */
        final double CONE_DOT = 0.55D;
        final double RANGE = 15.0D;

        List<Player> hit =
                new ArrayList<>();

        for (Player player :
                sl.getEntitiesOfClass(
                        Player.class,
                        getBoundingBox().inflate(RANGE)
                )) {

            if (!valid(player)) {
                continue;
            }

            Vec3 target =
                    player.position().add(
                            0,
                            player.getBbHeight() * 0.5D,
                            0
                    );

            Vec3 direction =
                    target.subtract(start);

            double distance =
                    direction.length();

            if (distance <= 0.1D ||
                    distance > RANGE) {
                continue;
            }

            direction =
                    direction.normalize();

            /*
             * 扇形判定。
             */
            if (direction.dot(look) >= CONE_DOT) {
                hit.add(player);
            }
        }

        /*
         * 画出火焰喷射的扇形。
         */
        spawnSprayParticles(
                sl,
                start,
                look
        );

        /*
         * 原版机制：
         * 集中站位可以降低魔法伤害。
         *
         * 这里采用：
         * 如果扇形内有2名及以上玩家，
         * 则本次喷射伤害降低50%。
         */
        float damage =
                hit.size() >= 2
                        ? SPRAY_DAMAGE * 0.5F
                        : SPRAY_DAMAGE;

        for (Player player : hit) {

            magicDamage(
                    player,
                    damage
            );

            /*
             * 被命中时再给一个火焰视觉。
             */
            sl.sendParticles(
                    ModParticles.SAKURA_FLAME.get(),
                    player.getX(),
                    player.getY() + 1.0D,
                    player.getZ(),
                    12,
                    0.35D,
                    0.5D,
                    0.35D,
                    0.02D
            );
        }

        if (valid(sprayTarget) &&
                !hit.contains(sprayTarget)) {

            tell(
                    sprayTarget,
                    "§a✔ 你成功躲开了火焰喷射！"
            );
        }

        level().playSound(
                null,
                blockPosition(),
                SoundEvents.BLAZE_SHOOT,
                SoundSource.HOSTILE,
                2.8F,
                0.65F
        );

        announce(
                "§c🔥 小樱释放了火焰喷射！"
        );
    }

    private void spawnSprayParticles(
            ServerLevel sl,
            Vec3 start,
            Vec3 look
    ) {

        /*
         * 扇形粒子。
         *
         * 这里不是简单的一条直线，
         * 而是把左右两侧逐渐展开。
         */
        Vec3 forward =
                new Vec3(
                        look.x,
                        0,
                        look.z
                );

        if (forward.lengthSqr() < 0.001D) {
            forward = new Vec3(0, 0, 1);
        } else {
            forward = forward.normalize();
        }

        Vec3 right =
                new Vec3(
                        -forward.z,
                        0,
                        forward.x
                );

        for (int distance = 1;
             distance <= 15;
             distance++) {

            double width =
                    distance * 0.48D;

            for (int side = -3;
                 side <= 3;
                 side++) {

                double offset =
                        width * side / 3.0D;

                Vec3 pos =
                        start
                                .add(forward.scale(distance))
                                .add(right.scale(offset));

                sl.sendParticles(
                        ModParticles.SAKURA_FLAME.get(),
                        pos.x,
                        pos.y,
                        pos.z,
                        2,
                        0.08D,
                        0.08D,
                        0.08D,
                        0
                );
            }
        }

        /*
         * 中心魔法闪光。
         */
        sl.sendParticles(
                ModParticles.SAKURA_MAGIC.get(),
                start.x,
                start.y,
                start.z,
                18,
                0.3D,
                0.3D,
                0.3D,
                0.01D
        );
    }

    // =========================================================
    // 火焰蓄能
    // =========================================================

    private void tickFireCharge() {

        /*
         * P2之前没有火焰蓄能。
         */
        if (entityData.get(PHASE) < 2) {
            return;
        }

        fireChargeTimer++;

        /*
         * 每2秒获得1层。
         */
        if (fireChargeTimer >= FIRE_CHARGE_INTERVAL) {

            fireChargeTimer = 0;

            int stacks =
                    entityData.get(FIRE_MARK_STACKS);

            stacks =
                    Math.min(
                            10,
                            stacks + 1
                    );

            entityData.set(
                    FIRE_MARK_STACKS,
                    stacks
            );

            showFireChargeStack(stacks);

            /*
             * 10层：
             * 立即释放全部火焰元素。
             */
            if (stacks >= 10) {

                entityData.set(
                        FIRE_MARK_STACKS,
                        0
                );

                explodeFireCharge();
            }
        }

        /*
         * 火焰元素越多，小樱身边火焰越明显。
         */
        if (level() instanceof ServerLevel sl) {

            int stacks =
                    entityData.get(FIRE_MARK_STACKS);

            if (stacks > 0 &&
                    tickCount % 5 == 0) {

                double radius =
                        1.1D +
                        stacks * 0.10D;

                for (int i = 0;
                     i < stacks + 4;
                     i++) {

                    double angle =
                            random.nextDouble() *
                            Math.PI *
                            2.0D;

                    sl.sendParticles(
                            ModParticles.SAKURA_FLAME.get(),
                            getX() +
                                    Math.cos(angle) * radius,
                            getY() + 0.15D +
                                    random.nextDouble() * 1.2D,
                            getZ() +
                                    Math.sin(angle) * radius,
                            1,
                            0,
                            0.01D,
                            0,
                            0
                    );
                }
            }
        }
    }

    private void showFireChargeStack(int stacks) {

        level().playSound(
                null,
                blockPosition(),
                SoundEvents.FIRECHARGE_USE,
                SoundSource.HOSTILE,
                0.8F,
                0.85F + stacks * 0.03F
        );

        announce(
                "§6🔥 小樱火焰元素：§e"
                        + stacks
                        + "§6/10"
        );

        if (level() instanceof ServerLevel sl) {

            sl.sendParticles(
                    ModParticles.SAKURA_MAGIC.get(),
                    getX(),
                    getY() + 1.0D,
                    getZ(),
                    4 + stacks,
                    0.5D,
                    0.7D,
                    0.5D,
                    0.01D
            );
        }
    }

    // =========================================================
    // 火焰爆炸
    // =========================================================

    private void explodeFireCharge() {

        if (!(level() instanceof ServerLevel sl)) {
            return;
        }

        /*
         * attack_03：
         * 这里作为短时间的火焰爆炸动作。
         */
        entityData.set(
                ATTACK_INDEX,
                3
        );

        entityData.set(
                ATTACK_TIMER,
                27
        );

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

        /*
         * 爆炸中心。
         */
        sl.sendParticles(
                ModParticles.SAKURA_EXPLOSION.get(),
                getX(),
                getY() + 1.0D,
                getZ(),
                1,
                1.0D,
                0.5D,
                1.0D,
                0
        );

        /*
         * 第二圈魔法火花。
         */
        sl.sendParticles(
                ModParticles.SAKURA_MAGIC.get(),
                getX(),
                getY() + 1.0D,
                getZ(),
                40,
                2.0D,
                1.0D,
                2.0D,
                0.04D
        );

        List<Player> players =
                players(30);

        for (Player player : players) {

            /*
             * 全团基础魔法伤害。
             */
            magicDamage(
                    player,
                    FIRE_EXPLOSION_DAMAGE
            );

            /*
             * 5格内队友判定。
             */
            int nearby =
                    0;

            for (Player other : players) {

                if (other == player) {
                    continue;
                }

                if (player.distanceToSqr(other) <= 25.0D) {
                    nearby++;
                }
            }

            /*
             * 每一个5格内队友都会增加余烬伤害。
             */
            if (nearby > 0) {

                magicDamage(
                        player,
                        EMBER_DAMAGE * nearby
                );

                tell(
                        player,
                        "§c⚠ 你与队友距离过近，受到额外余烬伤害！"
                );

                sl.sendParticles(
                        ModParticles.SAKURA_WARNING.get(),
                        player.getX(),
                        player.getY() + 0.1D,
                        player.getZ(),
                        12,
                        0.5D,
                        0.1D,
                        0.5D,
                        0
                );
            }
        }
    }

    // =========================================================
    // 火焰喷发：每8秒点名
    // =========================================================

    private void tickEruption() {

        if (entityData.get(PHASE) < 3) {
            return;
        }

        /*
         * 每160tick = 8秒生成一个新的火焰。
         *
         * 这个计时器直接使用tickCount，
         * 避免因为上一个火焰还在倒计时而卡住。
         */
        if (tickCount % ERUPTION_INTERVAL != 0) {
            return;
        }

        List<Player> players =
                players(30);

        if (players.isEmpty()) {
            return;
        }

        /*
         * 随机点名。
         */
        Player target =
                players.get(
                        random.nextInt(players.size())
                );

        BlockPos pos =
                BlockPos.containing(
                        target.getX(),
                        target.getY(),
                        target.getZ()
                );

        EruptionMarker marker =
                new EruptionMarker(
                        pos,
                        ERUPTION_DELAY
                );

        eruptionMarkers.add(marker);

        /*
         * 使用 attack_06 作为火焰喷发视觉动作。
         */
        entityData.set(
                ATTACK_INDEX,
                6
        );

        entityData.set(
                ATTACK_TIMER,
                24
        );

        tell(
                target,
                "§c⚠ 烈焰标记锁定了你！§e5秒后§c爆发，快离开脚下！"
        );

        announce(
                "§c🔥 小樱释放火焰喷发！"
        );

        level().playSound(
                null,
                pos,
                SoundEvents.FIRECHARGE_USE,
                SoundSource.HOSTILE,
                1.5F,
                0.75F
        );
    }

    // =========================================================
    // 火焰喷发倒计时
    // =========================================================

    private void tickEruptionMarkers() {

        if (eruptionMarkers.isEmpty()) {
            return;
        }

        Iterator<EruptionMarker> iterator =
                eruptionMarkers.iterator();

        while (iterator.hasNext()) {

            EruptionMarker marker =
                    iterator.next();

            /*
             * 倒计时。
             */
            marker.ticksRemaining--;

            spawnEruptionWarning(marker);

            /*
             * 0：
             * 爆炸。
             */
            if (marker.ticksRemaining <= 0) {

                explodeEruption(marker);

                iterator.remove();
            }
        }
    }

    private void spawnEruptionWarning(
            EruptionMarker marker
    ) {

        if (!(level() instanceof ServerLevel sl)) {
            return;
        }

        BlockPos pos =
                marker.pos;

        /*
         * 外圈火焰。
         */
        for (int i = 0;
             i < 10;
             i++) {

            double angle =
                    random.nextDouble() *
                    Math.PI *
                    2.0D;

            double radius =
                    0.7D +
                    random.nextDouble() * 1.4D;

            sl.sendParticles(
                    ModParticles.SAKURA_WARNING.get(),
                    pos.getX() + 0.5D +
                            Math.cos(angle) * radius,
                    pos.getY() + 0.08D,
                    pos.getZ() + 0.5D +
                            Math.sin(angle) * radius,
                    1,
                    0,
                    0,
                    0,
                    0
            );
        }

        /*
         * 中心火焰。
         */
        int intensity =
                marker.ticksRemaining <= 40
                        ? 8
                        : 3;

        sl.sendParticles(
                ModParticles.SAKURA_FLAME.get(),
                pos.getX() + 0.5D,
                pos.getY() + 0.15D,
                pos.getZ() + 0.5D,
                intensity,
                0.35D,
                0.08D,
                0.35D,
                0.01D
        );

        /*
         * 最后2秒加警告。
         */
        if (marker.ticksRemaining <= 40 &&
                tickCount % 5 == 0) {

            sl.sendParticles(
                    ModParticles.SAKURA_MAGIC.get(),
                    pos.getX() + 0.5D,
                    pos.getY() + 0.4D,
                    pos.getZ() + 0.5D,
                    6,
                    0.45D,
                    0.4D,
                    0.45D,
                    0.02D
            );
        }
    }

    private void explodeEruption(
            EruptionMarker marker
    ) {

        if (!(level() instanceof ServerLevel sl)) {
            return;
        }

        BlockPos pos =
                marker.pos;

        level().playSound(
                null,
                pos,
                SoundEvents.GENERIC_EXPLODE,
                SoundSource.HOSTILE,
                2.2F,
                0.9F
        );

        /*
         * 爆炸贴图。
         */
        sl.sendParticles(
                ModParticles.SAKURA_ERUPTION.get(),
                pos.getX() + 0.5D,
                pos.getY() + 0.4D,
                pos.getZ() + 0.5D,
                1,
                0,
                0,
                0,
                0
        );

        /*
         * 魔法爆炸光。
         */
        sl.sendParticles(
                ModParticles.SAKURA_EXPLOSION.get(),
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D,
                1,
                0.5D,
                0.3D,
                0.5D,
                0
        );

        /*
         * 3格范围。
         */
        AABB area =
                new AABB(pos).inflate(3.0D);

        for (Player player :
                sl.getEntitiesOfClass(
                        Player.class,
                        area
                )) {

            if (!valid(player)) {
                continue;
            }

            double dx =
                    player.getX() -
                    (pos.getX() + 0.5D);

            double dz =
                    player.getZ() -
                    (pos.getZ() + 0.5D);

            if (dx * dx + dz * dz <= 9.0D) {

                magicDamage(
                        player,
                        ERUPTION_DAMAGE
                );
            }
        }
    }

    // =========================================================
    // 阶段
    // =========================================================

    private void updatePhase() {

        float ratio =
                getHealth() /
                getMaxHealth();

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
         * 阶段切换时清掉当前技能状态，
         * 防止切阶段卡技能。
         */
        cancelSkill();

        /*
         * 进入P2时重新从0开始蓄能。
         */
        if (nextPhase == 2) {
            fireChargeTimer = 0;

            entityData.set(
                    FIRE_MARK_STACKS,
                    0
            );
        }

        /*
         * P3开始时不清掉已有火焰，
         * 但重新让喷发按8秒节奏开始。
         */
        if (nextPhase == 3) {
            eruptionMarkers.clear();
        }

        level().playSound(
                null,
                blockPosition(),
                nextPhase == 2
                        ? SoundEvents.BLAZE_SHOOT
                        : SoundEvents.WITHER_SPAWN,
                SoundSource.HOSTILE,
                2.0F,
                nextPhase == 2
                        ? 0.6F
                        : 1.1F
        );

        announce(
                "§c⚠ 小樱进入第"
                        + nextPhase
                        + "阶段！"
        );

        if (level() instanceof ServerLevel sl) {

            sl.sendParticles(
                    ModParticles.SAKURA_MAGIC.get(),
                    getX(),
                    getY() + 1.0D,
                    getZ(),
                    30,
                    1.5D,
                    1.0D,
                    1.5D,
                    0.03D
            );

            sl.sendParticles(
                    ModParticles.SAKURA_FLAME.get(),
                    getX(),
                    getY() + 1.0D,
                    getZ(),
                    30,
                    1.5D,
                    1.0D,
                    1.5D,
                    0.03D
            );
        }
    }

    // =========================================================
    // Boss血条
    // =========================================================

    private void updateBossBar() {

        float hp =
                Math.max(
                        0.0F,
                        Math.min(
                                1.0F,
                                getHealth() /
                                        getMaxHealth()
                        )
                );

        bossEvent.setProgress(hp);

        int phase =
                entityData.get(PHASE);

        bossEvent.setColor(
                phase == 1
                        ? BossEvent.BossBarColor.GREEN
                        : phase == 2
                        ? BossEvent.BossBarColor.YELLOW
                        : BossEvent.BossBarColor.RED
        );
    }

    // =========================================================
    // 目标
    // =========================================================

    private void updateTarget() {

        if (tickCount % 20 != 0) {
            return;
        }

        Entity current =
                getTarget();

        if (current instanceof Player player &&
                valid(player) &&
                distanceToSqr(player) <= 35.0D * 35.0D) {
            return;
        }

        Player nearest =
                level().getNearestPlayer(
                        this,
                        35
                );

        if (valid(nearest)) {
            setTarget(nearest);
        }
    }

    // =========================================================
    // 动画状态
    // =========================================================

    private void updateWalking() {

        double dx =
                getX() - xo;

        double dz =
                getZ() - zo;

        boolean moving =
                dx * dx + dz * dz > 1.0E-5D;

        /*
         * 技能期间不播放走路。
         */
        if (entityData.get(SKILL_STATE) != IDLE) {
            moving = false;
        }

        if (entityData.get(ATTACK_TIMER) > 0) {
            moving = false;
        }

        entityData.set(
                IS_WALKING,
                moving
        );
    }

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

        if (timer == 0) {

            entityData.set(
                    ATTACK_INDEX,
                    0
            );
        }
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

        int stacks =
                effect == null
                        ? 0
                        : Math.min(
                                10,
                                effect.getAmplifier() + 1
                        );

        /*
         * 魔抗削弱越高，
         * 小樱的魔法伤害越危险。
         */
        float damage =
                baseDamage *
                (1.0F + 0.05F * stacks);

        player.hurt(
                damageSources().indirectMagic(
                        this,
                        this
                ),
                damage
        );
    }

    // =========================================================
    // 工具
    // =========================================================

    private List<Player> players(
            double radius
    ) {

        List<Player> players =
                level().getEntitiesOfClass(
                        Player.class,
                        getBoundingBox().inflate(radius)
                );

        players.removeIf(
                player -> !valid(player)
        );

        return players;
    }

    private boolean valid(Player player) {

        return player != null
                && player.isAlive()
                && !player.isRemoved()
                && !player.isCreative()
                && !player.isSpectator();
    }

    private void tell(
            Player player,
            String message
    ) {

        if (player instanceof ServerPlayer serverPlayer) {

            serverPlayer.displayClientMessage(
                    Component.literal(message),
                    true
            );
        }
    }

    private void announce(
            String message
    ) {

        if (!(level() instanceof ServerLevel sl)) {
            return;
        }

        for (ServerPlayer player :
                sl.getEntitiesOfClass(
                        ServerPlayer.class,
                        getBoundingBox().inflate(35)
                )) {

            if (player.isAlive() &&
                    !player.isSpectator()) {

                player.displayClientMessage(
                        Component.literal(message),
                        true
                );
            }
        }
    }

    private void lookAt(Vec3 position) {

        Vec3 direction =
                position.subtract(
                        getX(),
                        getY() + getEyeHeight(),
                        getZ()
                );

        double horizontal =
                Math.sqrt(
                        direction.x * direction.x +
                        direction.z * direction.z
                );

        float yaw =
                (float) Math.toDegrees(
                        Math.atan2(
                                -direction.x,
                                direction.z
                        )
                );

        float pitch =
                (float) -Math.toDegrees(
                        Math.atan2(
                                direction.y,
                                horizontal
                        )
                );

        setYRot(yaw);
        setXRot(pitch);

        yHeadRot = yaw;
        yBodyRot = yaw;
    }

    private void cancelSkill() {

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
        sprayTarget = null;
    }

    // =========================================================
    // 受伤 / 死亡
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

        eruptionMarkers.clear();

        setInvulnerable(true);
        setHealth(0.0F);

        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);

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

    private void tickSakuraDeath() {

        setInvulnerable(true);

        setDeltaMovement(Vec3.ZERO);

        getNavigation().stop();

        deathTimer++;

        if (level() instanceof ServerLevel sl &&
                deathTimer % 3 == 0) {

            sl.sendParticles(
                    ModParticles.SAKURA_FLAME.get(),
                    getX(),
                    getY() + 1.0D,
                    getZ(),
                    8,
                    0.8D,
                    0.8D,
                    0.8D,
                    0.03D
            );

            sl.sendParticles(
                    ModParticles.SAKURA_PETAL.get(),
                    getX(),
                    getY() + 1.0D,
                    getZ(),
                    5,
                    0.7D,
                    0.8D,
                    0.7D,
                    0.02D
            );
        }

        if (deathTimer >= 35) {

            setInvulnerable(false);

            super.die(
                    damageSources().generic()
            );
        }
    }

    @Override
    public boolean removeWhenFarAway(
            double distance
    ) {
        return false;
    }

    // =========================================================
    // 存档
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
                "NormalAttackTimer",
                normalAttackTimer
        );

        tag.putInt(
                "FireMarkStacks",
                entityData.get(FIRE_MARK_STACKS)
        );

        tag.putInt(
                "Phase",
                entityData.get(PHASE)
        );
    }

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

    // =========================================================
    // 火焰喷发数据
    // =========================================================

    private static class EruptionMarker {

        private final BlockPos pos;
        private int ticksRemaining;

        private EruptionMarker(
                BlockPos pos,
                int ticksRemaining
        ) {
            this.pos = pos;
            this.ticksRemaining = ticksRemaining;
        }
    }
}
