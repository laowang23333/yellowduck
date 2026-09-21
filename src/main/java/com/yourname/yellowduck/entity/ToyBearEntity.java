package com.yourname.yellowduck.entity;

import com.yourname.yellowduck.event.ToyBearEntangleEvents;
import com.yourname.yellowduck.particle.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stargazer 1.1.3-beta 的 EntityBossPuppetTeddyDec 机制移植。
 *
 * 只保留 YellowDuck 自己的布偶熊 GLB/动画；战斗循环按 Stargazer 熊还原：
 * - 普攻两段交替，3 秒 CD，8 tick 后结算；
 * - 技能1：30 秒一次，30 格群体缓慢 III，持续 5 秒；
 * - 技能2：P2/P3，50 秒一次，蓄力 8 秒，点名后瞬移斩；多人贴近时分摊，单吃 4 倍；
 * - 技能3：P3，20 秒一次，随机点名并召唤 500 血缠绕守卫；守卫活着时目标不能移动且只能攻击守卫；
 * - 小樱死亡后狂暴：攻击力立即 x2，之后每 10 秒再 x1.5；
 * - 熊死亡时通知小樱狂暴。
 *
 * 原包没有熊技能专属 PNG 粒子，因此技能视觉用 YellowDuck 自带 SAKURA_BEAR_* 粒子替代。
 */
public class ToyBearEntity extends PathfinderMob {
    public static final EntityDataAccessor<Boolean> RAGING =
            SynchedEntityData.defineId(ToyBearEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> ATTACKING =
            SynchedEntityData.defineId(ToyBearEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> ATTACK_SERIAL =
            SynchedEntityData.defineId(ToyBearEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> DYING =
            SynchedEntityData.defineId(ToyBearEntity.class, EntityDataSerializers.BOOLEAN);

    private static final double ORIGINAL_MAX_HEALTH = 20000.0D;
    private static final double ORIGINAL_MOVE_SPEED = 0.30D;
    private static final double ORIGINAL_ATTACK_DAMAGE = 15.0D;
    private static final double ORIGINAL_FOLLOW_RANGE = 48.0D;

    private static final int MELEE_DEFENSE = 30;
    private static final int RANGED_DEFENSE = 10;
    private static final int MAGIC_DEFENSE = 30;
    private static final float FIXED_DAMAGE_REDUCTION = 0.50F;

    private static final int NORMAL_ATTACK_COOLDOWN = 60;
    private static final int NORMAL_ATTACK_DAMAGE_DELAY = 8;
    private static final double NORMAL_ATTACK_RANGE = 2.5D;

    private static final int SKILL1_COOLDOWN = 600;
    private static final int SKILL2_COOLDOWN = 1000;
    private static final int SKILL2_CHARGE_TICKS = 160;
    private static final double SKILL2_SHARE_RADIUS = 2.0D;
    private static final int SKILL3_COOLDOWN = 400;

    private static final int ENRAGE_INTERVAL = 200;
    private static final int DEATH_TICKS = 60;

    private int normalAttackCooldown;
    private int attackCombo;
    private int skill1Timer = SKILL1_COOLDOWN;
    private int skill2Timer = SKILL2_COOLDOWN;
    private int skill3Timer = SKILL3_COOLDOWN;

    private boolean skill2Charging;
    private int skill2ChargeTimer;
    private UUID skill2TargetUUID;

    private UUID pendingDamageTarget;
    private int pendingDamageDelay;

    private int attackVisualTicks;
    private int deathTimer = -1;
    private boolean enraged;
    private int enrageTimer;

    private UUID ownerSakura;

    /** 简化复刻 NetCraft 仇恨：默认 10 点，实际受伤额外叠加，基础仇恨每秒衰减。 */
    private final Map<UUID, Double> hatred = new HashMap<>();
    private int hatredTick;

    public ToyBearEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // Stargazer 熊的追击/攻击由实体自己的 tick 驱动，避免原版 MeleeAttackGoal 重复结算。
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(RAGING, false);
        entityData.define(ATTACKING, false);
        entityData.define(ATTACK_SERIAL, 0);
        entityData.define(DYING, false);
    }

    @Override
    public Component getName() {
        return Component.literal("布偶熊");
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        if (entityData.get(DYING)) {
            tickDeathState();
            return;
        }

        if (!isAlive()) return;

        tickCooldowns();
        tickAttackVisual();
        tickSkill2();
        tickPendingDamage();
        tickEnrage();
        tickHatred();
        tickOwnerLink();

        if (skill2Charging) {
            getNavigation().stop();
            setDeltaMovement(Vec3.ZERO);
            return;
        }

        tickSkills();
    }

    private void tickCooldowns() {
        if (normalAttackCooldown > 0) normalAttackCooldown--;
        if (skill1Timer > 0) skill1Timer--;
        if (skill2Timer > 0) skill2Timer--;
        if (skill3Timer > 0) skill3Timer--;
    }

    private void tickAttackVisual() {
        if (attackVisualTicks > 0) {
            attackVisualTicks--;
            if (attackVisualTicks == 0) entityData.set(ATTACKING, false);
        }
    }

    private void playAttackVisual(int ticks) {
        entityData.set(ATTACKING, true);
        entityData.set(ATTACK_SERIAL, entityData.get(ATTACK_SERIAL) + 1);
        attackVisualTicks = Math.max(1, ticks);
    }

    private void tickOwnerLink() {
        SakurawitchEntity sakura = owner();
        if (sakura != null && (sakura.getEntityData().get(SakurawitchEntity.IS_DYING) || !sakura.isAlive())) {
            triggerEnrage();
        }
    }

    private void tickHatred() {
        if (++hatredTick < 20) return;
        hatredTick = 0;

        // NetCraft 熊会把可战斗玩家至少放进 10 点仇恨表。
        for (Player player : combatPlayers(ORIGINAL_FOLLOW_RANGE)) {
            hatred.merge(player.getUUID(), 10.0D, Math::max);
        }

        Iterator<Map.Entry<UUID, Double>> it = hatred.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Double> entry = it.next();
            Player p = level().getPlayerByUUID(entry.getKey());
            if (!valid(p) || distanceToSqr(p) > ORIGINAL_FOLLOW_RANGE * ORIGINAL_FOLLOW_RANGE) {
                it.remove();
                continue;
            }
            entry.setValue(Math.max(10.0D, entry.getValue() * 0.75D));
        }

        Player target = findHighestHatredPlayer();
        setTarget(target);
    }

    private Player findHighestHatredPlayer() {
        return combatPlayers(ORIGINAL_FOLLOW_RANGE).stream()
                .max(Comparator.<Player>comparingDouble(p -> hatred.getOrDefault(p.getUUID(), 10.0D))
                        .thenComparingDouble(p -> -distanceToSqr(p)))
                .orElse(null);
    }

    private void tickSkills() {
        if (entityData.get(ATTACKING)) {
            getNavigation().stop();
            setDeltaMovement(Vec3.ZERO);
            return;
        }

        Player target = valid(getTarget()) ? (Player) getTarget() : findHighestHatredPlayer();
        if (!valid(target)) {
            getNavigation().stop();
            return;
        }
        setTarget(target);
        faceTarget(target);

        int phase = getPhase();
        if (phase == 3 && skill3Timer <= 0) {
            performSkill3();
            return;
        }
        if (phase >= 2 && skill2Timer <= 0) {
            performSkill2(target);
            return;
        }
        if (skill1Timer <= 0) {
            performSkill1();
            return;
        }

        if (distanceTo(target) <= NORMAL_ATTACK_RANGE && normalAttackCooldown <= 0) {
            performNormalAttack(target);
            return;
        }

        moveTowardsTarget(target);
    }

    private void moveTowardsTarget(Player target) {
        Vec3 dir = target.position().subtract(position()).normalize();
        double speed = getAttributeValue(Attributes.MOVEMENT_SPEED);
        Vec3 current = getDeltaMovement();
        setDeltaMovement(dir.x * speed, current.y, dir.z * speed);
    }

    private void performNormalAttack(Player target) {
        faceTarget(target);
        attackCombo = (attackCombo + 1) % 2;
        normalAttackCooldown = NORMAL_ATTACK_COOLDOWN;
        playAttackVisual(20);
        pendingDamageTarget = target.getUUID();
        pendingDamageDelay = NORMAL_ATTACK_DAMAGE_DELAY;
        playHostileSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, attackCombo == 0 ? 0.9F : 1.1F);
    }

    private void tickPendingDamage() {
        if (pendingDamageTarget == null || pendingDamageDelay <= 0) return;
        if (--pendingDamageDelay > 0) return;

        Player target = level().getPlayerByUUID(pendingDamageTarget);
        pendingDamageTarget = null;
        if (!valid(target)) return;

        // 原 Stargazer 这里按 UUID 找目标后直接结算，不再额外做近战距离判定。
        target.hurt(damageSources().mobAttack(this), getAttackDamage());
        target.setDeltaMovement(Vec3.ZERO);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ModParticles.SAKURA_BEAR_RAGE_SLASH.get(),
                    target.getX(), target.getY() + target.getBbHeight() * 0.55D, target.getZ(),
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** 技能1：30 格所有玩家缓慢 III 5 秒。 */
    private void performSkill1() {
        skill1Timer = SKILL1_COOLDOWN;
        playAttackVisual(20);

        for (Player player : combatPlayers(30.0D)) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2, false, false));
        }

        if (level() instanceof ServerLevel server) {
            server.sendParticles(ModParticles.SAKURA_BEAR_RAGE_GROUND_RING.get(),
                    getX(), getY() + 0.05D, getZ(), 1, 0, 0, 0, 0);
            server.sendParticles(ModParticles.SAKURA_BEAR_RAGE_MIST.get(),
                    getX(), getY() + 1.0D, getZ(), 18, 1.0D, 0.5D, 1.0D, 0.02D);
        }
        playHostileSound(SoundEvents.EVOKER_CAST_SPELL, 1.4F, 0.75F);
    }

    /** 技能2：P2/P3，8 秒蓄力点名后瞬移并结算分摊/单吃伤害。 */
    private void performSkill2(Player target) {
        faceTarget(target);
        skill2Charging = true;
        skill2ChargeTimer = SKILL2_CHARGE_TICKS;
        skill2TargetUUID = target.getUUID();
        skill2Timer = SKILL2_COOLDOWN;
        playAttackVisual(45);
        playHostileSound(SoundEvents.WARDEN_HEARTBEAT, 1.2F, 1.1F);
    }

    private void tickSkill2() {
        if (!skill2Charging) return;

        Player target = skill2TargetUUID == null ? null : level().getPlayerByUUID(skill2TargetUUID);
        if (valid(target)) {
            faceTarget(target);
            if (level() instanceof ServerLevel server && tickCount % 4 == 0) {
                server.sendParticles(ModParticles.SAKURA_WARNING.get(),
                        target.getX(), target.getY() + 0.05D, target.getZ(),
                        3, 0.45D, 0.03D, 0.45D, 0.01D);
                server.sendParticles(ModParticles.SAKURA_BEAR_RAGE_SPARK.get(),
                        getX(), getY() + 1.0D, getZ(),
                        2, 0.45D, 0.6D, 0.45D, 0.01D);
            }
        }

        if (--skill2ChargeTimer > 0) return;
        skill2Charging = false;
        executeSkill2Damage();
        playAttackVisual(32);
    }

    private void executeSkill2Damage() {
        Player target = skill2TargetUUID == null ? null : level().getPlayerByUUID(skill2TargetUUID);
        skill2TargetUUID = null;
        if (!valid(target)) return;

        teleportToTarget(target);
        faceTarget(target);

        List<Player> hit = new ArrayList<>();
        for (Player player : level().getEntitiesOfClass(Player.class, target.getBoundingBox().inflate(3.0D))) {
            if (valid(player) && player.distanceTo(target) <= SKILL2_SHARE_RADIUS) hit.add(player);
        }

        if (hit.size() >= 2) {
            float sharedDamage = getAttackDamage() * 2.0F / hit.size();
            for (Player player : hit) player.hurt(damageSources().mobAttack(this), sharedDamage);
        } else {
            for (Player player : hit) player.hurt(damageSources().mobAttack(this), getAttackDamage() * 4.0F);
        }

        if (level() instanceof ServerLevel server) {
            server.sendParticles(ModParticles.SAKURA_BEAR_RAGE_BURST.get(),
                    target.getX(), target.getY() + 0.5D, target.getZ(),
                    3, 0.5D, 0.3D, 0.5D, 0.01D);
            server.sendParticles(ModParticles.SAKURA_BEAR_RAGE_SLASH.get(),
                    target.getX(), target.getY() + 0.7D, target.getZ(),
                    10, 1.0D, 0.5D, 1.0D, 0.05D);
        }
        playHostileSound(SoundEvents.GENERIC_EXPLODE, 1.4F, 1.2F);
    }

    private void teleportToTarget(Player target) {
        Vec3 dir = target.position().subtract(position()).normalize();
        teleportTo(target.getX() - dir.x * 1.5D, target.getY(), target.getZ() - dir.z * 1.5D);
    }

    /** 技能3：P3 随机点名，召唤 500 血守卫并缠绕目标。 */
    private void performSkill3() {
        skill3Timer = SKILL3_COOLDOWN;
        playAttackVisual(40);

        Player target = findRandomUnentangledPlayer();
        if (target == null) return;

        faceTarget(target);
        spawnEntangleGuard(target);

        if (level() instanceof ServerLevel server) {
            server.sendParticles(ModParticles.SAKURA_BEAR_RAGE_GROUND_RING.get(),
                    target.getX(), target.getY() + 0.05D, target.getZ(),
                    1, 0, 0, 0, 0);
            server.sendParticles(ModParticles.SAKURA_BEAR_RAGE_MIST.get(),
                    target.getX(), target.getY() + 0.5D, target.getZ(),
                    10, 0.3D, 0.5D, 0.3D, 0.01D);
        }
        playHostileSound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.3F, 0.8F);
    }

    private void spawnEntangleGuard(Player target) {
        if (!(level() instanceof ServerLevel server)) return;

        Entity guardEntity = null;
        EntityType<?> netcraftGuardType = ForgeRegistries.ENTITY_TYPES.getValue(
                new ResourceLocation("netcraft", "entity_elite_t1_skeleton"));
        if (netcraftGuardType != null) {
            try {
                guardEntity = netcraftGuardType.create(server);
            } catch (Throwable ignored) {
            }
        }
        if (!(guardEntity instanceof Mob)) {
            guardEntity = EntityType.SKELETON.create(server);
        }
        if (!(guardEntity instanceof Mob guard)) return;

        double angle = random.nextDouble() * Math.PI * 2.0D;
        guard.moveTo(
                target.getX() + Math.cos(angle) * 2.0D,
                target.getY(),
                target.getZ() + Math.sin(angle) * 2.0D,
                target.getYRot() + 180.0F,
                0.0F
        );

        var maxHealth = guard.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(500.0D);
        guard.setHealth(500.0F);
        guard.setTarget(target);
        guard.setPersistenceRequired();

        ToyBearEntangleEvents.markGuard(guard, target);
        ToyBearEntangleEvents.markGuardOwner(guard, this);
        server.addFreshEntity(guard);
        ToyBearEntangleEvents.entangle(target, guard);
    }

    private Player findRandomUnentangledPlayer() {
        List<Player> players = new ArrayList<>();
        for (Player p : combatPlayers(30.0D)) {
            if (!ToyBearEntangleEvents.isEntangled(p)) players.add(p);
        }
        if (players.isEmpty()) return null;
        return players.get(random.nextInt(players.size()));
    }

    public void triggerEnrage() {
        if (enraged || level().isClientSide) return;
        enraged = true;
        entityData.set(RAGING, true);
        enrageTimer = 0;

        var attack = getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) attack.setBaseValue(attack.getBaseValue() * 2.0D);

        if (level() instanceof ServerLevel server) {
            server.sendParticles(ModParticles.SAKURA_BEAR_RAGE_BURST.get(),
                    getX(), getY() + 1.0D, getZ(),
                    20, 0.5D, 1.0D, 0.5D, 0.02D);
        }
        playHostileSound(SoundEvents.WITHER_SPAWN, 1.5F, 1.1F);
    }

    /** 兼容小樱当前联动调用。 */
    public void onOwnerSakuraDeath() {
        triggerEnrage();
    }

    private void tickEnrage() {
        if (!enraged) return;
        enrageTimer++;
        if (enrageTimer < ENRAGE_INTERVAL) return;
        enrageTimer = 0;

        var attack = getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) attack.setBaseValue(attack.getBaseValue() * 1.5D);

        if (level() instanceof ServerLevel server) {
            server.sendParticles(ModParticles.SAKURA_BEAR_RAGE_MIST.get(),
                    getX(), getY() + 1.0D, getZ(),
                    5, 0.5D, 0.5D, 0.5D, 0.02D);
        }
    }

    private void playHostileSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        level().playSound(null, blockPosition(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    private void faceTarget(Player player) {
        if (player == null) return;
        double dx = player.getX() - getX();
        double dz = player.getZ() - getZ();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        setYRot(yaw);
        yHeadRot = yaw;
        yBodyRot = yaw;
        getLookControl().setLookAt(player, 90.0F, 90.0F);
    }

    private int getPhase() {
        float ratio = getMaxHealth() <= 0.0F ? 0.0F : getHealth() / getMaxHealth();
        if (ratio > 0.80F) return 1;
        if (ratio > 0.50F) return 2;
        return 3;
    }

    private float getAttackDamage() {
        return (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    private List<Player> combatPlayers(double radius) {
        return level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(radius), this::valid);
    }

    private boolean valid(Entity entity) {
        return entity instanceof Player p && valid(p);
    }

    private boolean valid(Player p) {
        return p != null && p.isAlive() && !p.isRemoved() && !p.isCreative() && !p.isSpectator();
    }

    public void setOwnerSakura(SakurawitchEntity sakura) {
        ownerSakura = sakura == null ? null : sakura.getUUID();
    }

    public UUID getOwnerSakura() {
        return ownerSakura;
    }

    private SakurawitchEntity owner() {
        if (!(level() instanceof ServerLevel server)) return null;
        if (ownerSakura != null) {
            Entity entity = server.getEntity(ownerSakura);
            if (entity instanceof SakurawitchEntity sakura) return sakura;
        }

        for (SakurawitchEntity sakura : level().getEntitiesOfClass(
                SakurawitchEntity.class, getBoundingBox().inflate(10.0D))) {
            if (!sakura.isRemoved()) {
                ownerSakura = sakura.getUUID();
                return sakura;
            }
        }
        return null;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (entityData.get(DYING)) return false;

        float before = getHealth();
        float adjusted = applyStargazerDefense(source, amount);
        boolean damaged = super.hurt(source, adjusted);
        if (damaged && !level().isClientSide) {
            float dealt = Math.max(0.0F, before - getHealth());
            Player attacker = resolvePlayerAttacker(source);
            if (attacker != null && dealt > 0.0F) {
                hatred.merge(attacker.getUUID(), dealt, Double::sum);
            }
        }
        return damaged;
    }

    private float applyStargazerDefense(DamageSource source, float amount) {
        float damage = Math.max(0.0F, amount);
        DamageClass type = classifyDamage(source);
        damage -= switch (type) {
            case MELEE -> MELEE_DEFENSE;
            case RANGED -> RANGED_DEFENSE;
            case MAGIC -> MAGIC_DEFENSE;
        };
        damage = Math.max(0.0F, damage) * (1.0F - FIXED_DAMAGE_REDUCTION);
        return Math.max(0.1F, damage);
    }

    private DamageClass classifyDamage(DamageSource source) {
        if (source.getDirectEntity() instanceof Projectile) return DamageClass.RANGED;
        String id = source.getMsgId().toLowerCase(java.util.Locale.ROOT);
        if (id.contains("magic") || id.contains("wither") || id.contains("dragonbreath")
                || id.contains("dragon_breath") || id.contains("sonic")) return DamageClass.MAGIC;
        return DamageClass.MELEE;
    }

    private Player resolvePlayerAttacker(DamageSource source) {
        if (source.getEntity() instanceof Player p) return p;
        if (source.getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof Player p) return p;
        return null;
    }

    private enum DamageClass { MELEE, RANGED, MAGIC }

    @Override
    public void die(DamageSource source) {
        if (level().isClientSide) {
            super.die(source);
            return;
        }
        if (entityData.get(DYING)) return;

        entityData.set(DYING, true);
        entityData.set(ATTACKING, false);
        deathTimer = 0;
        setInvulnerable(true);
        setHealth(0.0F);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        SakurawitchEntity sakura = owner();
        if (sakura != null && sakura.isAlive()
                && !sakura.getEntityData().get(SakurawitchEntity.IS_DYING)) {
            sakura.onToyBearDefeated();
        }
    }

    private void tickDeathState() {
        setInvulnerable(true);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        deathTimer++;

        if (level() instanceof ServerLevel server && deathTimer % 4 == 0) {
            server.sendParticles(ModParticles.SAKURA_BEAR_RAGE_MIST.get(),
                    getX(), getY() + 0.8D, getZ(), 4, 0.5D, 0.6D, 0.5D, 0.01D);
        }

        if (deathTimer >= DEATH_TICKS) {
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
        if (ownerSakura != null) tag.putUUID("OwnerSakura", ownerSakura);
        if (skill2TargetUUID != null) tag.putUUID("Skill2Target", skill2TargetUUID);
        if (pendingDamageTarget != null) tag.putUUID("PendingDamageTarget", pendingDamageTarget);
        tag.putInt("NormalAttackCD", normalAttackCooldown);
        tag.putInt("AttackCombo", attackCombo);
        tag.putInt("Skill1Timer", skill1Timer);
        tag.putInt("Skill2Timer", skill2Timer);
        tag.putInt("Skill3Timer", skill3Timer);
        tag.putBoolean("Skill2Charging", skill2Charging);
        tag.putInt("Skill2ChargeTimer", skill2ChargeTimer);
        tag.putInt("PendingDamageDelay", pendingDamageDelay);
        tag.putBoolean("Enraged", enraged);
        tag.putInt("EnrageTimer", enrageTimer);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("OwnerSakura")) ownerSakura = tag.getUUID("OwnerSakura");
        if (tag.hasUUID("Skill2Target")) skill2TargetUUID = tag.getUUID("Skill2Target");
        if (tag.hasUUID("PendingDamageTarget")) pendingDamageTarget = tag.getUUID("PendingDamageTarget");
        if (tag.contains("NormalAttackCD")) normalAttackCooldown = tag.getInt("NormalAttackCD");
        if (tag.contains("AttackCombo")) attackCombo = tag.getInt("AttackCombo");
        if (tag.contains("Skill1Timer")) skill1Timer = tag.getInt("Skill1Timer");
        if (tag.contains("Skill2Timer")) skill2Timer = tag.getInt("Skill2Timer");
        if (tag.contains("Skill3Timer")) skill3Timer = tag.getInt("Skill3Timer");
        if (tag.contains("Skill2Charging")) skill2Charging = tag.getBoolean("Skill2Charging");
        if (tag.contains("Skill2ChargeTimer")) skill2ChargeTimer = tag.getInt("Skill2ChargeTimer");
        if (tag.contains("PendingDamageDelay")) pendingDamageDelay = tag.getInt("PendingDamageDelay");
        if (tag.contains("Enraged")) enraged = tag.getBoolean("Enraged");
        if (tag.contains("EnrageTimer")) enrageTimer = tag.getInt("EnrageTimer");
        entityData.set(RAGING, enraged);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, ORIGINAL_MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, ORIGINAL_MOVE_SPEED)
                .add(Attributes.ATTACK_DAMAGE, ORIGINAL_ATTACK_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, ORIGINAL_FOLLOW_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }
}
