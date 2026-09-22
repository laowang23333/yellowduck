package com.yourname.yellowduck.silk;

import com.yourname.yellowduck.boss.NetcraftBossBase;
import com.yourname.yellowduck.dungeon.DungeonTeleportGuard;
import com.yourname.yellowduck.particle.ModParticles;
import com.yourname.yellowduck.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 疯狂教授斯尔克。
 *
 * 本版把旧 YellowDuck 的“追踪陨石/30秒传熊/中心扩圈黑水”替换为从奶块客户端资源确认的机制：
 * P1：普攻AOE、多重蝙蝠、10秒流星禁锢+分摊、120度暗火喷射；
 * P2：腐化横扫、黑暗泰迪/黑暗史莱姆、20秒玩家间疫病传染、能量爆发；
 * P3：10秒后脚下黑水并十字扩散、200HP黑暗能量球；
 * 玩家资源：心智腐蚀/黑暗能量上限和疯狂持续时间可由 yellowduck-entities.toml 调整。
 */
public class SilkBoss extends NetcraftBossBase {
    public static final EntityDataAccessor<Integer> ANIMATION =
            SynchedEntityData.defineId(SilkBoss.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> CAST_SERIAL =
            SynchedEntityData.defineId(SilkBoss.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> MAD =
            SynchedEntityData.defineId(SilkBoss.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> WALKING =
            SynchedEntityData.defineId(SilkBoss.class, EntityDataSerializers.BOOLEAN);

    private static final int ACT_BASIC = 1;
    private static final int ACT_BATS = 2;
    private static final int ACT_METEOR = 3;
    private static final int ACT_FLAME = 4;
    private static final int ACT_SWEEP = 5;
    private static final int ACT_SUMMON = 6;
    private static final int ACT_PLAGUE = 7;
    private static final int ACT_BURST = 8;
    private static final int ACT_BLACK_WATER = 9;
    private static final int ACT_BLACK_BALL = 10;

    private static final class Fighter {
        int corruption;
        int blackEnergy;
        int rootUntil;
        Vec3 rootPoint;
        int plagueDue;
        int hostUntil;
        int blackWaterDue;
        int heartFire;
        int heartFireUntil;
        int fireStacks;
        int fireUntil;
        int madUntil;
        int lastPillarCleanse;
    }

    private record MeteorMark(UUID target, Vec3 point, int due) {}
    private record Echo(UUID target, Vec3 fallback, int due) {}
    private record SupportOrb(Vec3 point, int expires) {}
    private record SupportZone(Vec3 point, int expires) {}
    private record SupportPillar(Vec3 point, int expires) {}

    private final Map<UUID, Fighter> fighters = new HashMap<>();
    private final List<MeteorMark> meteorMarks = new ArrayList<>();
    private final List<Echo> echoes = new ArrayList<>();
    private final List<SupportOrb> fireOrbs = new ArrayList<>();
    private final List<SupportZone> fireRain = new ArrayList<>();
    private final List<SupportPillar> pillars = new ArrayList<>();
    private final Set<UUID> summons = new HashSet<>();
    /** 一次暗火喷射内已经命中过的玩家；视觉持续期间继续判定，但同一轮只结算一次主伤害。 */
    private final Set<UUID> flameHitPlayers = new HashSet<>();

    private boolean engaged;
    private boolean enteredP2;
    private boolean enteredP3;
    private int castAction;
    private int castAge;
    private int castDuration;
    private UUID castTarget;
    private Vec3 castPoint;
    private float flameYaw;
    private double previousX;
    private double previousZ;
    private boolean configApplied;
    /** Boss 技能之间的公共恢复窗口，避免多个已冷却技能在动画结束后瞬间倾泻。 */
    private int nextDecisionTick;

    private int nextBasic;
    private int nextBats;
    private int nextMeteor;
    private int nextFlame;
    private int nextSweep;
    private int nextSummon;
    private int nextPlague;
    private int nextBurst;
    private int nextBlackWater;
    private int nextBlackBall;
    private int nextFireOrb;
    private int nextFireRain;
    private int nextPillar;
    private int nextHeartFire;

    public SilkBoss(EntityType<? extends SilkBoss> type, net.minecraft.world.level.Level level) {
        super(type, level);
        setPersistenceRequired();
        setBaseTier(SilkBalance.BOSS_TIER);
        setBaseDamage((int) SilkBalance.BASIC_DAMAGE);
        xpReward = 150;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, SilkBalance.HEALTH)
                .add(Attributes.MOVEMENT_SPEED, SilkBalance.BOSS_MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, SilkBalance.BOSS_FOLLOW_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, SilkBalance.BOSS_KNOCKBACK_RESISTANCE)
                .add(Attributes.ATTACK_DAMAGE, SilkBalance.BASIC_DAMAGE);
    }

    @Override
    protected void registerGoals() {
        // 技能循环自行管理导航，避免 vanilla MeleeAttackGoal 额外补一次伤害。
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ANIMATION, 0);
        entityData.define(CAST_SERIAL, 0);
        entityData.define(MAD, false);
        entityData.define(WALKING, false);
    }

    @Override
    public Component getName() {
        return Component.literal("疯狂教授斯尔克");
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPlayingAttackAnimation() {
        return castAction != 0;
    }

    @Override public int getMeleeDefense() { return SilkBalance.BOSS_MELEE_DEFENSE; }
    @Override public int getRangedDefense() { return SilkBalance.BOSS_RANGED_DEFENSE; }
    @Override public int getMagicDefense() { return SilkBalance.BOSS_MAGIC_DEFENSE; }
    @Override public float getDamageReductionRatio() { return SilkBalance.BOSS_DAMAGE_REDUCTION; }

    public int phase() {
        float pct = getHealth() / Math.max(1.0F, getMaxHealth());
        if (pct <= SilkBalance.PHASE_THREE_HEALTH) return 3;
        if (pct <= SilkBalance.PHASE_TWO_HEALTH) return 2;
        return 1;
    }

    @Override
    public boolean isValidHatredPlayer(net.minecraft.world.entity.player.Player player) {
        if (!super.isValidHatredPlayer(player)) return false;
        Vec3 spawn = getSpawnPosition();
        return spawn == null || player.position().distanceToSqr(spawn)
                <= SilkBalance.ARENA_RADIUS * SilkBalance.ARENA_RADIUS;
    }

    public boolean valid(ServerPlayer player) {
        return isValidHatredPlayer(player);
    }

    /** 供召唤物判断其所属教授这一轮战斗是否仍有效；服务器重启/脱战后旧召唤物应自行清理。 */
    public boolean isEncounterActive() {
        return isAlive() && engaged;
    }

    /** /yd reload 调低层数上限时，立即把已经存在的战斗状态压回新上限。 */
    public void clampRuntimeMeters() {
        int max = Math.max(1, SilkBalance.MAX_METER);
        for (Fighter fighter : fighters.values()) {
            fighter.corruption = Math.min(fighter.corruption, max);
            fighter.blackEnergy = Math.min(fighter.blackEnergy, max);
            fighter.heartFire = Math.min(fighter.heartFire, max);
            fighter.fireStacks = Math.min(fighter.fireStacks, max);
        }
    }

    public List<ServerPlayer> targets() {
        if (!(level() instanceof ServerLevel serverLevel)) return List.of();
        Vec3 home = homePosition();
        return serverLevel.getEntitiesOfClass(
                ServerPlayer.class,
                new AABB(home, home).inflate(SilkBalance.ARENA_RADIUS),
                this::valid);
    }

    private ServerPlayer online(UUID id) {
        if (id == null || !(level() instanceof ServerLevel serverLevel)) return null;
        return serverLevel.getServer().getPlayerList().getPlayer(id);
    }

    private ServerPlayer player(UUID id) {
        ServerPlayer player = online(id);
        return player != null && valid(player) ? player : null;
    }

    private ServerPlayer chooseTank() {
        return getAttackTargetEntity() instanceof ServerPlayer player && valid(player) ? player : null;
    }

    private List<ServerPlayer> randomTargets(int count) {
        List<ServerPlayer> result = new ArrayList<>(targets());
        for (int i = result.size() - 1; i > 0; i--) {
            Collections.swap(result, i, random.nextInt(i + 1));
        }
        return result.subList(0, Math.min(count, result.size()));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !isAlive()) return;
        if (!configApplied) {
            SilkConfig.reapply(this);
            configApplied = true;
        }

        ServerPlayer tank = chooseTank();
        if (tank == null || !tank.isAlive()) {
            // 目标丢失时不要把半截技能冻住；下次重新进战后从干净状态继续。
            if (castAction != 0) {
                castAction = 0;
                castAge = 0;
                castDuration = 0;
                castTarget = null;
                castPoint = null;
                flameHitPlayers.clear();
                entityData.set(ANIMATION, 0);
            }
            getNavigation().stop();
            entityData.set(WALKING, false);
            return;
        }

        if (!engaged) beginEncounter();
        for (ServerPlayer player : targets()) fighters.computeIfAbsent(player.getUUID(), ignored -> new Fighter());

        int phase = phase();
        entityData.set(MAD, phase == 2);
        if (phase >= 2 && !enteredP2) enterPhaseTwo();
        if (phase == 3 && !enteredP3) enterPhaseThree();

        updateHazards();
        updateFighters();
        if (!isAlive()) return;

        setTarget(tank);
        getLookControl().setLookAt(tank, 30.0F, 30.0F);

        if (castAction != 0) {
            getNavigation().stop();
            tickCast();
        } else {
            if (distanceToSqr(tank) > 64.0D || !hasLineOfSight(tank)) {
                getNavigation().moveTo(tank, 1.0D);
            } else {
                getNavigation().stop();
            }
            schedule(tank);
        }

        double dx = getX() - previousX;
        double dz = getZ() - previousZ;
        entityData.set(WALKING, dx * dx + dz * dz > 1.0E-5D);
        previousX = getX();
        previousZ = getZ();
    }

    private void beginEncounter() {
        engaged = true;
        previousX = getX();
        previousZ = getZ();
        int now = tickCount;
        nextDecisionTick = now;

        // 首轮不再使用 5/9/13 秒这种无来源的手写时间；直接从解析到的技能 CD 开始计时。
        nextBasic = now + SilkBalance.BASIC_COOLDOWN;
        nextBats = now + SilkBalance.BAT_COOLDOWN;
        nextMeteor = now + SilkBalance.METEOR_COOLDOWN;
        nextFlame = now + SilkBalance.FLAME_COOLDOWN;

        // 尚未进入对应阶段的技能不提前计时，避免刚切阶段就一股脑全放。
        nextSweep = Integer.MAX_VALUE;
        nextSummon = Integer.MAX_VALUE;
        nextPlague = Integer.MAX_VALUE;
        nextBurst = Integer.MAX_VALUE;
        nextBlackWater = Integer.MAX_VALUE;
        nextBlackBall = Integer.MAX_VALUE;
        nextFireOrb = Integer.MAX_VALUE;
        nextFireRain = Integer.MAX_VALUE;
        nextPillar = Integer.MAX_VALUE;
        nextHeartFire = Integer.MAX_VALUE;
    }

    private void enterPhaseTwo() {
        enteredP2 = true;
        announce("§5斯尔克进入疯狂阶段：黑暗泰迪、黑暗史莱姆与黑暗疫病出现！");

        // P2 从进入阶段这一刻开始自己的 CD，不继承 P1 已经过掉的时间。
        nextSweep = tickCount + SilkBalance.SWEEP_COOLDOWN;
        nextSummon = tickCount + SilkBalance.SUMMON_COOLDOWN;
        nextPlague = tickCount + SilkBalance.PLAGUE_COOLDOWN;
        nextBurst = tickCount + SilkBalance.BURST_COOLDOWN;

        // 助战小樱 7451~7454 同样从 P2 开始计时，杜绝切阶段瞬间四个辅助效果同时刷出。
        nextFireOrb = tickCount + SilkBalance.SUPPORT_FIRE_ORB_COOLDOWN;
        nextFireRain = tickCount + SilkBalance.SUPPORT_FIRE_RAIN_COOLDOWN;
        nextPillar = tickCount + SilkBalance.SUPPORT_PILLAR_COOLDOWN;
        nextHeartFire = tickCount + SilkBalance.SUPPORT_HEART_FIRE_COOLDOWN;
        nextDecisionTick = Math.max(nextDecisionTick, tickCount + 20);
    }

    private void enterPhaseThree() {
        enteredP3 = true;
        announce("§4斯尔克进入狂暴阶段：腐蚀黑水与黑暗能量球出现！");
        nextSweep = tickCount + SilkBalance.SWEEP_COOLDOWN;
        nextBlackWater = tickCount + SilkBalance.BLACK_WATER_COOLDOWN;
        nextBlackBall = tickCount + SilkBalance.BLACK_BALL_COOLDOWN;
        nextDecisionTick = Math.max(nextDecisionTick, tickCount + 20);
    }

    private void schedule(ServerPlayer tank) {
        if (tickCount < nextDecisionTick) return;

        int phase = phase();
        if (phase == 1) {
            if (tickCount >= nextFlame) {
                begin(ACT_FLAME, tank);
                return;
            }
            if (tickCount >= nextMeteor) {
                begin(ACT_METEOR, tank);
                return;
            }
            if (tickCount >= nextBats) {
                begin(ACT_BATS, tank);
                return;
            }
        } else if (phase == 2) {
            if (tickCount >= nextSummon) {
                begin(ACT_SUMMON, tank);
                return;
            }
            if (tickCount >= nextPlague) {
                begin(ACT_PLAGUE, tank);
                return;
            }
            if (tickCount >= nextBurst) {
                begin(ACT_BURST, tank);
                return;
            }
            if (tickCount >= nextSweep) {
                begin(ACT_SWEEP, tank);
                return;
            }
        } else {
            if (tickCount >= nextBlackWater) {
                begin(ACT_BLACK_WATER, tank);
                return;
            }
            if (tickCount >= nextBlackBall) {
                begin(ACT_BLACK_BALL, tank);
                return;
            }
            if (tickCount >= nextSweep) {
                begin(ACT_SWEEP, tank);
                return;
            }
        }

        if (tickCount >= nextBasic && distanceToSqr(tank) <= 24.0D * 24.0D && hasLineOfSight(tank)) {
            begin(ACT_BASIC, tank);
        }
    }

    private int animationFor(int action) {
        return switch (action) {
            case ACT_BASIC -> 1;                    // attack_01
            case ACT_BATS, ACT_SUMMON -> 2;        // attack_02
            case ACT_METEOR, ACT_BLACK_WATER, ACT_BLACK_BALL -> 3; // attack_03
            case ACT_SWEEP -> 4;                    // attack_04
            case ACT_PLAGUE -> 5;                   // attack_05
            case ACT_BURST -> 6;                    // attack_06
            case ACT_FLAME -> 7;                    // attack_07
            default -> 0;
        };
    }

    private int durationForAnimation(int animation) {
        return switch (animation) {
            case 1 -> 28;   // 192~233 @30fps
            case 2 -> 50;   // 236~310 @30fps
            case 3 -> 32;   // 313~359 @30fps
            case 4 -> 23;   // 362~395 @30fps
            case 5 -> 58;   // 398~483 @30fps
            case 6 -> 54;   // 486~566 @30fps
            case 7 -> 172;  // 398~483 @10fps
            default -> 20;
        };
    }

    private void begin(int action, ServerPlayer target) {
        faceTargetForAttack(target);
        castAction = action;
        castAge = 0;
        castTarget = target.getUUID();
        castPoint = target.position();
        int animation = animationFor(action);
        castDuration = durationForAnimation(animation);
        entityData.set(ANIMATION, animation);
        entityData.set(CAST_SERIAL, entityData.get(CAST_SERIAL) + 1);

        if (action == ACT_FLAME) {
            flameHitPlayers.clear();
            Vec3 direction = castPoint.subtract(position());
            flameYaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
            setYRot(flameYaw);
            yBodyRot = flameYaw;
            announce("§6斯尔克正在蓄力暗火喷射：离开正面 120° 扇形！");
        }
    }

    private void tickCast() {
        castAge++;
        if (castAction == ACT_FLAME) {
            setYRot(flameYaw);
            yBodyRot = flameYaw;
            if (castAge < 90 && castAge % 2 == 0) chargeFlame();
            if (castAge >= 90 && castAge <= 110) {
                sprayFlameVisual();
                // 原 7505 仍是一轮一次主伤害；持续判定只为避免玩家晚 1 tick 进入火里却完全不受伤。
                if (castAge % 2 == 0) fanDamage();
            }
        } else {
            int trigger = switch (castAction) {
                case ACT_BASIC -> 12;
                case ACT_BATS, ACT_SUMMON -> 20;
                case ACT_METEOR, ACT_BLACK_WATER, ACT_BLACK_BALL -> 14;
                case ACT_SWEEP -> 10;
                case ACT_PLAGUE -> 24;
                case ACT_BURST -> 28;
                default -> 12;
            };
            if (castAge == trigger) executeAction(castAction);
        }

        if (castAge >= castDuration) {
            int finished = castAction;
            castAction = 0;
            castAge = 0;
            castDuration = 0;
            castTarget = null;
            castPoint = null;
            entityData.set(ANIMATION, 0);
            finishCooldown(finished);
        }
    }

    /**
     * 技能 CD 从动作真正结束后开始；同时给所有已经到点的技能一个公共恢复窗口，
     * 防止长动画期间其它技能全部到点，随后无缝连续倾泻。
     */
    private void finishCooldown(int action) {
        switch (action) {
            case ACT_BASIC -> nextBasic = tickCount + SilkBalance.BASIC_COOLDOWN;
            case ACT_BATS -> nextBats = tickCount + SilkBalance.BAT_COOLDOWN;
            case ACT_METEOR -> nextMeteor = tickCount + SilkBalance.METEOR_COOLDOWN;
            case ACT_FLAME -> nextFlame = tickCount + SilkBalance.FLAME_COOLDOWN;
            case ACT_SWEEP -> nextSweep = tickCount + SilkBalance.SWEEP_COOLDOWN;
            case ACT_SUMMON -> nextSummon = tickCount + SilkBalance.SUMMON_COOLDOWN;
            case ACT_PLAGUE -> nextPlague = tickCount + SilkBalance.PLAGUE_COOLDOWN;
            case ACT_BURST -> nextBurst = tickCount + SilkBalance.BURST_COOLDOWN;
            case ACT_BLACK_WATER -> nextBlackWater = tickCount + SilkBalance.BLACK_WATER_COOLDOWN;
            case ACT_BLACK_BALL -> nextBlackBall = tickCount + SilkBalance.BLACK_BALL_COOLDOWN;
            default -> {
            }
        }

        nextDecisionTick = tickCount + 20; // 1 秒公共恢复。
        deferReadyBossSkills();
    }

    private void deferReadyBossSkills() {
        int defer = nextDecisionTick;
        if (nextBasic <= tickCount) nextBasic = defer;
        if (nextBats <= tickCount) nextBats = defer;
        if (nextMeteor <= tickCount) nextMeteor = defer;
        if (nextFlame <= tickCount) nextFlame = defer;
        if (nextSweep <= tickCount) nextSweep = defer;
        if (nextSummon <= tickCount) nextSummon = defer;
        if (nextPlague <= tickCount) nextPlague = defer;
        if (nextBurst <= tickCount) nextBurst = defer;
        if (nextBlackWater <= tickCount) nextBlackWater = defer;
        if (nextBlackBall <= tickCount) nextBlackBall = defer;
    }

    private void executeAction(int action) {
        switch (action) {
            case ACT_BASIC -> basic();
            case ACT_BATS -> bats();
            case ACT_METEOR -> meteor();
            case ACT_SWEEP -> sweep();
            case ACT_SUMMON -> summonMonsters();
            case ACT_PLAGUE -> plague();
            case ACT_BURST -> burst();
            case ACT_BLACK_WATER -> markBlackWater();
            case ACT_BLACK_BALL -> summonBlackBall();
            default -> {
            }
        }
    }

    public boolean hit(ServerPlayer player, float amount, int corruption) {
        if (!isAlive() || !valid(player)) return false;
        float damage = amount * (phase() == 3 ? SilkBalance.PHASE_THREE_MULTIPLIER : 1.0F);
        boolean hit = hurtWithoutKnockback(player, damageSources().indirectMagic(this, this), damage);
        if (hit && corruption > 0) corrupt(player, corruption);
        return hit;
    }

    private void basic() {
        ServerPlayer target = player(castTarget);
        if (target == null) return;
        Vec3 center = target.position();
        spawnDarkHit(center.add(0.0D, 1.0D, 0.0D));
        for (ServerPlayer player : targets()) {
            if (player.position().distanceToSqr(center) <= SilkBalance.BASIC_RADIUS * SilkBalance.BASIC_RADIUS) {
                if (hit(player, (float) getAttributeValue(Attributes.ATTACK_DAMAGE), SilkBalance.BASIC_CORRUPTION)) {
                    // 7500：2285，被击中 +5 黑暗能量。
                    addBlackEnergy(player, SilkBalance.BLACK_ENERGY_PER_HIT);
                }
            }
        }
        level().playSound(null, blockPosition(), ModSounds.SILK_ATT1.get(), SoundSource.HOSTILE, 1.4F, 1.0F);
    }

    private void bats() {
        for (ServerPlayer target : randomTargets(3)) {
            SilkBat bat = SilkContent.BAT.get().create(level());
            if (bat == null) continue;
            Vec3 start = position().add(0.0D, 2.0D, 0.0D);
            bat.moveTo(start.x, start.y, start.z, getYRot(), 0.0F);
            bat.launch(this, target.getEyePosition());
            level().addFreshEntity(bat);
            summons.add(bat.getUUID());
            target.displayClientMessage(Component.literal("§5多重蝙蝠锁定了你当前的位置，横向躲开！"), true);
        }
    }

    private void meteor() {
        List<ServerPlayer> list = randomTargets(1);
        if (list.isEmpty()) return;
        ServerPlayer target = list.get(0);
        Fighter fighter = fighters.computeIfAbsent(target.getUUID(), ignored -> new Fighter());
        fighter.rootPoint = target.position();
        fighter.rootUntil = tickCount + SilkBalance.METEOR_ROOT_TICKS;
        meteorMarks.add(new MeteorMark(target.getUUID(), fighter.rootPoint, fighter.rootUntil));
        spawnCircle(fighter.rootPoint, SilkVisualCircle.PURPLE_METEOR, SilkBalance.METEOR_ROOT_TICKS);
        target.displayClientMessage(Component.literal("§5黑暗流星点名：你被禁锢！队友进入紫圈帮你分摊！"), false);
    }

    public void resolveMeteorImpact(Vec3 impact) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        List<ServerPlayer> splitters = new ArrayList<>();
        for (ServerPlayer player : targets()) {
            if (player.position().distanceToSqr(impact)
                    <= SilkBalance.METEOR_SPLIT_RADIUS * SilkBalance.METEOR_SPLIT_RADIUS) {
                splitters.add(player);
            }
        }

        serverLevel.sendParticles(ModParticles.SILK_DARK_FIRE.get(), impact.x, impact.y + 0.5D, impact.z,
                110, 2.2D, 1.2D, 2.2D, 0.09D);
        serverLevel.sendParticles(ModParticles.SILK_SMOKE.get(), impact.x, impact.y + 0.5D, impact.z,
                75, 2.8D, 1.0D, 2.8D, 0.06D);

        if (splitters.isEmpty()) {
            // 7502“流星灭团AOE”：没有任何人进入分摊圈时，全体受击并各 +10 黑暗能量。
            for (ServerPlayer player : targets()) {
                hit(player, SilkBalance.METEOR_DAMAGE, 0);
                addBlackEnergy(player, SilkBalance.BLACK_ENERGY_PER_HIT * 2);
            }
            announce("§4黑暗流星无人分摊，触发全场冲击！");
            return;
        }

        float share = SilkBalance.METEOR_DAMAGE / splitters.size();
        for (ServerPlayer player : splitters) {
            hit(player, share, 0);
            // 7501：2285 + 2288。
            addBlackEnergy(player, SilkBalance.BLACK_ENERGY_PER_HIT);
        }
    }

    private void sweep() {
        level().playSound(null, blockPosition(), ModSounds.SILK_ATT4.get(), SoundSource.HOSTILE, 1.4F, 1.0F);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.SILK_DARK_FIRE.get(), getX(), getY() + 1.0D, getZ(),
                    55, 2.8D, 0.6D, 2.8D, 0.05D);
        }
        for (ServerPlayer player : targets()) {
            if (distanceToSqr(player) <= SilkBalance.SWEEP_RADIUS * SilkBalance.SWEEP_RADIUS) {
                hit(player, SilkBalance.SWEEP_DAMAGE, SilkBalance.SWEEP_CORRUPTION);
            }
        }
    }

    private void summonMonsters() {
        SilkDarkTeddy teddy = SilkContent.DARK_TEDDY.get().create(level());
        if (teddy != null) {
            Vec3 point = randomFloor();
            teddy.moveTo(point.x, point.y, point.z, random.nextFloat() * 360.0F, 0.0F);
            teddy.setOwner(this);
            level().addFreshEntity(teddy);
            summons.add(teddy.getUUID());
        }
        for (int i = 0; i < 3; i++) {
            SilkDarkSlime slime = SilkContent.DARK_SLIME.get().create(level());
            if (slime == null) continue;
            Vec3 point = randomFloor();
            slime.moveTo(point.x, point.y, point.z, 0.0F, 0.0F);
            slime.setOwner(this);
            level().addFreshEntity(slime);
            summons.add(slime.getUUID());
        }
        announce("§5斯尔克召唤黑暗泰迪与黑暗史莱姆！史莱姆需用助战火雨解除无敌。");
    }

    private void plague() {
        List<ServerPlayer> candidates = new ArrayList<>(targets());
        candidates.removeIf(player -> {
            Fighter fighter = fighters.computeIfAbsent(player.getUUID(), ignored -> new Fighter());
            return fighter.plagueDue > tickCount;
        });
        if (candidates.isEmpty()) return;
        ServerPlayer target = candidates.get(random.nextInt(candidates.size()));
        infect(target);
        announce("§4黑暗疫病点名：" + target.getScoreboardName() + "，"
                + Math.max(0, SilkBalance.PLAGUE_TICKS / 20) + "秒结束前靠近另一名队友完成传染！");
    }

    private void infect(ServerPlayer player) {
        Fighter fighter = fighters.computeIfAbsent(player.getUUID(), ignored -> new Fighter());
        fighter.plagueDue = tickCount + SilkBalance.PLAGUE_TICKS;
        SilkCombatEvents.setPlague(player, SilkBalance.PLAGUE_TICKS);
    }

    private void transferPlague(ServerPlayer source) {
        Fighter sourceState = fighters.computeIfAbsent(source.getUUID(), ignored -> new Fighter());
        SilkCombatEvents.clearPlague(source);
        sourceState.plagueDue = 0;

        ServerPlayer target = targets().stream()
                .filter(player -> player != source)
                .filter(player -> player.distanceToSqr(source)
                        <= SilkBalance.PLAGUE_TRANSFER_RADIUS * SilkBalance.PLAGUE_TRANSFER_RADIUS)
                .filter(player -> fighters.computeIfAbsent(player.getUUID(), ignored -> new Fighter()).plagueDue <= tickCount)
                .min(Comparator.comparingDouble(player -> player.distanceToSqr(source)))
                .orElse(null);

        if (target == null) {
            source.displayClientMessage(Component.literal("§4黑暗疫病未找到传染目标，受到致命伤害！"), false);
            source.kill();
            return;
        }

        Fighter targetState = fighters.computeIfAbsent(target.getUUID(), ignored -> new Fighter());
        if (targetState.hostUntil > tickCount) {
            target.displayClientMessage(Component.literal("§4短时间内再次成为疫病宿主，黑暗能量失控！"), false);
            target.kill(); // 2318：99999 直接伤害。
            return;
        }

        sourceState.hostUntil = tickCount + SilkBalance.PLAGUE_HOST_MARK_TICKS;
        targetState.hostUntil = tickCount + SilkBalance.PLAGUE_HOST_MARK_TICKS;
        infect(target);
        spawnPlagueTransfer(source, target);
        announce("§d黑暗疫病：" + source.getScoreboardName() + " → " + target.getScoreboardName());
    }

    private void burst() {
        announce("§4黑暗能量爆发：3秒后每名玩家脚下都会发生追加AOE，请分散！");
        for (ServerPlayer player : targets()) {
            hit(player, SilkBalance.BURST_DAMAGE, SilkBalance.BURST_INITIAL_CORRUPTION);
            echoes.add(new Echo(player.getUUID(), player.position(), tickCount + SilkBalance.BURST_ECHO_DELAY_TICKS));
        }
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.SILK_GUSH.get(), getX(), getY() + 0.3D, getZ(),
                    180, 6.0D, 0.7D, 6.0D, 0.08D);
        }
    }

    private void markBlackWater() {
        for (ServerPlayer player : randomTargets(3)) {
            Fighter fighter = fighters.computeIfAbsent(player.getUUID(), ignored -> new Fighter());
            fighter.blackWaterDue = tickCount + SilkBalance.BLACK_WATER_DELAY_TICKS;
            player.displayClientMessage(Component.literal("§8腐蚀黑水："
                    + Math.max(0, SilkBalance.BLACK_WATER_DELAY_TICKS / 20) + "秒后将在你脚下生成，提前散开！"), true);
        }
    }

    private void summonBlackBall() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        long active = serverLevel.getEntitiesOfClass(
                        SilkBlackBall.class,
                        new AABB(homePosition(), homePosition()).inflate(SilkBalance.ARENA_RADIUS),
                        Entity::isAlive)
                .stream().count();
        if (active >= 4L) return;

        SilkBlackBall ball = SilkContent.BLACK_BALL.get().create(level());
        if (ball == null) return;
        Vec3 point = randomFloor().add(0.0D, 1.2D, 0.0D);
        ball.moveTo(point.x, point.y, point.z, 0.0F, 0.0F);
        ball.setOwner(this);
        level().addFreshEntity(ball);
        summons.add(ball.getUUID());
        announce("§5黑暗能量球出现：尽快集火摧毁！");
    }

    private Vec3 flameHandPosition() {
        double yaw = Math.toRadians(getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 right = new Vec3(forward.z, 0.0D, -forward.x);
        return position()
                // 旧版 0.68×4.4≈3 格高，视觉会从玩家头顶穿过去；下压到胸口高度。
                .add(0.0D, getBbHeight() * 0.52D, 0.0D)
                .add(forward.scale(0.55D))
                .add(right.scale(0.48D));
    }

    private void chargeFlame() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        Vec3 hand = flameHandPosition();
        serverLevel.sendParticles(ModParticles.SILK_FIRE.get(), hand.x, hand.y, hand.z,
                7, 0.22D, 0.22D, 0.22D, 0.025D);
        serverLevel.sendParticles(ModParticles.SILK_DARK_FIRE.get(), hand.x, hand.y, hand.z,
                4, 0.15D, 0.15D, 0.15D, 0.012D);
    }

    private void sprayFlameVisual() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        Vec3 hand = flameHandPosition();
        int visualSteps = Math.max(1, (int) Math.ceil(SilkBalance.FLAME_HALF_ANGLE_DEGREES / 10.0D));
        double visualStepDegrees = SilkBalance.FLAME_HALF_ANGLE_DEGREES / visualSteps;
        for (int i = -visualSteps; i <= visualSteps; i++) {
            double angle = Math.toRadians(flameYaw + i * visualStepDegrees);
            Vec3 direction = new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle));
            for (double distance = 0.7D; distance <= SilkBalance.FLAME_RANGE; distance += 1.0D) {
                Vec3 point = hand.add(direction.scale(distance));
                // 随距离向地面压低，使喷火视觉覆盖玩家身体而不是悬在头顶。
                double y = point.y - Math.min(1.50D, 0.15D + distance * 0.14D);
                serverLevel.sendParticles(ModParticles.SILK_DARK_FIRE.get(), point.x, y, point.z,
                        2, 0.22D, 0.30D, 0.22D, 0.01D);
                serverLevel.sendParticles(ModParticles.SILK_SOUL.get(), point.x, y, point.z,
                        1, 0.18D, 0.25D, 0.18D, 0.008D);
            }
        }
    }

    private void fanDamage() {
        Vec3 forward = new Vec3(-Math.sin(Math.toRadians(flameYaw)), 0.0D, Math.cos(Math.toRadians(flameYaw)));
        for (ServerPlayer player : targets()) {
            if (flameHitPlayers.contains(player.getUUID())) continue;

            Vec3 delta = player.getBoundingBox().getCenter().subtract(position());
            Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
            if (horizontal.lengthSqr() < 1.0E-6D || horizontal.length() > SilkBalance.FLAME_RANGE) continue;
            if (Math.abs(delta.y) > 4.0D) continue;

            double dot = horizontal.normalize().dot(forward);
            if (dot < Math.cos(Math.toRadians(SilkBalance.FLAME_HALF_ANGLE_DEGREES))) continue;

            if (hit(player, SilkBalance.FLAME_DAMAGE, 0)) {
                flameHitPlayers.add(player.getUUID());
                addBlackEnergy(player, SilkBalance.BLACK_ENERGY_PER_HIT);
            }
        }
    }

    private void updateHazards() {
        if (!(level() instanceof ServerLevel serverLevel)) return;

        // 10秒禁锢结束后才真正落下流星。
        for (Iterator<MeteorMark> iterator = meteorMarks.iterator(); iterator.hasNext();) {
            MeteorMark mark = iterator.next();
            if (tickCount < mark.due()) continue;
            Fighter fighter = fighters.get(mark.target());
            if (fighter != null) fighter.rootUntil = 0;
            SilkMeteor meteor = SilkContent.METEOR.get().create(level());
            if (meteor != null) {
                meteor.configure(this, mark.point());
                level().addFreshEntity(meteor);
                summons.add(meteor.getUUID());
            }
            iterator.remove();
        }

        // 能量爆发 3秒后追加 AOE；重叠区域按命中次数叠加。
        Map<ServerPlayer, Integer> echoHits = new HashMap<>();
        for (Iterator<Echo> iterator = echoes.iterator(); iterator.hasNext();) {
            Echo echo = iterator.next();
            if (tickCount < echo.due()) continue;
            ServerPlayer marked = player(echo.target());
            Vec3 point = marked == null ? echo.fallback() : marked.position();
            serverLevel.sendParticles(ModParticles.SILK_GUSH.get(), point.x, point.y + 0.1D, point.z,
                    85, 1.8D, 0.5D, 1.8D, 0.06D);
            for (ServerPlayer player : targets()) {
                if (player.position().distanceToSqr(point)
                        <= SilkBalance.BURST_ECHO_RADIUS * SilkBalance.BURST_ECHO_RADIUS) {
                    echoHits.merge(player, 1, Integer::sum);
                }
            }
            iterator.remove();
        }
        echoHits.forEach((player, count) ->
                hit(player, SilkBalance.BURST_DAMAGE * 0.6F * count,
                        SilkBalance.BURST_ECHO_CORRUPTION * count));

        if (phase() >= 2) updateSupport(serverLevel);
    }

    private void updateSupport(ServerLevel serverLevel) {
        if (tickCount >= nextFireOrb) {
            Vec3 point = randomFloor().add(0.0D, 0.8D, 0.0D);
            int life = 60 * 20; // NPC746 原资源存在时间 60 秒。
            fireOrbs.add(new SupportOrb(point, tickCount + life));
            spawnCircle(point, SilkVisualCircle.FIRE_ORB, life);
            nextFireOrb = tickCount + SilkBalance.SUPPORT_FIRE_ORB_COOLDOWN;
        }
        if (tickCount >= nextFireRain) {
            Vec3 point = randomFloor();
            int life = 45 * 20; // NPC750 原资源存在时间 45 秒。
            fireRain.add(new SupportZone(point, tickCount + life));
            spawnCircle(point, SilkVisualCircle.RED_FIRE_RAIN, life);
            nextFireRain = tickCount + SilkBalance.SUPPORT_FIRE_RAIN_COOLDOWN;
        }
        if (tickCount >= nextPillar) {
            Vec3 point = randomFloor();
            int life = 60 * 20; // NPC747 心火光柱原资源存在时间 60 秒。
            pillars.add(new SupportPillar(point, tickCount + life));
            spawnCircle(point, SilkVisualCircle.HEART_PILLAR, life);
            nextPillar = tickCount + SilkBalance.SUPPORT_PILLAR_COOLDOWN;
        }
        if (tickCount >= nextHeartFire) {
            for (ServerPlayer player : targets()) {
                Fighter fighter = fighters.computeIfAbsent(player.getUUID(), ignored -> new Fighter());
                fighter.heartFire = Math.min(SilkBalance.MAX_METER, fighter.heartFire + 10);
                fighter.heartFireUntil = tickCount + SilkBalance.HEART_FIRE_TICKS;
                player.displayClientMessage(Component.literal("§6获得心火庇护 ×10：每层可清除一格腐蚀黑水"), true);
            }
            nextHeartFire = tickCount + SilkBalance.SUPPORT_HEART_FIRE_COOLDOWN;
        }

        // NPC746：存在 60 秒，每秒对 2 格内玩家施加一层 2283 强化火焰，而不是碰一下就把火球吃掉。
        for (Iterator<SupportOrb> iterator = fireOrbs.iterator(); iterator.hasNext();) {
            SupportOrb orb = iterator.next();
            if (tickCount >= orb.expires()) {
                iterator.remove();
                continue;
            }
            serverLevel.sendParticles(ModParticles.SILK_FIRE.get(), orb.point().x, orb.point().y, orb.point().z,
                    4, 0.25D, 0.25D, 0.25D, 0.02D);

            if (tickCount % 20 == 0) {
                for (ServerPlayer player : targets()) {
                    if (player.position().distanceToSqr(orb.point()) > 2.0D * 2.0D) continue;
                    Fighter fighter = fighters.computeIfAbsent(player.getUUID(), ignored -> new Fighter());
                    fighter.fireStacks = Math.min(SilkBalance.MAX_METER, fighter.fireStacks + 1);
                    fighter.fireUntil = tickCount + SilkBalance.STRENGTHENED_FIRE_TICKS;
                    SilkCombatEvents.setStrengthenedFire(player, fighter.fireStacks, SilkBalance.STRENGTHENED_FIRE_TICKS);
                    player.displayClientMessage(Component.literal("§6强化火焰 +1（每层伤害 +10%）"), true);
                }
            }
        }

        // NPC750：45 秒火雨区；每秒解除史莱姆 2292 无敌并维持火雨灼烧/减速表现。
        for (Iterator<SupportZone> iterator = fireRain.iterator(); iterator.hasNext();) {
            SupportZone zone = iterator.next();
            if (tickCount >= zone.expires()) {
                iterator.remove();
                continue;
            }
            if (tickCount % 4 == 0) {
                serverLevel.sendParticles(ModParticles.SILK_FIRE.get(), zone.point().x, zone.point().y + 2.0D, zone.point().z,
                        14, SilkBalance.SUPPORT_ZONE_RADIUS * 0.65D, 0.8D,
                        SilkBalance.SUPPORT_ZONE_RADIUS * 0.65D, 0.07D);
            }
            if (tickCount % 20 == 0) {
                AABB area = new AABB(zone.point(), zone.point()).inflate(
                        SilkBalance.SUPPORT_ZONE_RADIUS, 3.0D, SilkBalance.SUPPORT_ZONE_RADIUS);
                for (SilkDarkSlime slime : serverLevel.getEntitiesOfClass(SilkDarkSlime.class, area, Entity::isAlive)) {
                    slime.breakShield();
                    slime.hurt(damageSources().indirectMagic(this, this), 12.0F);
                    slime.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 50, 1));
                }
            }
        }

        // NPC747 / 7471：60 秒，1 秒一次，2 格范围清除 10 层心智。
        for (Iterator<SupportPillar> iterator = pillars.iterator(); iterator.hasNext();) {
            SupportPillar pillar = iterator.next();
            if (tickCount >= pillar.expires()) {
                iterator.remove();
                continue;
            }
            if (tickCount % 4 == 0) {
                for (int y = 0; y < 8; y++) {
                    serverLevel.sendParticles(ModParticles.SILK_SOUL.get(), pillar.point().x,
                            pillar.point().y + 0.3D + y * 0.6D, pillar.point().z,
                            3, 0.25D, 0.16D, 0.25D, 0.01D);
                }
            }
            for (ServerPlayer player : targets()) {
                if (player.position().distanceToSqr(pillar.point()) > 2.0D * 2.0D) continue;
                Fighter fighter = fighters.computeIfAbsent(player.getUUID(), ignored -> new Fighter());
                if (tickCount - fighter.lastPillarCleanse >= 20) {
                    fighter.lastPillarCleanse = tickCount;
                    fighter.corruption = Math.max(0, fighter.corruption - 10); // 2297。
                }
            }
        }
    }

    private void updateFighters() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        for (Map.Entry<UUID, Fighter> entry : new ArrayList<>(fighters.entrySet())) {
            ServerPlayer player = online(entry.getKey());
            Fighter fighter = entry.getValue();
            if (player == null || !valid(player)) {
                fighters.remove(entry.getKey());
                continue;
            }

            if (fighter.rootUntil > tickCount && fighter.rootPoint != null) {
                if (player.isPassenger()) player.stopRiding();
                DungeonTeleportGuard.runInternal(() ->
                        player.teleportTo(fighter.rootPoint.x, fighter.rootPoint.y, fighter.rootPoint.z));
                player.setDeltaMovement(Vec3.ZERO);
            }

            if (fighter.plagueDue > tickCount && tickCount % 10 == 0) {
                serverLevel.sendParticles(ModParticles.SILK_SOUL.get(), player.getX(), player.getY() + 2.2D, player.getZ(),
                        5, 0.35D, 0.25D, 0.35D, 0.008D);
            }
            if (fighter.plagueDue > 0 && tickCount >= fighter.plagueDue) transferPlague(player);

            if (fighter.blackWaterDue > 0 && tickCount >= fighter.blackWaterDue) {
                fighter.blackWaterDue = 0;
                spawnBlackWater(player.position(), 0);
            }

            if (fighter.heartFireUntil <= tickCount) fighter.heartFire = 0;
            if (fighter.fireUntil <= tickCount) fighter.fireStacks = 0;

            if (tickCount % 20 == 0) {
                StringBuilder text = new StringBuilder()
                        .append("§5心智 ").append(fighter.corruption).append("/").append(SilkBalance.MAX_METER)
                        .append(" §8黑暗能量 ").append(fighter.blackEnergy).append("/").append(SilkBalance.MAX_METER);
                if (fighter.plagueDue > tickCount) {
                    text.append(" §4疫病 ").append((fighter.plagueDue - tickCount + 19) / 20).append("秒");
                }
                if (fighter.heartFire > 0) text.append(" §6心火×").append(fighter.heartFire);
                if (fighter.fireStacks > 0) text.append(" §c强化火焰×").append(fighter.fireStacks);
                if (fighter.madUntil > tickCount) text.append(" §d疯狂");
                player.displayClientMessage(Component.literal(text.toString()), true);
            }
        }
    }

    public void corrupt(ServerPlayer player, int amount) {
        if (amount <= 0 || !valid(player)) return;
        Fighter fighter = fighters.computeIfAbsent(player.getUUID(), ignored -> new Fighter());
        fighter.corruption = Math.min(SilkBalance.MAX_METER, fighter.corruption + amount);
        if (fighter.corruption >= SilkBalance.MAX_METER && player.isAlive()) {
            player.displayClientMessage(Component.literal("§4心智腐蚀达到" + SilkBalance.MAX_METER + "层，你失去神志！"), false);
            SilkCombatEvents.lock(player);
            player.kill();
        }
    }

    public void addBlackEnergy(ServerPlayer player, int amount) {
        if (amount <= 0 || !valid(player)) return;
        Fighter fighter = fighters.computeIfAbsent(player.getUUID(), ignored -> new Fighter());
        if (fighter.madUntil > tickCount) return;
        fighter.blackEnergy = Math.min(SilkBalance.MAX_METER, fighter.blackEnergy + amount);
        if (fighter.blackEnergy >= SilkBalance.MAX_METER) {
            fighter.blackEnergy = 0;
            fighter.madUntil = tickCount + SilkBalance.MADNESS_TICKS;
            SilkCombatEvents.setMadness(player, SilkBalance.MADNESS_TICKS);
            player.displayClientMessage(Component.literal("§d黑暗能量达到" + SilkBalance.MAX_METER + "层：陷入疯狂"
                    + Math.max(0, SilkBalance.MADNESS_TICKS / 20) + "秒，移速降低但伤害大幅提升！"), false);
        }
    }

    public boolean consumeHeartFire(ServerPlayer player) {
        Fighter fighter = fighters.get(player.getUUID());
        if (fighter == null || fighter.heartFireUntil <= tickCount || fighter.heartFire <= 0) return false;
        fighter.heartFire--;
        player.displayClientMessage(Component.literal("§6心火庇护消耗1层，清除一格腐蚀黑水"), true);
        return true;
    }

    public void spawnBlackWater(Vec3 point, int depth) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        Vec3 floor = floorPoint(point.x, point.z);
        AABB near = new AABB(floor, floor).inflate(1.0D, 1.0D, 1.0D);
        if (!serverLevel.getEntitiesOfClass(SilkBlackWater.class, near, Entity::isAlive).isEmpty()) return;

        AABB arena = new AABB(homePosition(), homePosition()).inflate(SilkBalance.ARENA_RADIUS);
        long count = serverLevel.getEntitiesOfClass(SilkBlackWater.class, arena, Entity::isAlive).stream().count();
        if (count >= 128L) return;

        SilkBlackWater water = SilkContent.BLACK_WATER.get().create(level());
        if (water == null) return;
        water.moveTo(floor.x, floor.y + 0.03D, floor.z, 0.0F, 0.0F);
        water.configure(this, depth);
        level().addFreshEntity(water);
        summons.add(water.getUUID());
    }

    public void spreadBlackWater(Vec3 origin, int depth) {
        Vec3 home = homePosition();
        Vec3[] points = {
                origin.add(SilkBalance.BLACK_WATER_SPREAD_DISTANCE, 0.0D, 0.0D), origin.add(-SilkBalance.BLACK_WATER_SPREAD_DISTANCE, 0.0D, 0.0D),
                origin.add(0.0D, 0.0D, SilkBalance.BLACK_WATER_SPREAD_DISTANCE), origin.add(0.0D, 0.0D, -SilkBalance.BLACK_WATER_SPREAD_DISTANCE)
        };
        for (Vec3 point : points) {
            if (point.distanceToSqr(home) <= SilkBalance.ARENA_RADIUS * SilkBalance.ARENA_RADIUS) {
                spawnBlackWater(point, depth);
            }
        }
    }

    public void spawnDarkHit(Vec3 point) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        serverLevel.sendParticles(ModParticles.SILK_DARK_FIRE.get(), point.x, point.y, point.z,
                45, 0.9D, 0.8D, 0.9D, 0.055D);
    }

    private void spawnPlagueTransfer(ServerPlayer from, ServerPlayer to) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        Vec3 start = from.getEyePosition();
        Vec3 end = to.getEyePosition();
        Vec3 delta = end.subtract(start);
        int steps = Math.max(4, (int) Math.ceil(delta.length() * 3.0D));
        for (int i = 0; i <= steps; i++) {
            Vec3 point = start.add(delta.scale(i / (double) steps));
            serverLevel.sendParticles(ModParticles.SILK_SOUL.get(), point.x, point.y, point.z,
                    1, 0.05D, 0.05D, 0.05D, 0.0D);
        }
    }

    private void spawnCircle(Vec3 point, int style, int life) {
        SilkVisualCircle circle = SilkContent.VISUAL_CIRCLE.get().create(level());
        if (circle == null) return;
        Vec3 floor = floorPoint(point.x, point.z);
        circle.moveTo(floor.x, floor.y + 0.04D, floor.z, 0.0F, 0.0F);
        circle.configure(style, life);
        level().addFreshEntity(circle);
        summons.add(circle.getUUID());
    }

    private Vec3 homePosition() {
        Vec3 spawn = getSpawnPosition();
        return spawn == null ? position() : spawn;
    }

    private Vec3 floorPoint(double x, double z) {
        Vec3 home = homePosition();
        BlockPos start = BlockPos.containing(x, home.y + 6.0D, z);
        for (int i = 0; i < 16; i++) {
            BlockPos at = start.below(i);
            if (level().getBlockState(at.below()).isSolidRender(level(), at.below())
                    && level().getBlockState(at).getCollisionShape(level(), at).isEmpty()
                    && level().getBlockState(at.above()).getCollisionShape(level(), at.above()).isEmpty()) {
                return new Vec3(x, at.getY(), z);
            }
        }
        return new Vec3(x, home.y, z);
    }

    private Vec3 randomFloor() {
        Vec3 home = homePosition();
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double radius = 5.0D + random.nextDouble() * 13.0D;
        return floorPoint(home.x + Math.cos(angle) * radius, home.z + Math.sin(angle) * radius);
    }

    public void aoe(Vec3 point, double radius, float damage, int corruption) {
        for (ServerPlayer player : targets()) {
            if (player.position().distanceToSqr(point) <= radius * radius) {
                hit(player, damage, corruption);
            }
        }
    }

    private void announce(String message) {
        for (ServerPlayer player : targets()) player.displayClientMessage(Component.literal(message), false);
    }

    private void cleanup() {
        if (level() instanceof ServerLevel serverLevel) {
            for (UUID id : summons) {
                Entity entity = serverLevel.getEntity(id);
                if (entity != null) entity.discard();
            }
            for (UUID id : fighters.keySet()) {
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(id);
                if (player != null) SilkCombatEvents.clearCombatBuffs(player);
            }
        }
        summons.clear();
        flameHitPlayers.clear();
        fighters.clear();
        meteorMarks.clear();
        echoes.clear();
        fireOrbs.clear();
        fireRain.clear();
        pillars.clear();
    }

    private void resetSilkCombatState() {
        cleanup();
        getNavigation().stop();
        setTarget(null);
        castAction = 0;
        castAge = 0;
        castDuration = 0;
        castTarget = null;
        castPoint = null;
        engaged = false;
        enteredP2 = false;
        enteredP3 = false;
        nextDecisionTick = 0;
        flameHitPlayers.clear();
        entityData.set(ANIMATION, 0);
        entityData.set(MAD, false);
        entityData.set(WALKING, false);
    }

    @Override
    protected void onNetcraftDisengageStarted() {
        resetSilkCombatState();
    }

    @Override
    protected void onNetcraftFightReset() {
        resetSilkCombatState();
        getHatredManager().clearCombatStatistics();
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            level().playSound(null, blockPosition(), ModSounds.SILK_DEATH.get(), SoundSource.HOSTILE, 2.0F, 1.0F);
        }
        super.die(source);
        if (!level().isClientSide) {
            cleanup();
            getHatredManager().clearAll();
            entityData.set(ANIMATION, -1);
        }
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (!level().isClientSide && deathTime >= 40) remove(Entity.RemovalReason.KILLED);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide) cleanup();
        super.remove(reason);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (getSpawnPosition() == null && tag.contains("SilkHomeX")) {
            setSpawnPosition(new Vec3(tag.getDouble("SilkHomeX"), tag.getDouble("SilkHomeY"), tag.getDouble("SilkHomeZ")));
        }
        setHealth(getMaxHealth());
        resetSilkCombatState();
    }

    @Override public int getIconAtlasU() { return 1152; }
    @Override public int getIconAtlasV() { return 2; }
    @Override public int getIconWidth() { return 106; }
    @Override public int getIconHeight() { return 95; }
}
