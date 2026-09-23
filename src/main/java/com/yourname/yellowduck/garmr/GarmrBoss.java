package com.yourname.yellowduck.garmr;

import com.yourname.yellowduck.boss.NetcraftBossBase;
import com.yourname.yellowduck.config.EntityTuningConfig;
import com.yourname.yellowduck.dungeon.DungeonManager;
import com.yourname.yellowduck.particle.ModParticles;
import com.yourname.yellowduck.registry.ModSounds;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 恐惧之地：地狱双头犬·加姆。
 *
 * V5 以用户提供的“4.1.6.3 恐惧之地”规则表为主要依据，并继续替换已解析的 NetCraft 原模型：
 * 1) 生成时必须先正常站在地面；
 * 2) 第一次被玩家击中后才起飞并进入 P1 无敌；
 * 3) 起飞结束后共生成 4 只怪：1 个熔岩卫士 + 3 只骷髅射手；
 * 4) P2/P3 的冰火幽灵、10 秒轮流吐息、阿努比斯守护/献祭、小恶魔爆炸按表执行。
 *
 * V6 已换入：熔岩守卫、阿努比斯、小恶魔、骷髅射手、骷髅守卫、冰/火亡灵夫人均使用独立辅助实体与原模型。
 */
public final class GarmrBoss extends NetcraftBossBase {
    public static final EntityDataAccessor<Integer> PHASE =
            SynchedEntityData.defineId(GarmrBoss.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> ACTION =
            SynchedEntityData.defineId(GarmrBoss.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> ACTION_SERIAL =
            SynchedEntityData.defineId(GarmrBoss.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> ACTION_START_TICK =
            SynchedEntityData.defineId(GarmrBoss.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> BREATH_TYPE =
            SynchedEntityData.defineId(GarmrBoss.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> AIRBORNE =
            SynchedEntityData.defineId(GarmrBoss.class, EntityDataSerializers.BOOLEAN);

    public static final int P1_GROUND = 0;
    public static final int P1_TAKEOFF = 1;
    public static final int P1_WAVE = 2;
    public static final int P1_LANDING = 3;
    public static final int P2 = 4;
    public static final int P3 = 5;

    public static final int ACT_IDLE = 0;
    public static final int ACT_BASIC = 1;
    public static final int ACT_FIRE_BREATH = 2;
    public static final int ACT_ICE_BREATH = 3;
    public static final int ACT_RANGED = 4;
    public static final int ACT_LANDING = 5;
    public static final int ACT_DEATH = 6;
    public static final int ACT_TAKEOFF = 7;

    public static final int BREATH_NONE = 0;
    public static final int BREATH_FIRE = 1;
    public static final int BREATH_ICE = 2;

    static final String TAG_ROLE = "GarmrRole";
    static final String TAG_OWNER = "GarmrOwner";
    static final String TAG_DEFENSE = "GarmrDefense";
    static final String TAG_ATTACK_LEVEL = "GarmrAttackLevel";
    static final String TAG_DEFENSE_LEVEL = "GarmrDefenseLevel";
    static final String TAG_LADY_TYPE = "GarmrLadyType";
    static final String TAG_LADY_SPAWN_TICK = "GarmrLadySpawnTick";

    static final String ROLE_CORE_ADD = "p1_core_add_placeholder";
    static final String ROLE_P1_SKELETON = "p1_skeleton_netcraft";
    static final String ROLE_LADY = "lady_netcraft";
    static final String ROLE_ANUBIS = "anubis_netcraft";
    static final String ROLE_DEVIL = "devil_netcraft";
    static final String ROLE_DEATH_GUARD = "death_guard_netcraft";

    private final Map<UUID, Integer> curseStacks = new HashMap<>();
    private final Map<UUID, Integer> ladies = new HashMap<>(); // UUID -> BREATH_FIRE / BREATH_ICE
    private final List<UUID> p1SkeletonIds = new ArrayList<>();
    private final List<UUID> devils = new ArrayList<>();

    private boolean initialized;
    private double homeX;
    private double homeY;
    private double homeZ;

    private int takeoffAge;
    private int landingAge;
    private boolean p1WaveStarted;
    private UUID coreAddId;
    private int nextP1Projectile;
    private float lastObservedHealth;

    private int nextBasicAttack;
    private int nextBreath;
    private int nextBreathType = BREATH_ICE;
    private int breathAge;
    private int breathType;
    private int actionUntilTick;

    private int nextLady;
    private int nextLadyType = BREATH_ICE;

    private UUID anubisId;
    private boolean anubisLost;
    private int nextAnubisAction;

    // P1：祝福 +100% 伤害，30 秒。
    private UUID blessingTargetId;
    private int blessingUntilTick;

    // P2：守护 15 秒；P3：分身 30 秒。carrierId 即当前白圈/分身玩家。
    private UUID carrierId;
    private int protectionUntilTick;

    // P3 献祭 -> 5 秒后分身。
    private UUID pendingCloneTargetId;
    private int cloneReadyTick;

    private int nextDevil;

    public GarmrBoss(EntityType<? extends GarmrBoss> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setBaseTier(GarmrConfig.BOSS_ATTACK_LEVEL);
        setBaseDamage((int) GarmrConfig.BASIC_DAMAGE);
        setBaseDefense(GarmrConfig.BOSS_DEFENSE_LEVEL);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, GarmrConfig.BOSS_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ATTACK_DAMAGE, GarmrConfig.BASIC_DAMAGE);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PHASE, P1_GROUND);
        entityData.define(ACTION, ACT_IDLE);
        entityData.define(ACTION_SERIAL, 0);
        entityData.define(ACTION_START_TICK, 0);
        entityData.define(BREATH_TYPE, BREATH_NONE);
        entityData.define(AIRBORNE, false);
    }

    @Override
    protected void registerGoals() {
        // 定点 Boss：不注册寻路/追击 Goal。
    }

    @Override public Component getName() { return Component.literal("地狱双头犬·加姆"); }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override
    public void setTarget(LivingEntity target) {
        // 加姆只允许把玩家写入 vanilla target；召唤物、动物和其他生物不能触发 Boss 仇恨。
        super.setTarget(target instanceof Player ? target : null);
    }
    @Override public boolean isPushable() { return false; }
    @Override public void push(Entity entity) { }
    @Override public void push(double x, double y, double z) { }
    @Override public void knockback(double strength, double x, double z) { }
    @Override public int getMeleeDefense() { return GarmrConfig.MELEE_DEFENSE; }
    @Override public int getRangedDefense() { return GarmrConfig.RANGED_DEFENSE; }
    @Override public int getMagicDefense() { return GarmrConfig.MAGIC_DEFENSE; }
    @Override public float getDamageReductionRatio() { return GarmrConfig.DAMAGE_REDUCTION; }
    @Override public boolean isPlayingAttackAnimation() { return entityData.get(ACTION) != ACT_IDLE; }
    @Override public boolean shouldDisengageOnDistance() { return false; }
    @Override public boolean shouldDisengageOnLowHatred() { return false; }
    @Override public boolean shouldDisengageOnAttackTimeout() { return false; }

    public int visualAction() { return entityData.get(ACTION); }
    public int visualActionStartTick() { return entityData.get(ACTION_START_TICK); }
    public boolean isVisualAirborne() { return entityData.get(AIRBORNE); }

    public boolean isParticipant(ServerPlayer player) {
        if (player == null || !isValidHatredPlayer(player)) return false;
        if (getPersistentData().hasUUID("YellowDuckDungeon")) {
            var instance = DungeonManager.instanceOf(player);
            return instance != null && instance.id.equals(getPersistentData().getUUID("YellowDuckDungeon"));
        }
        return player.distanceToSqr(homeX, homeY, homeZ) <= 48.0D * 48.0D;
    }

    public List<ServerPlayer> participants() {
        if (!(level() instanceof ServerLevel server)) return List.of();
        List<ServerPlayer> result = new ArrayList<>();
        for (ServerPlayer player : server.players()) {
            if (isParticipant(player)) result.add(player);
        }
        return result;
    }

    public int curseStacks(UUID player) {
        return curseStacks.getOrDefault(player, 0);
    }

    public boolean isBlessed(ServerPlayer player) {
        return player != null
                && blessingTargetId != null
                && blessingTargetId.equals(player.getUUID())
                && tickCount < blessingUntilTick;
    }

    public boolean hasAnubisProtection(ServerPlayer player) {
        return player != null
                && carrierId != null
                && carrierId.equals(player.getUUID())
                && tickCount < protectionUntilTick;
    }

    public boolean damageNoKnockback(LivingEntity target, float damage) {
        return hurtWithoutKnockback(target, damageSources().mobAttack(this), damage);
    }

    /** 按最大生命百分比造成伤害，但保持玩家当前速度不被击退。 */
    private boolean damagePercentNoKnockback(ServerPlayer player, float ratio) {
        if (player == null || !player.isAlive()) return false;
        Vec3 motion = player.getDeltaMovement();
        boolean result = player.hurt(player.damageSources().fellOutOfWorld(), player.getMaxHealth() * ratio);
        player.setDeltaMovement(motion);
        return result;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide) {
            if (!initialized) initializeEncounter();
            int phase = entityData.get(PHASE);
            if (phase <= P1_LANDING) {
                // V5：出生先在地面；任意玩家第一次打中才开始起飞。首击只作为机制触发，不扣血。
                if (phase == P1_GROUND && source.getEntity() instanceof ServerPlayer player && isParticipant(player)) {
                    getHatredManager().addRawHatred(player, 10.0D);
                    beginTakeoff();
                }
                return false;
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !isAlive()) return;
        if (!(level() instanceof ServerLevel server)) return;

        if (!initialized) initializeEncounter();
        int currentPhase = entityData.get(PHASE);
        boolean atFullHealth = getHealth() >= getMaxHealth() - 0.5F;
        // P1_TAKEOFF 是首击后、Boss 仍满血的正常过渡，不能在这里被误判为回血重置。
        if (currentPhase >= P1_WAVE
                && lastObservedHealth > 0.0F
                && (getHealth() > lastObservedHealth + 0.5F
                || (currentPhase >= P2 && atFullHealth))) {
            // 回满血只重置 Boss 战斗阶段和阶段召唤物；阿努比斯由 Boss 持有，回血时必须保留。
            resetForNewFight(server);
        }
        lastObservedHealth = getHealth();
        lockHorizontalPosition();
        cleanupCurseOwners();
        expireTimedAction();
        tickCurse();
        tickAnubis(server); // 阿努比斯从 P1 到 P3 全程协战。

        int phase = entityData.get(PHASE);
        if (phase == P1_GROUND) {
            // 等待首击。保持地面 idle。
            return;
        }
        if (phase == P1_TAKEOFF) {
            tickTakeoff();
            return;
        }
        if (phase == P1_WAVE) {
            tickP1(server);
            return;
        }
        if (phase == P1_LANDING) {
            tickLanding();
            return;
        }

        if (phase == P2 && getHealth() / Math.max(1.0F, getMaxHealth()) < GarmrConfig.PHASE_THREE_HEALTH) {
            enterP3();
            phase = P3;
        }

        tickLadies(server);
        tickBreathAndBasic(server);
        if (phase == P3) tickDevils(server);
    }

    private void initializeEncounter() {
        initialized = true;
        Vec3 spawn = getSpawnPosition() != null ? getSpawnPosition() : position();
        homeX = spawn.x;
        homeY = spawn.y;
        homeZ = spawn.z;

        // 修复视频里的“生成即飞天”：初始保持正常重力与地面高度。
        setNoGravity(false);
        setPos(homeX, homeY, homeZ);
        setDeltaMovement(Vec3.ZERO);
        entityData.set(AIRBORNE, false);
        entityData.set(PHASE, P1_GROUND);
        setIdleAction();

        spawnAnubis();
        nextAnubisAction = tickCount + 20; // P1 第一次祝福尽快出现，之后按 30 秒持续轮转。
        lastObservedHealth = getHealth();
    }

    private void lockHorizontalPosition() {
        if (!initialized) return;
        double y = getY();
        setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
        double dx = getX() - homeX;
        double dz = getZ() - homeZ;
        if (dx * dx + dz * dz > 0.12D) setPos(homeX, y, homeZ);
        getNavigation().stop();
    }

    private void beginTakeoff() {
        if (entityData.get(PHASE) != P1_GROUND) return;
        entityData.set(PHASE, P1_TAKEOFF);
        entityData.set(AIRBORNE, true);
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        takeoffAge = 0;
        actionUntilTick = 0;
        setAction(ACT_TAKEOFF);
    }

    private void tickTakeoff() {
        takeoffAge++;
        double progress = Math.min(1.0D, takeoffAge / (double) Math.max(1, GarmrConfig.TAKEOFF_TICKS));
        double smooth = progress * progress * (3.0D - 2.0D * progress);
        setPos(homeX, Mth.lerp(smooth, homeY, homeY + GarmrConfig.AIR_HEIGHT), homeZ);
        if (progress >= 1.0D) {
            setIdleAction();
            startP1Wave();
        }
    }

    private void startP1Wave() {
        if (p1WaveStarted || !(level() instanceof ServerLevel server)) return;
        p1WaveStarted = true;
        entityData.set(PHASE, P1_WAVE);
        nextP1Projectile = tickCount + GarmrConfig.P1_PROJECTILE_INTERVAL_TICKS;

        // 熔岩守卫使用用户提供的原始 GLB；实体本身保持近战 AI，并通过统一事件结算固定伤害。
        GarmrHelperEntity core = GarmrContent.HELPER.get().create(server);
        if (core != null) {
            core.setVariant(GarmrHelperEntity.LAVA_GUARD);
            core.moveTo(homeX, homeY, homeZ - 7.0D, 0F, 0F);
            core.setPersistenceRequired();
            core.setNoAi(false);
            tagHelper(core, ROLE_CORE_ADD);
            applyMobStats(core, GarmrConfig.LAVA_GUARD_HEALTH, GarmrConfig.LAVA_GUARD_ATTACK,
                    GarmrConfig.LAVA_GUARD_DEFENSE,
                    GarmrConfig.LAVA_GUARD_ATTACK_LEVEL, GarmrConfig.LAVA_GUARD_DEFENSE_LEVEL);
            if (server.addFreshEntity(core)) coreAddId = core.getUUID();
        }

        // 图片只写“熔岩卫士四周召唤骷髅射手”；用户本轮明确“召唤四只怪”，因此按 1 卫士 + 3 射手，共 4 只执行。
        p1SkeletonIds.clear();
        for (int i = 0; i < GarmrConfig.P1_ARCHER_COUNT; i++) {
            double angle = Math.PI * 2.0D * i / GarmrConfig.P1_ARCHER_COUNT;
            GarmrHelperEntity skeleton = GarmrContent.HELPER.get().create(server);
            if (skeleton == null) continue;
            skeleton.setVariant(GarmrHelperEntity.P1_ARCHER);
            skeleton.moveTo(homeX + Math.cos(angle) * 8.0D, homeY,
                    homeZ - 7.0D + Math.sin(angle) * 8.0D, 0F, 0F);
            skeleton.setNoAi(true); // 射击由 Boss 的服务器权威 P1 投射物逻辑统一结算。
            skeleton.setPersistenceRequired();
            tagHelper(skeleton, ROLE_P1_SKELETON);
            applyMobStats(skeleton, GarmrConfig.P1_ARCHER_HEALTH, GarmrConfig.P1_ARCHER_ATTACK,
                    GarmrConfig.P1_ARCHER_DEFENSE,
                    GarmrConfig.P1_ARCHER_ATTACK_LEVEL, GarmrConfig.P1_ARCHER_DEFENSE_LEVEL);
            if (server.addFreshEntity(skeleton)) p1SkeletonIds.add(skeleton.getUUID());
        }

    }

    private void tickP1(ServerLevel server) {
        Entity core = coreAddId == null ? null : server.getEntity(coreAddId);
        if (coreAddId != null && (core == null || !core.isAlive())) {
            beginLanding(server);
            return;
        }

        // 熔岩卫士始终攻击当前最高仇恨玩家。
        if (core instanceof GarmrHelperEntity guard && guard.isAlive()) {
            guard.clearFire();
            ServerPlayer target = highestHatredTarget();
            if (target != null) guard.setTarget(target);
        }

        if (tickCount >= nextP1Projectile) {
            nextP1Projectile = tickCount + GarmrConfig.P1_PROJECTILE_INTERVAL_TICKS;
            List<ServerPlayer> targets = shuffledParticipants();
            if (!targets.isEmpty()) {
                int shotCount = Math.min(GarmrConfig.P1_PROJECTILE_TARGET_COUNT, targets.size());
                for (int i = 0; i < p1SkeletonIds.size(); i++) {
                    Entity shooter = server.getEntity(p1SkeletonIds.get(i));
                    if (!(shooter instanceof GarmrHelperEntity archer) || !archer.isAlive()) continue;
                    ServerPlayer target = targets.get(i % shotCount);
                    fireProjectileFrom(archer, target, GarmrConfig.P1_AOE_DAMAGE);
                }
            }
        }
    }

    private void beginLanding(ServerLevel server) {
        entityData.set(PHASE, P1_LANDING);
        actionUntilTick = 0;
        setAction(ACT_LANDING);
        landingAge = 0;
        float targetHealth = getMaxHealth() * GarmrConfig.PHASE_TWO_HEALTH;
        setHealth(Math.min(getHealth(), targetHealth));

        // P1 结束时清掉仍残留的弓手，避免把 P1 小怪带进 P2。
        for (UUID id : p1SkeletonIds) {
            Entity entity = server.getEntity(id);
            if (entity != null && entity.isAlive()) entity.discard();
        }
        p1SkeletonIds.clear();
    }

    private void tickLanding() {
        landingAge++;
        double startY = homeY + GarmrConfig.AIR_HEIGHT;
        double progress = Math.min(1.0D, landingAge / (double) Math.max(1, GarmrConfig.LANDING_TICKS));
        double smooth = progress * progress * (3.0D - 2.0D * progress);
        setPos(homeX, Mth.lerp(smooth, startY, homeY), homeZ);
        if (progress >= 1.0D) enterP2();
    }

    private void enterP2() {
        entityData.set(PHASE, P2);
        entityData.set(AIRBORNE, false);
        setNoGravity(false);
        setPos(homeX, homeY, homeZ);
        setDeltaMovement(Vec3.ZERO);
        actionUntilTick = 0;
        setIdleAction();

        blessingTargetId = null;
        blessingUntilTick = 0;
        carrierId = null;
        protectionUntilTick = 0;
        pendingCloneTargetId = null;
        cloneReadyTick = 0;

        nextBasicAttack = tickCount + 30;
        nextBreath = tickCount + GarmrConfig.BREATH_INTERVAL_TICKS;
        nextBreathType = BREATH_ICE;
        nextLady = tickCount + GarmrConfig.LADY_INTERVAL_TICKS;
        nextLadyType = BREATH_ICE;
        nextAnubisAction = tickCount + GarmrConfig.ANUBIS_GUARD_INTERVAL_TICKS;
    }

    private void enterP3() {
        entityData.set(PHASE, P3);
        carrierId = null;
        protectionUntilTick = 0;
        pendingCloneTargetId = null;
        cloneReadyTick = 0;
        nextAnubisAction = tickCount + GarmrConfig.ANUBIS_SACRIFICE_INTERVAL_TICKS;
        nextDevil = tickCount + GarmrConfig.DEVIL_INTERVAL_TICKS;
    }

    private void tickCurse() {
        if (entityData.get(PHASE) == P1_GROUND) return;
        if (tickCount % GarmrConfig.CURSE_INTERVAL_TICKS != 0) return;
        for (ServerPlayer player : participants()) {
            int stacks = curseStacks.merge(player.getUUID(), 1, Integer::sum);
            // 只在玩家状态栏显示图标和层数，不再发送 actionbar/聊天提示。
            player.removeEffect(com.yourname.yellowduck.registry.ModEffects.GARMR_DEATH_CURSE.get());
            player.addEffect(new MobEffectInstance(
                    com.yourname.yellowduck.registry.ModEffects.GARMR_DEATH_CURSE.get(),
                    GarmrConfig.CURSE_INTERVAL_TICKS + 40,
                    Math.max(0, stacks - 1), false, false, true));
            if (stacks >= GarmrConfig.CURSE_KILL_STACKS) {
                player.hurt(player.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
            }
        }
    }

    private void spawnAnubis() {
        if (!(level() instanceof ServerLevel server) || anubisLost || anubisId != null) return;
        GarmrHelperEntity anubis = GarmrContent.HELPER.get().create(server);
        if (anubis == null) return;
        anubis.setVariant(GarmrHelperEntity.ANUBIS);
        anubis.moveTo(homeX, homeY, homeZ + GarmrConfig.ANUBIS_OFFSET_Z, 180F, 0F);
        anubis.setNoAi(true);
        anubis.setInvulnerable(true);
        tagHelper(anubis, ROLE_ANUBIS);
        if (server.addFreshEntity(anubis)) anubisId = anubis.getUUID();
    }

    private void tickAnubis(ServerLevel server) {
        if (anubisLost) return;
        Entity raw = anubisId == null ? null : server.getEntity(anubisId);
        if (!(raw instanceof LivingEntity anubis) || !anubis.isAlive()) {
            loseAnubis(false);
            return;
        }

        int phase = entityData.get(PHASE);
        if (phase <= P1_WAVE) {
            tickAnubisBlessing(anubis);
            return;
        }
        if (phase == P1_LANDING) return;
        if (phase == P2) {
            tickAnubisGuard(server, anubis);
            return;
        }
        if (phase == P3) {
            tickAnubisSacrifice(server, anubis);
        }
    }

    private void tickAnubisBlessing(LivingEntity anubis) {
        if (blessingTargetId != null && tickCount >= blessingUntilTick) blessingTargetId = null;
        if (tickCount < nextAnubisAction) return;

        ServerPlayer nearest = nearestParticipant(anubis);
        nextAnubisAction = tickCount + GarmrConfig.ANUBIS_BLESS_INTERVAL_TICKS;
        if (nearest == null) return;
        blessingTargetId = nearest.getUUID();
        blessingUntilTick = tickCount + GarmrConfig.ANUBIS_BLESS_DURATION_TICKS;
    }

    private void tickAnubisGuard(ServerLevel server, LivingEntity anubis) {
        if (carrierId != null && tickCount >= protectionUntilTick) carrierId = null;
        if (tickCount >= nextAnubisAction) {
            nextAnubisAction = tickCount + GarmrConfig.ANUBIS_GUARD_INTERVAL_TICKS;
            ServerPlayer nearest = nearestParticipant(anubis);
            if (nearest != null) {
                carrierId = nearest.getUUID();
                protectionUntilTick = tickCount + GarmrConfig.ANUBIS_GUARD_DURATION_TICKS;
            }
        }
        tickProtectionCircle(server);
    }

    private void tickAnubisSacrifice(ServerLevel server, LivingEntity anubis) {
        // 5 秒后转为分身。
        if (pendingCloneTargetId != null && cloneReadyTick > 0 && tickCount >= cloneReadyTick) {
            ServerPlayer target = onlineParticipant(server, pendingCloneTargetId);
            pendingCloneTargetId = null;
            cloneReadyTick = 0;
            if (target != null) {
                carrierId = target.getUUID();
                protectionUntilTick = tickCount + GarmrConfig.ANUBIS_CLONE_DURATION_TICKS;
            }
        }

        if (carrierId != null && tickCount >= protectionUntilTick) {
            carrierId = null;
            protectionUntilTick = 0;
        }

        // 不允许上一轮献祭/分身尚未结束时再次叠加。
        boolean busy = pendingCloneTargetId != null || carrierId != null;
        if (!busy && tickCount >= nextAnubisAction) {
            ServerPlayer nearest = nearestParticipant(anubis);
            if (nearest != null) {
                damagePercentNoKnockback(nearest, GarmrConfig.ANUBIS_SACRIFICE_DAMAGE_RATIO);
                pendingCloneTargetId = nearest.getUUID();
                cloneReadyTick = tickCount + GarmrConfig.ANUBIS_CLONE_DELAY_TICKS;
                nextAnubisAction = cloneReadyTick + GarmrConfig.ANUBIS_CLONE_DURATION_TICKS;
            } else {
                nextAnubisAction = tickCount + GarmrConfig.ANUBIS_SACRIFICE_INTERVAL_TICKS;
            }
        }

        tickProtectionCircle(server);
    }

    private void tickProtectionCircle(ServerLevel server) {
        ServerPlayer carrier = carrierId == null ? null : onlineParticipant(server, carrierId);
        if (carrier == null || tickCount >= protectionUntilTick) return;

        if (tickCount % 5 == 0) {
            for (int i = 0; i < 24; i++) {
                double a = Math.PI * 2.0D * i / 24.0D;
                server.sendParticles(ParticleTypes.END_ROD,
                        carrier.getX() + Math.cos(a) * GarmrConfig.CLEANSE_RADIUS,
                        carrier.getY() + 0.15D,
                        carrier.getZ() + Math.sin(a) * GarmrConfig.CLEANSE_RADIUS,
                        1, 0, 0, 0, 0);
            }
        }

        if (tickCount % GarmrConfig.CLEANSE_INTERVAL_TICKS == 0) {
            double radiusSq = GarmrConfig.CLEANSE_RADIUS * GarmrConfig.CLEANSE_RADIUS;
            for (ServerPlayer teammate : participants()) {
                if (teammate.distanceToSqr(carrier) > radiusSq) continue;
                int old = curseStacks.getOrDefault(teammate.getUUID(), 0);
                if (old > 0) {
                    curseStacks.put(teammate.getUUID(), Math.max(0, old - GarmrConfig.CLEANSE_STACKS_PER_TICK));
                }
            }
        }
    }

    private void loseAnubis(boolean killedByDevil) {
        if (anubisLost) return;
        anubisLost = true;

        if (killedByDevil && level() instanceof ServerLevel server && carrierId != null && tickCount < protectionUntilTick) {
            ServerPlayer clone = onlineParticipant(server, carrierId);
            if (clone != null && clone.isAlive()) {
                // 图片：分身持续期间阿努比斯被小恶魔击杀，玩家受到150%最大生命值伤害后死亡。
                damagePercentNoKnockback(clone, GarmrConfig.ANUBIS_FAIL_DAMAGE_RATIO);
                if (clone.isAlive()) clone.kill();
            }
        }

        blessingTargetId = null;
        blessingUntilTick = 0;
        carrierId = null;
        protectionUntilTick = 0;
        pendingCloneTargetId = null;
        cloneReadyTick = 0;
    }

    private void tickBreathAndBasic(ServerLevel server) {
        int phase = entityData.get(PHASE);
        if (phase != P2 && phase != P3) return;
        if (breathAge > 0) {
            tickBreath(server);
            return;
        }

        // 图片规则：冰/火扇形每 10 秒轮流，而不是“每 4 次普攻随机一次”。
        if (tickCount >= nextBreath && tickCount >= actionUntilTick) {
            int type = nextBreathType;
            nextBreathType = type == BREATH_ICE ? BREATH_FIRE : BREATH_ICE;
            nextBreath = tickCount + GarmrConfig.BREATH_INTERVAL_TICKS;
            startBreath(type, highestHatredTarget());
            return;
        }

        if (tickCount < actionUntilTick || tickCount < nextBasicAttack) return;
        ServerPlayer tank = highestHatredTarget();
        if (tank == null) return;

        // 图片明确为近战普攻；离开近战范围不再额外补全场子弹。
        if (distanceToSqr(tank) > GarmrConfig.MELEE_RANGE * GarmrConfig.MELEE_RANGE) return;

        faceTargetForAttack(tank);
        beginTimedAction(ACT_BASIC, GarmrConfig.BASIC_ACTION_TICKS);
        float damage = GarmrConfig.BASIC_DAMAGE;
        if (entityData.get(PHASE) == P3) damage *= GarmrConfig.PHASE_THREE_BASIC_MULTIPLIER;
        damageNoKnockback(tank, damage);
        nextBasicAttack = tickCount + GarmrConfig.BASIC_COOLDOWN_TICKS;
    }

    private ServerPlayer highestHatredTarget() {
        if (getHatredManager().getHighestHatredTarget() instanceof ServerPlayer player && isParticipant(player)) {
            return player;
        }
        return participants().stream().filter(ServerPlayer::isAlive)
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
    }

    private ServerPlayer nearestParticipant(Entity reference) {
        if (reference == null) return null;
        return participants().stream()
                .filter(ServerPlayer::isAlive)
                .min(Comparator.comparingDouble(p -> p.distanceToSqr(reference)))
                .orElse(null);
    }

    private ServerPlayer onlineParticipant(ServerLevel server, UUID id) {
        if (id == null) return null;
        ServerPlayer player = server.getServer().getPlayerList().getPlayer(id);
        return player != null && player.isAlive() && isParticipant(player) ? player : null;
    }

    private void startBreath(int type, ServerPlayer target) {
        int phase = entityData.get(PHASE);
        if (phase != P2 && phase != P3) return;
        breathType = type;
        breathAge = 1;
        actionUntilTick = 0;
        entityData.set(BREATH_TYPE, type);
        setAction(type == BREATH_FIRE ? ACT_FIRE_BREATH : ACT_ICE_BREATH);
        if (target != null) faceTargetForAttack(target);
        level().playSound(null, blockPosition(),
                type == BREATH_FIRE ? ModSounds.GARMR_BREATH_FIRE.get() : ModSounds.GARMR_BREATH_ICE.get(),
                SoundSource.HOSTILE, 1.6F, 1.0F);
    }

    private void tickBreath(ServerLevel server) {
        int phase = entityData.get(PHASE);
        if (phase != P2 && phase != P3) {
            breathAge = 0;
            breathType = BREATH_NONE;
            entityData.set(BREATH_TYPE, BREATH_NONE);
            if (entityData.get(ACTION) == ACT_FIRE_BREATH || entityData.get(ACTION) == ACT_ICE_BREATH) {
                setIdleAction();
            }
            return;
        }
        breathAge++;
        spawnBreathParticles(server, breathType);

        if (breathAge == 12 || breathAge == 22 || breathAge == 32) {
            // 总计 50% 近战物理，分三次结算只是为了匹配持续吐息的判定窗口。
            for (ServerPlayer player : participants()) {
                if (insideBreathCone(player.position())) {
                    damageNoKnockback(player, GarmrConfig.BREATH_DAMAGE / 3.0F);
                }
            }

            // 只能秒杀“相反属性”的冰/火幽灵。
            for (GarmrHelperEntity lady : server.getEntitiesOfClass(GarmrHelperEntity.class,
                    getBoundingBox().inflate(GarmrConfig.BREATH_RANGE + 4.0D), this::isOwnedLady)) {
                if (!insideBreathCone(lady.position())) continue;
                int ladyType = lady.getPersistentData().getInt(TAG_LADY_TYPE);
                boolean opposite = (breathType == BREATH_FIRE && ladyType == BREATH_ICE)
                        || (breathType == BREATH_ICE && ladyType == BREATH_FIRE);
                if (opposite) {
                    ladies.remove(lady.getUUID());
                    lady.discard();
                }
            }
        }

        if (breathAge >= GarmrConfig.BREATH_DURATION_TICKS) {
            breathAge = 0;
            breathType = BREATH_NONE;
            entityData.set(BREATH_TYPE, BREATH_NONE);
            setIdleAction();
        }
    }

    private boolean insideBreathCone(Vec3 point) {
        double dx = point.x - getX();
        double dz = point.z - getZ();
        double horizontalSq = dx * dx + dz * dz;
        if (horizontalSq > GarmrConfig.BREATH_RANGE * GarmrConfig.BREATH_RANGE || horizontalSq < 0.01D) return false;
        float wanted = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        return Math.abs(Mth.wrapDegrees(wanted - getYRot())) <= GarmrConfig.BREATH_HALF_ANGLE_DEGREES;
    }

    private void spawnBreathParticles(ServerLevel server, int type) {
        ParticleOptions particle = type == BREATH_FIRE ? ModParticles.GARMR_FIRE.get() : ModParticles.GARMR_ICE.get();
        double yaw = Math.toRadians(getYRot() + 90.0F);
        for (int i = 0; i < 4; i++) {
            double angle = yaw + Math.toRadians((random.nextDouble() * 60.0D) - 30.0D);
            double distance = 1.5D + random.nextDouble() * GarmrConfig.BREATH_RANGE;
            server.sendParticles(particle,
                    getX() + Math.cos(angle) * distance,
                    getY() + 2.1D + (random.nextDouble() - 0.5D) * 1.2D,
                    getZ() + Math.sin(angle) * distance,
                    1, 0.08D, 0.08D, 0.08D, 0.0D);
        }
    }

    private void tickLadies(ServerLevel server) {
        int phase = entityData.get(PHASE);
        if (phase != P2 && phase != P3) return;
        if (tickCount >= nextLady) {
            nextLady = tickCount + GarmrConfig.LADY_INTERVAL_TICKS;
            int type = nextLadyType;
            nextLadyType = type == BREATH_ICE ? BREATH_FIRE : BREATH_ICE;
            spawnLady(server, type);
        }

        Iterator<Map.Entry<UUID, Integer>> it = ladies.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            Entity raw = server.getEntity(entry.getKey());
            if (!(raw instanceof GarmrHelperEntity lady) || !lady.isLady() || !lady.isAlive()) {
                it.remove();
                continue;
            }

            ServerPlayer target = highestHatredTarget();
            if (target != null) {
                // 亡灵夫人只负责靠近目标；伤害由每秒成长 AOE 结算，不触发 helper 的近战 Goal。
                lady.setTarget(null);
                lady.getNavigation().moveTo(target, 0.85D);
            }

            if (tickCount % 20 == 0) {
                int born = lady.getPersistentData().getInt(TAG_LADY_SPAWN_TICK);
                int elapsedSeconds = Math.max(0, (tickCount - born) / 20);
                String ladySection = entry.getValue() == BREATH_FIRE ? "garmr_fire_lady" : "garmr_ice_lady";
                float damage = (float) EntityTuningConfig.configured(
                        ladySection, "attack_damage", GarmrConfig.LADY_BASE_DAMAGE)
                        * (1.0F + elapsedSeconds * GarmrConfig.LADY_DAMAGE_GROWTH_PER_SECOND);
                double radiusSq = GarmrConfig.LADY_AOE_RADIUS * GarmrConfig.LADY_AOE_RADIUS;
                for (ServerPlayer player : participants()) {
                    if (player.distanceToSqr(lady) <= radiusSq) damageNoKnockback(player, damage);
                }
            }
        }
    }

    private void spawnLady(ServerLevel server, int type) {
        int side = random.nextInt(3); // 前 / 左 / 右
        double x = homeX;
        double z = homeZ - GarmrConfig.LADY_OFFSET;
        if (side == 1) { x = homeX - GarmrConfig.LADY_OFFSET; z = homeZ; }
        if (side == 2) { x = homeX + GarmrConfig.LADY_OFFSET; z = homeZ; }

        GarmrHelperEntity lady = GarmrContent.HELPER.get().create(server);
        if (lady == null) return;
        lady.setVariant(type == BREATH_FIRE ? GarmrHelperEntity.LADY_FIRE : GarmrHelperEntity.LADY_ICE);
        lady.moveTo(x, homeY, z, 0F, 0F);
        lady.setPersistenceRequired();
        tagHelper(lady, ROLE_LADY);
        lady.getPersistentData().putInt(TAG_LADY_TYPE, type);
        lady.getPersistentData().putInt(TAG_LADY_SPAWN_TICK, tickCount);
        applyMobStats(lady, GarmrConfig.LADY_HEALTH, 0.0F,
                GarmrConfig.LADY_DEFENSE,
                GarmrConfig.LADY_ATTACK_LEVEL, GarmrConfig.LADY_DEFENSE_LEVEL);
        if (server.addFreshEntity(lady)) ladies.put(lady.getUUID(), type);
    }

    public boolean isOwnedLady(Entity entity) {
        return entity != null && ROLE_LADY.equals(entity.getPersistentData().getString(TAG_ROLE))
                && entity.getPersistentData().hasUUID(TAG_OWNER)
                && getUUID().equals(entity.getPersistentData().getUUID(TAG_OWNER));
    }

    private void tickDevils(ServerLevel server) {
        if (entityData.get(PHASE) != P3) return;
        if (anubisLost) return;
        if (tickCount >= nextDevil) {
            nextDevil = tickCount + GarmrConfig.DEVIL_INTERVAL_TICKS;
            spawnDevil(server);
        }

        Entity anubis = anubisId == null ? null : server.getEntity(anubisId);
        if (anubis == null || !anubis.isAlive()) {
            loseAnubis(false);
            return;
        }

        Iterator<UUID> it = devils.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity raw = server.getEntity(id);
            if (!(raw instanceof GarmrHelperEntity devil) || devil.getVariant() != GarmrHelperEntity.DEVIL || !devil.isAlive()) {
                it.remove();
                continue;
            }

            // 小恶魔从阿努比斯头顶缓慢下降，同时保留少量水平追踪，避免瞬移/直落。
            Vec3 delta = anubis.position().add(0.0D, 1.2D, 0.0D).subtract(devil.position());
            if (delta.lengthSqr() < 1.5D * 1.5D) {
                explodeDevil(server, devil, anubis);
                it.remove();
                return;
            }
            if (delta.lengthSqr() > 0.01D) {
                double horizontalLength = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
                double horizontalSpeed = Math.min(0.08D, horizontalLength * 0.12D);
                double verticalSpeed = Mth.clamp(delta.y * 0.08D, -0.045D, 0.045D);
                double dx = horizontalLength < 1.0E-4D ? 0.0D : delta.x / horizontalLength * horizontalSpeed;
                double dz = horizontalLength < 1.0E-4D ? 0.0D : delta.z / horizontalLength * horizontalSpeed;
                // 直接改位置，避免 Mob#aiStep 在 noAI 状态下把手动速度清零。
                devil.setPos(devil.getX() + dx, devil.getY() + verticalSpeed, devil.getZ() + dz);
                devil.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    private void spawnDevil(ServerLevel server) {
        Entity anubis = anubisId == null ? null : server.getEntity(anubisId);
        if (anubis == null) return;
        GarmrHelperEntity devil = GarmrContent.HELPER.get().create(server);
        if (devil == null) return;
        devil.setVariant(GarmrHelperEntity.DEVIL);
        devil.moveTo(anubis.getX(), anubis.getY() + 7.0D, anubis.getZ(), 0F, 0F);
        devil.setPersistenceRequired();
        devil.setNoAi(true);
        devil.setNoGravity(true);
        tagHelper(devil, ROLE_DEVIL);
        applyMobStats(devil, GarmrConfig.DEVIL_HEALTH, GarmrConfig.DEVIL_ATTACK,
                GarmrConfig.DEVIL_DEFENSE,
                GarmrConfig.DEVIL_ATTACK_LEVEL, GarmrConfig.DEVIL_DEFENSE_LEVEL);
        if (server.addFreshEntity(devil)) devils.add(devil.getUUID());
    }

    private void explodeDevil(ServerLevel server, GarmrHelperEntity devil, Entity anubis) {
        double radiusSq = GarmrConfig.DEVIL_EXPLOSION_RADIUS * GarmrConfig.DEVIL_EXPLOSION_RADIUS;
        for (ServerPlayer player : participants()) {
            if (player.distanceToSqr(devil) <= radiusSq) {
                damageNoKnockback(player, player.getMaxHealth() * GarmrConfig.DEVIL_EXPLOSION_MAX_HEALTH_RATIO);
            }
        }
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                devil.getX(), devil.getY(), devil.getZ(), 1, 0, 0, 0, 0);
        devil.discard();
        if (anubis.isAlive()) anubis.kill();
        loseAnubis(true);
    }

    private void fireProjectile(ServerPlayer target, float damage) {
        fireProjectileFrom(this, target, damage);
    }

    private void fireProjectileFrom(Entity source, ServerPlayer target, float damage) {
        if (!(level() instanceof ServerLevel server) || target == null || !target.isAlive()) return;
        GarmrProjectile projectile = GarmrContent.PROJECTILE.get().create(server);
        if (projectile == null) return;
        projectile.setOwner(this);
        projectile.configure(damage, (float) GarmrConfig.PROJECTILE_AOE_RADIUS);
        projectile.moveTo(source.getX(), source.getY() + source.getBbHeight() * 0.75D,
                source.getZ(), source.getYRot(), source.getXRot());
        Vec3 aim = target.position().add(0, 0.6D, 0).subtract(projectile.position());
        projectile.shoot(aim.x, aim.y, aim.z, 0.65F, 0.0F);
        tagHelper(projectile, "projectile");
        server.addFreshEntity(projectile);
    }

    private void clearOwnedSummons(ServerLevel server) {
        for (Entity entity : server.getAllEntities()) {
            if (entity == this) continue;
            if (entity.getPersistentData().hasUUID(TAG_OWNER)
                    && getUUID().equals(entity.getPersistentData().getUUID(TAG_OWNER))) {
                entity.discard();
            }
        }
        coreAddId = null;
        anubisId = null;
        anubisLost = true;
        p1SkeletonIds.clear();
        ladies.clear();
        devils.clear();
        blessingTargetId = null;
        blessingUntilTick = 0;
        carrierId = null;
        protectionUntilTick = 0;
        pendingCloneTargetId = null;
        cloneReadyTick = 0;
    }

    /** 回血/脱战后的新一轮：清理阶段召唤物，但保留仍存活的阿努比斯。 */
    private void resetForNewFight(ServerLevel server) {
        clearPhaseSummons(server);
        getHatredManager().resetRawHatred();
        curseStacks.clear();
        for (ServerPlayer player : participants()) {
            player.removeEffect(com.yourname.yellowduck.registry.ModEffects.GARMR_DEATH_CURSE.get());
        }
        blessingTargetId = null;
        blessingUntilTick = 0;
        carrierId = null;
        protectionUntilTick = 0;
        pendingCloneTargetId = null;
        cloneReadyTick = 0;
        p1WaveStarted = false;
        takeoffAge = 0;
        landingAge = 0;
        breathAge = 0;
        breathType = BREATH_NONE;
        actionUntilTick = 0;
        nextBreathType = BREATH_ICE;
        nextLadyType = BREATH_ICE;
        nextBasicAttack = tickCount + 30;
        nextBreath = tickCount + GarmrConfig.BREATH_INTERVAL_TICKS;
        nextLady = tickCount + GarmrConfig.LADY_INTERVAL_TICKS;
        nextAnubisAction = tickCount + 20;
        nextDevil = tickCount + GarmrConfig.DEVIL_INTERVAL_TICKS;
        entityData.set(PHASE, P1_GROUND);
        entityData.set(AIRBORNE, false);
        entityData.set(BREATH_TYPE, BREATH_NONE);
        setNoGravity(false);
        setPos(homeX, homeY, homeZ);
        setDeltaMovement(Vec3.ZERO);
        setIdleAction();
    }

    /** 清理除阿努比斯外的所有本 Boss 阶段召唤物。 */
    private void clearPhaseSummons(ServerLevel server) {
        for (Entity entity : server.getAllEntities()) {
            if (entity == this) continue;
            if (!entity.getPersistentData().hasUUID(TAG_OWNER)
                    || !getUUID().equals(entity.getPersistentData().getUUID(TAG_OWNER))) continue;
            if (ROLE_ANUBIS.equals(entity.getPersistentData().getString(TAG_ROLE))) continue;
            entity.discard();
        }
        coreAddId = null;
        p1SkeletonIds.clear();
        ladies.clear();
        devils.clear();
    }

    @Override
    public void remove(RemovalReason reason) {
        // 只有真正销毁/消失时清理阿努比斯；区块卸载不应提前清理它。
        if (!level().isClientSide && reason.shouldDestroy()) {
            if (level() instanceof ServerLevel server) clearOwnedSummons(server);
        }
        super.remove(reason);
    }

    private List<ServerPlayer> shuffledParticipants() {
        List<ServerPlayer> result = new ArrayList<>(participants());
        for (int i = result.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            ServerPlayer a = result.get(i);
            result.set(i, result.get(j));
            result.set(j, a);
        }
        return result;
    }

    private void applyMobStats(Mob mob, double healthValue, float attackValue,
                               int defense, int attackLevel, int defenseLevel) {
        String configSection = garmrConfigSection(mob);
        if (configSection != null) {
            healthValue = EntityTuningConfig.configured(configSection, "max_health", healthValue);
            attackValue = (float) EntityTuningConfig.configured(configSection, "attack_damage", attackValue);
            defense = (int) Math.round(EntityTuningConfig.configured(configSection, "armor", defense));
            attackLevel = (int) Math.round(EntityTuningConfig.configured(configSection, "attack_level", attackLevel));
            defenseLevel = (int) Math.round(EntityTuningConfig.configured(configSection, "defense_level", defenseLevel));
            double knockback = EntityTuningConfig.configured(configSection, "knockback_resistance", 1.0D);
            AttributeInstance resistance = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (resistance != null) resistance.setBaseValue(knockback);
        }
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(healthValue);
            mob.setHealth((float) healthValue);
        }
        AttributeInstance attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) attack.setBaseValue(attackValue);
        mob.getPersistentData().putInt(TAG_DEFENSE, defense);
        mob.getPersistentData().putInt(TAG_ATTACK_LEVEL, attackLevel);
        mob.getPersistentData().putInt(TAG_DEFENSE_LEVEL, defenseLevel);
    }

    private String garmrConfigSection(Mob mob) {
        String role = mob.getPersistentData().getString(TAG_ROLE);
        if (ROLE_CORE_ADD.equals(role)) return "garmr_lava_guard";
        if (ROLE_P1_SKELETON.equals(role)) return "garmr_p1_archer";
        if (ROLE_ANUBIS.equals(role)) return "garmr_anubis";
        if (ROLE_DEVIL.equals(role)) return "garmr_little_devil";
        if (ROLE_DEATH_GUARD.equals(role)) return "garmr_death_guard";
        if (ROLE_LADY.equals(role)) {
            return mob.getPersistentData().getInt(TAG_LADY_TYPE) == BREATH_FIRE
                    ? "garmr_fire_lady" : "garmr_ice_lady";
        }
        return null;
    }

    private void tagHelper(Entity entity, String role) {
        entity.getPersistentData().putUUID(TAG_OWNER, getUUID());
        entity.getPersistentData().putString(TAG_ROLE, role);
        if (getPersistentData().hasUUID("YellowDuckDungeon")) {
            entity.getPersistentData().putUUID("YellowDuckDungeon", getPersistentData().getUUID("YellowDuckDungeon"));
        }
    }

    private void cleanupCurseOwners() {
        List<UUID> active = participants().stream().map(ServerPlayer::getUUID).toList();
        curseStacks.keySet().removeIf(id -> !active.contains(id));
    }

    private void beginTimedAction(int action, int durationTicks) {
        setAction(action);
        actionUntilTick = tickCount + Math.max(1, durationTicks);
    }

    private void expireTimedAction() {
        int action = entityData.get(ACTION);
        if (actionUntilTick > 0 && tickCount >= actionUntilTick
                && action != ACT_FIRE_BREATH && action != ACT_ICE_BREATH
                && action != ACT_LANDING && action != ACT_TAKEOFF && action != ACT_DEATH) {
            actionUntilTick = 0;
            setIdleAction();
        }
    }

    private void setIdleAction() {
        entityData.set(ACTION, ACT_IDLE);
        entityData.set(ACTION_START_TICK, tickCount);
    }

    private void setAction(int action) {
        entityData.set(ACTION, action);
        entityData.set(ACTION_START_TICK, tickCount);
        entityData.set(ACTION_SERIAL, entityData.get(ACTION_SERIAL) + 1);
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            actionUntilTick = 0;
            setAction(ACT_DEATH);
            if (level() instanceof ServerLevel server) clearOwnedSummons(server);
            for (ServerPlayer player : participants()) {
                player.removeEffect(com.yourname.yellowduck.registry.ModEffects.GARMR_DEATH_CURSE.get());
            }
            curseStacks.clear();
            blessingTargetId = null;
            carrierId = null;
            pendingCloneTargetId = null;
        }
        super.die(source);
    }
}
