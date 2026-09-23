package com.yourname.yellowduck.garmr;

import com.yourname.yellowduck.boss.NetcraftBossBase;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
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
 * 这一版优先把“已确认机制”做成服务器权威状态机。原数据仍未确认的实体外观/战斗数值均显式
 * 标记为 placeholder，不伪装成原版还原：P1 核心小怪暂用 Blaze、亡灵夫人暂用 Zombie、
 * 阿努比斯暂用 IronGolem、小恶魔暂用 Vex。后续拿到原实体资源时只替换这些承载体。
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

    public static final int P1_AIR = 1;
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

    public static final int BREATH_NONE = 0;
    public static final int BREATH_FIRE = 1;
    public static final int BREATH_ICE = 2;

    private static final String TAG_ROLE = "GarmrRole";
    private static final String TAG_OWNER = "GarmrOwner";
    private static final String ROLE_CORE_ADD = "p1_core_add_placeholder";
    private static final String ROLE_P1_SKELETON = "p1_skeleton_placeholder";
    private static final String ROLE_LADY = "lady_placeholder";
    private static final String ROLE_ANUBIS = "anubis_placeholder";
    private static final String ROLE_DEVIL = "devil_placeholder";

    private final Map<UUID, Integer> curseStacks = new HashMap<>();
    private final Map<UUID, Integer> ladies = new HashMap<>(); // UUID -> fire/ice
    private final List<UUID> devils = new ArrayList<>();

    private boolean initialized;
    private double homeX;
    private double homeY;
    private double homeZ;
    private boolean p1WaveStarted;
    private UUID coreAddId;
    private UUID anubisId;
    private UUID carrierId;
    private boolean anubisLost;
    private int landingAge;
    private int nextP1Projectile;
    private int nextBasicAttack;
    private int basicAttackCount;
    private int actionUntilTick;
    private int queuedBreathType;
    private int breathAge;
    private int breathType;
    private int nextLady;
    private int nextAnubisSelect;
    private int nextDevil;

    public GarmrBoss(EntityType<? extends GarmrBoss> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setBaseTier(3); // TODO: replace with parsed original tier
        setBaseDamage((int) GarmrConfig.BASIC_DAMAGE);
        xpReward = 0; // 副本奖励由 DungeonRewardManager 统一结算
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
        entityData.define(PHASE, P1_AIR);
        entityData.define(ACTION, ACT_IDLE);
        entityData.define(ACTION_SERIAL, 0);
        entityData.define(ACTION_START_TICK, 0);
        entityData.define(BREATH_TYPE, BREATH_NONE);
        entityData.define(AIRBORNE, true);
    }

    @Override
    protected void registerGoals() {
        // 原机制：Boss 本体固定站位，不注册追击/游走 Goal。
    }

    @Override public Component getName() { return Component.literal("地狱双头犬·加姆"); }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
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
        // /summon 调试时没有副本实例，允许出生点 48 格内的生存玩家参与。
        return player.distanceToSqr(homeX, homeY, homeZ) <= 48.0D * 48.0D;
    }

    public List<ServerPlayer> participants() {
        if (!(level() instanceof ServerLevel server)) return List.of();
        List<ServerPlayer> result = new ArrayList<>();
        for (ServerPlayer player : server.players()) if (isParticipant(player)) result.add(player);
        return result;
    }

    public int curseStacks(UUID player) { return curseStacks.getOrDefault(player, 0); }

    public boolean damageNoKnockback(LivingEntity target, float damage) {
        return hurtWithoutKnockback(target, damageSources().mobAttack(this), damage);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && entityData.get(PHASE) <= P1_LANDING) {
            Entity attacker = source.getEntity();
            Entity direct = source.getDirectEntity();
            if (!p1WaveStarted && attacker instanceof ServerPlayer player && isParticipant(player)
                    && direct instanceof Projectile) {
                getHatredManager().addRawHatred(player, 10.0D);
                startP1Wave();
            }
            return false; // P1 空中/落地过程无敌，转阶段由核心小怪驱动。
        }
        return super.hurt(source, amount);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !isAlive()) return;
        if (!(level() instanceof ServerLevel server)) return;

        if (!initialized) initializeEncounter();
        lockHorizontalPosition();
        cleanupCurseOwners();
        expireTimedAction();
        // 索命属于整场副本持续机制，P1 空中阶段同样累计。
        tickCurse();

        int phase = entityData.get(PHASE);
        if (phase == P1_AIR || phase == P1_WAVE) {
            tickP1(server);
        } else if (phase == P1_LANDING) {
            tickLanding();
        } else {
            if (phase == P2 && getHealth() / Math.max(1.0F, getMaxHealth()) < GarmrConfig.PHASE_THREE_HEALTH) {
                enterP3();
                phase = P3;
            }
            tickAnubis(server);
            tickLadies(server);
            tickBreathOrBasic(server);
            if (phase == P3) tickDevils(server);
        }
    }

    private void initializeEncounter() {
        initialized = true;
        Vec3 spawn = getSpawnPosition() != null ? getSpawnPosition() : position();
        homeX = spawn.x;
        homeY = spawn.y;
        homeZ = spawn.z;
        setNoGravity(true);
        setPos(homeX, homeY + 6.0D, homeZ);
        entityData.set(AIRBORNE, true);
        entityData.set(PHASE, P1_AIR);
        setIdleAction();
        nextP1Projectile = tickCount + GarmrConfig.P1_PROJECTILE_INTERVAL_TICKS;
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

    private void startP1Wave() {
        if (p1WaveStarted || !(level() instanceof ServerLevel server)) return;
        p1WaveStarted = true;
        entityData.set(PHASE, P1_WAVE);
        announce("§4[恐惧之地] §c加姆仍处于空中无敌状态，核心小怪已经出现！");

        // PLACEHOLDER：原资源尚未确认“火元素/熔岩卫士/巨兽”的最终实体 ID。
        Blaze core = EntityType.BLAZE.create(server);
        if (core != null) {
            core.moveTo(homeX, homeY + 1.0D, homeZ - 7.0D, 0F, 0F);
            core.setPersistenceRequired();
            core.setCustomName(Component.literal("§6火元素（原实体待解析）"));
            core.setCustomNameVisible(true);
            tagHelper(core, ROLE_CORE_ADD);
            if (server.addFreshEntity(core)) coreAddId = core.getUUID();
        }

        for (int i = 0; i < 4; i++) {
            double angle = Math.PI * 2.0D * i / 4.0D;
            Skeleton skeleton = EntityType.SKELETON.create(server);
            if (skeleton == null) continue;
            skeleton.moveTo(homeX + Math.cos(angle) * 8.0D, homeY + 1.0D,
                    homeZ - 7.0D + Math.sin(angle) * 8.0D, 0F, 0F);
            skeleton.setPersistenceRequired();
            tagHelper(skeleton, ROLE_P1_SKELETON);
            server.addFreshEntity(skeleton);
        }
    }

    private void tickP1(ServerLevel server) {
        if (!p1WaveStarted) return;
        Entity core = coreAddId == null ? null : server.getEntity(coreAddId);
        if (coreAddId != null && (core == null || !core.isAlive())) {
            beginLanding();
            return;
        }
        if (tickCount >= nextP1Projectile) {
            nextP1Projectile = tickCount + GarmrConfig.P1_PROJECTILE_INTERVAL_TICKS;
            beginTimedAction(ACT_RANGED, GarmrConfig.RANGED_ACTION_TICKS);
            List<ServerPlayer> targets = shuffledParticipants();
            for (int i = 0; i < Math.min(3, targets.size()); i++) {
                fireProjectile(targets.get(i), GarmrConfig.P1_AOE_DAMAGE);
            }
        }
    }

    private void beginLanding() {
        entityData.set(PHASE, P1_LANDING);
        actionUntilTick = 0;
        queuedBreathType = BREATH_NONE;
        setAction(ACT_LANDING);
        landingAge = 0;
        float targetHealth = getMaxHealth() * GarmrConfig.PHASE_TWO_HEALTH;
        setHealth(Math.min(getHealth(), targetHealth));
        announce("§4[恐惧之地] §e核心小怪已被击败，加姆开始落地！");
    }

    private void tickLanding() {
        landingAge++;
        double startY = homeY + 6.0D;
        double progress = Math.min(1.0D, landingAge / (double) Math.max(1, GarmrConfig.LANDING_TICKS));
        double smooth = progress * progress * (3.0D - 2.0D * progress);
        setPos(homeX, Mth.lerp(smooth, startY, homeY + 1.0D), homeZ);
        if (progress >= 1.0D) enterP2();
    }

    private void enterP2() {
        entityData.set(PHASE, P2);
        entityData.set(AIRBORNE, false);
        actionUntilTick = 0;
        queuedBreathType = BREATH_NONE;
        setIdleAction();
        setNoGravity(false);
        setPos(homeX, homeY + 1.0D, homeZ);
        nextBasicAttack = tickCount + 30;
        nextLady = tickCount + GarmrConfig.LADY_INTERVAL_TICKS;
        nextAnubisSelect = tickCount + 20;
        spawnAnubis();
        announce("§4[恐惧之地] §c加姆进入第二阶段！索命与冰火吐息开始生效。");
    }

    private void enterP3() {
        entityData.set(PHASE, P3);
        nextDevil = tickCount + GarmrConfig.DEVIL_INTERVAL_TICKS;
        announce("§4[恐惧之地] §4加姆进入第三阶段：小恶魔开始袭击阿努比斯！");
    }

    private void tickCurse() {
        if (tickCount % GarmrConfig.CURSE_INTERVAL_TICKS != 0) return;
        for (ServerPlayer player : participants()) {
            int stacks = curseStacks.merge(player.getUUID(), 1, Integer::sum);
            player.displayClientMessage(Component.literal("§5索命 §f" + stacks + "/" + GarmrConfig.CURSE_KILL_STACKS), true);
            if (stacks >= GarmrConfig.CURSE_KILL_STACKS) {
                player.hurt(player.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
            }
        }
    }

    private void spawnAnubis() {
        if (!(level() instanceof ServerLevel server) || anubisLost) return;
        IronGolem anubis = EntityType.IRON_GOLEM.create(server);
        if (anubis == null) return;
        anubis.moveTo(homeX, homeY + 1.0D, homeZ + GarmrConfig.ANUBIS_OFFSET_Z, 180F, 0F);
        anubis.setNoAi(true);
        anubis.setInvulnerable(true);
        anubis.setCustomName(Component.literal("§f阿努比斯（模型待原资源）"));
        anubis.setCustomNameVisible(true);
        tagHelper(anubis, ROLE_ANUBIS);
        if (server.addFreshEntity(anubis)) anubisId = anubis.getUUID();
    }

    private void tickAnubis(ServerLevel server) {
        if (anubisLost) return;
        Entity anubis = anubisId == null ? null : server.getEntity(anubisId);
        if (!(anubis instanceof LivingEntity living) || !living.isAlive()) {
            loseAnubis();
            return;
        }

        if (tickCount >= nextAnubisSelect) {
            nextAnubisSelect = tickCount + GarmrConfig.ANUBIS_SELECT_INTERVAL_TICKS;
            ServerPlayer nearest = participants().stream()
                    .filter(ServerPlayer::isAlive)
                    .min(Comparator.comparingDouble(p -> p.distanceToSqr(anubis)))
                    .orElse(null);
            carrierId = nearest == null ? null : nearest.getUUID();
            if (nearest != null && GarmrConfig.ANUBIS_SELECT_DAMAGE > 0) {
                damageNoKnockback(nearest, GarmrConfig.ANUBIS_SELECT_DAMAGE);
            }
        }

        ServerPlayer carrier = carrierId == null ? null : server.getServer().getPlayerList().getPlayer(carrierId);
        if (carrier == null || !carrier.isAlive() || !isParticipant(carrier)) return;

        if (tickCount % 5 == 0) {
            for (int i = 0; i < 24; i++) {
                double a = Math.PI * 2.0D * i / 24.0D;
                server.sendParticles(ParticleTypes.END_ROD,
                        carrier.getX() + Math.cos(a) * GarmrConfig.CLEANSE_RADIUS,
                        carrier.getY() + 0.15D,
                        carrier.getZ() + Math.sin(a) * GarmrConfig.CLEANSE_RADIUS,
                        1, 0, 0, 0, 0);
            }
            for (ServerPlayer teammate : participants()) {
                if (teammate.getUUID().equals(carrier.getUUID())) continue;
                if (teammate.distanceToSqr(carrier) <= GarmrConfig.CLEANSE_RADIUS * GarmrConfig.CLEANSE_RADIUS) {
                    curseStacks.put(teammate.getUUID(), 0);
                }
            }
        }
    }

    private void loseAnubis() {
        if (anubisLost) return;
        anubisLost = true;
        if (level() instanceof ServerLevel server && carrierId != null) {
            ServerPlayer carrier = server.getServer().getPlayerList().getPlayer(carrierId);
            if (carrier != null && carrier.isAlive() && isParticipant(carrier)) {
                carrier.hurt(carrier.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
            }
        }
        carrierId = null;
        announce("§4[恐惧之地] §4阿努比斯已经死亡，当前分身玩家被一同带走，净化永久失效！");
    }

    private void tickBreathOrBasic(ServerLevel server) {
        if (breathAge > 0) {
            tickBreath(server);
            return;
        }

        // 第四次普攻先完整播放，再接吐息；避免同一 tick 把普攻动画直接覆盖掉。
        if (queuedBreathType != BREATH_NONE) {
            if (tickCount < actionUntilTick) return;
            int queued = queuedBreathType;
            queuedBreathType = BREATH_NONE;
            startBreath(queued, highestHatredTarget());
            return;
        }
        if (tickCount < actionUntilTick || tickCount < nextBasicAttack) return;

        ServerPlayer tank = highestHatredTarget();
        if (tank == null) return;
        faceTargetForAttack(tank);

        if (distanceToSqr(tank) <= GarmrConfig.MELEE_RANGE * GarmrConfig.MELEE_RANGE) {
            beginTimedAction(ACT_BASIC, GarmrConfig.BASIC_ACTION_TICKS);
            damageNoKnockback(tank, GarmrConfig.BASIC_DAMAGE);
        } else {
            beginTimedAction(ACT_RANGED, GarmrConfig.RANGED_ACTION_TICKS);
            // BWIKI描述“仇恨目标不在近战范围则喷出子弹攻击全场玩家”。
            for (ServerPlayer player : participants()) fireProjectile(player, GarmrConfig.RANGED_DAMAGE);
        }

        basicAttackCount++;
        nextBasicAttack = tickCount + GarmrConfig.BASIC_COOLDOWN_TICKS;
        if (basicAttackCount >= GarmrConfig.BASIC_ATTACKS_PER_BREATH) {
            basicAttackCount = 0;
            queuedBreathType = random.nextBoolean() ? BREATH_FIRE : BREATH_ICE;
        }
    }

    private ServerPlayer highestHatredTarget() {
        if (getHatredManager().getHighestHatredTarget() instanceof ServerPlayer player && isParticipant(player)) return player;
        return participants().stream().filter(ServerPlayer::isAlive)
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
    }

    private void startBreath(int type, ServerPlayer target) {
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
        breathAge++;
        spawnBreathParticles(server, breathType);
        if (breathAge == 12 || breathAge == 22 || breathAge == 32) {
            for (ServerPlayer player : participants()) {
                if (insideBreathCone(player.position())) damageNoKnockback(player, GarmrConfig.BREATH_DAMAGE / 3.0F);
            }
            // 公开资料只确认“吐息能处理亡灵夫人”，尚不能确认必须同属性。
            for (Zombie lady : server.getEntitiesOfClass(Zombie.class, getBoundingBox().inflate(GarmrConfig.BREATH_RANGE + 4.0D),
                    this::isOwnedLady)) {
                if (insideBreathCone(lady.position())) lady.hurt(damageSources().mobAttack(this), 1_000_000.0F);
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
        if (tickCount >= nextLady) {
            nextLady = tickCount + GarmrConfig.LADY_INTERVAL_TICKS;
            spawnLady(server, random.nextBoolean() ? BREATH_FIRE : BREATH_ICE);
        }
        Iterator<Map.Entry<UUID, Integer>> it = ladies.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            Entity raw = server.getEntity(entry.getKey());
            if (!(raw instanceof Zombie lady) || !lady.isAlive()) { it.remove(); continue; }
            ServerPlayer target = highestHatredTarget();
            if (target != null) {
                lady.setTarget(target);
                lady.getNavigation().moveTo(target, 0.85D);
            }
            if (tickCount % 20 == 0) {
                for (ServerPlayer player : participants()) {
                    if (player.distanceToSqr(lady) <= 3.5D * 3.5D) damageNoKnockback(player, GarmrConfig.LADY_AOE_DAMAGE);
                }
            }
        }
    }

    private void spawnLady(ServerLevel server, int type) {
        int side = random.nextInt(3); // front / left / right
        double x = homeX;
        double z = homeZ - GarmrConfig.LADY_OFFSET;
        if (side == 1) { x = homeX - GarmrConfig.LADY_OFFSET; z = homeZ; }
        if (side == 2) { x = homeX + GarmrConfig.LADY_OFFSET; z = homeZ; }
        Zombie lady = EntityType.ZOMBIE.create(server);
        if (lady == null) return;
        lady.moveTo(x, homeY + 1.0D, z, 0F, 0F);
        lady.setPersistenceRequired();
        lady.setCustomName(Component.literal(type == BREATH_FIRE ? "§c火亡灵夫人（模型待原资源）" : "§b冰亡灵夫人（模型待原资源）"));
        lady.setCustomNameVisible(true);
        tagHelper(lady, ROLE_LADY);
        lady.getPersistentData().putInt("GarmrLadyType", type);
        AttributeInstance health = lady.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) { health.setBaseValue(100.0D); lady.setHealth(100.0F); }
        if (server.addFreshEntity(lady)) ladies.put(lady.getUUID(), type);
    }

    public boolean isOwnedLady(Entity entity) {
        return entity != null && ROLE_LADY.equals(entity.getPersistentData().getString(TAG_ROLE))
                && entity.getPersistentData().hasUUID(TAG_OWNER)
                && getUUID().equals(entity.getPersistentData().getUUID(TAG_OWNER));
    }

    private void tickDevils(ServerLevel server) {
        if (anubisLost) return;
        if (tickCount >= nextDevil) {
            nextDevil = tickCount + GarmrConfig.DEVIL_INTERVAL_TICKS;
            spawnDevil(server);
        }
        Entity anubis = anubisId == null ? null : server.getEntity(anubisId);
        if (anubis == null || !anubis.isAlive()) { loseAnubis(); return; }

        Iterator<UUID> it = devils.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity raw = server.getEntity(id);
            if (!(raw instanceof Vex devil) || !devil.isAlive()) { it.remove(); continue; }
            Vec3 delta = anubis.position().add(0, 1.2D, 0).subtract(devil.position());
            if (delta.lengthSqr() < 1.5D * 1.5D) {
                anubis.kill();
                devil.discard();
                it.remove();
                loseAnubis();
                return;
            }
            if (delta.lengthSqr() > 0.01D) devil.setDeltaMovement(delta.normalize().scale(0.32D));
            if (tickCount % 3 == 0) {
                server.sendParticles(ModParticles.GARMR_DEVIL_SMOKE.get(), devil.getX(), devil.getY(), devil.getZ(),
                        2, 0.15D, 0.15D, 0.15D, 0.0D);
            }
        }
    }

    private void spawnDevil(ServerLevel server) {
        Entity anubis = anubisId == null ? null : server.getEntity(anubisId);
        if (anubis == null) return;
        Vex devil = EntityType.VEX.create(server);
        if (devil == null) return;
        devil.moveTo(anubis.getX(), anubis.getY() + 7.0D, anubis.getZ(), 0F, 0F);
        devil.setPersistenceRequired();
        devil.setNoAi(true);
        devil.setNoGravity(true);
        devil.setCustomName(Component.literal("§5小恶魔"));
        tagHelper(devil, ROLE_DEVIL);
        if (server.addFreshEntity(devil)) devils.add(devil.getUUID());
    }

    private void fireProjectile(ServerPlayer target, float damage) {
        if (!(level() instanceof ServerLevel server) || target == null || !target.isAlive()) return;
        GarmrProjectile projectile = GarmrContent.PROJECTILE.get().create(server);
        if (projectile == null) return;
        projectile.setOwner(this);
        projectile.configure(damage, (float) GarmrConfig.PROJECTILE_AOE_RADIUS);
        projectile.moveTo(getX(), getY() + 2.2D, getZ(), getYRot(), getXRot());
        Vec3 aim = target.position().add(0, 0.6D, 0).subtract(projectile.position());
        projectile.shoot(aim.x, aim.y, aim.z, 0.65F, 0.0F);
        tagHelper(projectile, "projectile");
        server.addFreshEntity(projectile);
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
                && action != ACT_LANDING && action != ACT_DEATH) {
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

    private void announce(String text) {
        for (ServerPlayer player : participants()) player.sendSystemMessage(Component.literal(text));
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            actionUntilTick = 0;
            queuedBreathType = BREATH_NONE;
            setAction(ACT_DEATH);
            curseStacks.clear();
        }
        super.die(source);
    }
}
