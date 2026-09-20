package com.yourname.yellowduck.cleopatra;

import com.yourname.yellowduck.boss.NetcraftBossBase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Stargazer 1.1.3-beta 精英艳后战斗逻辑移植。 */
public class CleopatraBoss extends NetcraftBossBase {
    public static final int ANIM_IDLE = 0;
    public static final int ANIM_ATTACK1 = 2;
    public static final int ANIM_ATTACK2 = 3;
    public static final int ANIM_ATTACK3 = 4;
    public static final int ANIM_DEATH = 5;

    public static final EntityDataAccessor<Integer> ANIM_STATE =
            SynchedEntityData.defineId(CleopatraBoss.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> CAST_SERIAL =
            SynchedEntityData.defineId(CleopatraBoss.class, EntityDataSerializers.INT);

    private int normalAttackCooldown;
    private int volleyCooldown;
    private int scorpionCooldown;
    private boolean attackAnimPlaying;
    private int attackAnimDuration;
    private UUID pendingDamageTarget;
    private int pendingDamageDelay;
    private int lastSeenPlayerTick;
    private int hatredScanTimer;
    private boolean sandworm75Spawned;
    private boolean sandworm50Spawned;
    private boolean autoDeathTriggered;
    private boolean deathSummoned;
    private boolean initialized;

    public CleopatraBoss(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setBaseTier(4);
        setBaseDamage(70);
        setPersistenceRequired();
        setCustomName(Component.literal("艳后"));
        setCustomNameVisible(true);
        volleyCooldown = CleopatraConfig.volleyFirstCd.get();
        scorpionCooldown = CleopatraConfig.scorpionFirstCd.get();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20000.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 70.0D)
                .add(Attributes.FOLLOW_RANGE, 3.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ANIM_STATE, ANIM_IDLE);
        entityData.define(CAST_SERIAL, 0);
    }

    public int getAttackState() {
        return entityData.get(ANIM_STATE);
    }

    private void setAttackState(int value) {
        entityData.set(ANIM_STATE, value);
    }

    private void playAnimation(int state, int ticks) {
        setAttackState(state);
        entityData.set(CAST_SERIAL, entityData.get(CAST_SERIAL) + 1);
        attackAnimPlaying = true;
        attackAnimDuration = Math.max(1, ticks);
        notifyAttackAction();
    }

    @Override public boolean isPushable() { return false; }
    @Override public boolean useAutomaticHatredManagerTick() { return false; }
    @Override public boolean isHatredLocked() { return true; }
    @Override public boolean shouldIgnoreSpawnDistanceLimit() { return true; }
    @Override public boolean shouldDisengageOnDistance() { return false; }
    @Override public boolean shouldDisengageOnLowHatred() { return false; }
    @Override public boolean shouldDisengageOnAttackTimeout() { return false; }
    @Override public double getNoPlayerDisengageRadius() { return 100000.0D; }
    @Override public boolean isPlayingAttackAnimation() { return attackAnimPlaying && getAttackState() != ANIM_DEATH; }

    @Override public int getMeleeDefense() { return CleopatraConfig.bossMeleeDefense.get().intValue(); }
    @Override public int getRangedDefense() { return CleopatraConfig.bossRangedDefense.get().intValue(); }
    @Override public int getMagicDefense() { return CleopatraConfig.bossMagicDefense.get().intValue(); }
    @Override public float getDamageReductionRatio() { return CleopatraConfig.bossReduction.get().floatValue(); }
    @Override public ResourceLocation getBossHudStandaloneIcon() {
        return new ResourceLocation("yellowduck", "textures/gui/boss_head/cleopatra.png");
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        if (!initialized && tickCount >= 1) {
            initialized = true;
            var hp = getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) hp.setBaseValue(CleopatraConfig.bossHealth.get());
            var ad = getAttribute(Attributes.ATTACK_DAMAGE);
            if (ad != null) ad.setBaseValue(CleopatraConfig.bossAttack.get());
            var fr = getAttribute(Attributes.FOLLOW_RANGE);
            if (fr != null) fr.setBaseValue(CleopatraConfig.hatredRange.get());
            setHealth(getMaxHealth());
            lastSeenPlayerTick = tickCount;
        }
        if (!isAlive()) return;

        if (!autoDeathTriggered && getHealth() <= getMaxHealth() * CleopatraConfig.autoDeathRatio.get()) {
            autoDeathTriggered = true;
            setHealth(0.0F);
            die(damageSources().indirectMagic(this, this));
            return;
        }

        if (normalAttackCooldown > 0) normalAttackCooldown--;
        if (volleyCooldown > 0) volleyCooldown--;
        if (scorpionCooldown > 0) scorpionCooldown--;
        tickPendingDamage();
        tickAttackAnimation();
        tickHatred();
        tickSkills();
    }

    private List<Player> playersInHatredRange() {
        double r = CleopatraConfig.hatredRange.get();
        return level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(r), CleopatraUtil::validPlayer);
    }

    private void tickHatred() {
        if (++hatredScanTimer < CleopatraConfig.hatredScanInterval.get()) return;
        hatredScanTimer = 0;

        List<Player> inRange = playersInHatredRange();
        if (!inRange.isEmpty()) {
            lastSeenPlayerTick = tickCount;
            for (Player player : inRange) {
                getHatredManager().addRawHatred(player, CleopatraConfig.hatredPerScan.get());
            }
        }

        for (UUID id : new ArrayList<>(getHatredManager().getTrackedPlayerIds())) {
            boolean stillInside = inRange.stream().anyMatch(p -> p.getUUID().equals(id));
            if (!stillInside) getHatredManager().clearHatred(id);
        }

        if (tickCount - lastSeenPlayerTick > CleopatraConfig.hatredLostTicks.get()) {
            getHatredManager().resetRawHatred();
            setTarget(null);
            if (!isDeadOrDying() && !autoDeathTriggered && getHealth() < getMaxHealth()) {
                setHealth(getMaxHealth());
            }
        }
    }

    private Player pickHatredTarget() {
        Player best = null;
        double highest = -1.0D;
        for (Player player : playersInHatredRange()) {
            double hatred = getHatredManager().getHatred(player);
            if (hatred > highest) {
                highest = hatred;
                best = player;
            }
        }
        return best;
    }

    private void tickAttackAnimation() {
        if (!attackAnimPlaying) return;
        if (--attackAnimDuration <= 0) {
            attackAnimPlaying = false;
            if (getAttackState() != ANIM_DEATH) setAttackState(ANIM_IDLE);
        }
    }

    private void tickPendingDamage() {
        if (pendingDamageTarget == null) return;
        if (pendingDamageDelay > 0 && --pendingDamageDelay > 0) return;

        UUID id = pendingDamageTarget;
        pendingDamageTarget = null;
        AABB box = getBoundingBox().inflate(30.0D);
        Player target = level().getEntitiesOfClass(Player.class, box,
                p -> p.isAlive() && p.getUUID().equals(id)).stream().findFirst().orElse(null);
        if (target != null) {
            hurtWithoutKnockback(target, target.damageSources().indirectMagic(this, this),
                    CleopatraConfig.bossAttack.get().floatValue());
        }
    }

    private void tickSkills() {
        if (attackAnimPlaying || getAttackState() == ANIM_DEATH) return;
        Player target = pickHatredTarget();
        if (target == null) {
            setAttackState(ANIM_IDLE);
            return;
        }

        double hpRatio = getHealth() / Math.max(1.0F, getMaxHealth());
        if (!sandworm75Spawned && hpRatio <= CleopatraConfig.sandworm75.get()) {
            sandworm75Spawned = true;
            summonSandworm(true, target);
            return;
        }
        if (!sandworm50Spawned && hpRatio <= CleopatraConfig.sandworm50.get()) {
            sandworm50Spawned = true;
            summonSandworm(false, target);
            return;
        }
        if (volleyCooldown <= 0) {
            volleyCooldown = CleopatraConfig.volleyCd.get();
            performVenomVolley(target);
            return;
        }
        if (scorpionCooldown <= 0) {
            scorpionCooldown = CleopatraConfig.scorpionCd.get();
            summonScorpions(target);
            return;
        }
        if (normalAttackCooldown <= 0) performNormalAttack(target);
        else setAttackState(ANIM_IDLE);
    }

    private void performNormalAttack(Player target) {
        double range = CleopatraConfig.meleeRange.get();
        if (distanceToSqr(target) > range * range) return;
        faceTargetForAttack(target);
        playAnimation(ANIM_ATTACK1, CleopatraConfig.normalAnimTicks.get());
        normalAttackCooldown = CleopatraConfig.normalCd.get();
        pendingDamageTarget = target.getUUID();
        pendingDamageDelay = CleopatraConfig.normalDamageDelay.get();
    }

    private void performVenomVolley(Player primary) {
        List<Player> pool = new ArrayList<>(playersInHatredRange());
        if (pool.isEmpty()) return;
        if (primary != null) faceTargetForAttack(primary);
        // 原版普通攻击和毒弹齐射共用 Attack1 动画。
        playAnimation(ANIM_ATTACK1, CleopatraConfig.volleyAnimTicks.get());

        int max = Math.min(CleopatraConfig.volleyTargets.get(), pool.size());
        for (int i = 0; i < max; i++) {
            int index = random.nextInt(pool.size());
            Player target = pool.remove(index);
            fireBulletAt(target, CleopatraConfig.volleyDamage.get().floatValue(), false);
        }
    }

    private void summonScorpions(Player primary) {
        if (primary != null) faceTargetForAttack(primary);
        playAnimation(ANIM_ATTACK2, CleopatraConfig.scorpionAnimTicks.get());

        double yaw = Math.toRadians(getYRot());
        double d = CleopatraConfig.scorpionSpawnDistance.get();
        double[][] original = {
                {Math.sin(yaw) * d, -Math.cos(yaw) * d},
                {-Math.cos(yaw) * d, -Math.sin(yaw) * d},
                {Math.cos(yaw) * d, Math.sin(yaw) * d}
        };
        int count = CleopatraConfig.scorpionCount.get();
        for (int i = 0; i < count; i++) {
            double ox;
            double oz;
            if (i < 3) {
                ox = original[i][0];
                oz = original[i][1];
            } else {
                double a = yaw + (Math.PI * 2.0D * i / Math.max(1, count));
                ox = Math.sin(a) * d;
                oz = -Math.cos(a) * d;
            }
            CleopatraScorpion scorpion = CleopatraEntities.SCORPION.get().create(level());
            if (scorpion == null) continue;
            scorpion.setPos(getX() + ox, getY(), getZ() + oz);
            scorpion.setOwnerId(getUUID());
            scorpion.setOwnerPos(getX(), getY(), getZ());
            level().addFreshEntity(scorpion);
        }
    }

    private void summonSandworm(boolean firstThreshold, Player primary) {
        if (primary != null) faceTargetForAttack(primary);
        playAnimation(ANIM_ATTACK3, CleopatraConfig.sandwormSummonAnimTicks.get());

        double yaw = Math.toRadians(getYRot());
        double offset = CleopatraConfig.sandwormSpawnOffset.get();
        double sign = firstThreshold ? -1.0D : 1.0D;
        CleopatraSandworm worm = CleopatraEntities.SANDWORM.get().create(level());
        if (worm == null) return;
        worm.setPos(getX() + Math.cos(yaw) * offset * sign,
                getY(), getZ() + Math.sin(yaw) * offset * sign);
        level().addFreshEntity(worm);
    }

    private void fireBulletAt(Player target, float damage, boolean sickness) {
        if (target == null || !target.isAlive()) return;
        CleopatraVenomBullet bullet = CleopatraEntities.VENOM_BULLET.get().create(level());
        if (bullet == null) return;
        bullet.setOwner(this);
        bullet.setDamage(damage);
        bullet.setApplySickness(sickness);
        bullet.setPos(getX(), getY() + 1.6D, getZ());

        double dx = target.getX() - getX();
        double dy = target.getY() + target.getBbHeight() * 0.6D - getY() - 1.6D;
        double dz = target.getZ() - getZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.01D) return;
        bullet.shoot(dx / len * 1.4D, dy / len * 1.4D, dz / len * 1.4D, 1.4F, 1.0F);
        level().addFreshEntity(bullet);
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        if (!level().isClientSide && !deathSummoned && !isRemoved()) {
            deathSummoned = true;
            setAttackState(ANIM_DEATH);
            entityData.set(CAST_SERIAL, entityData.get(CAST_SERIAL) + 1);
            CleopatraSnakeSummoner summoner = CleopatraEntities.SNAKE_SUMMONER.get().create(level());
            if (summoner != null) {
                summoner.setPos(getX(), getY(), getZ());
                summoner.setYRot(getYRot());
                level().addFreshEntity(summoner);
            }
        }
        super.die(source);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("NormalAttackCooldown", normalAttackCooldown);
        tag.putInt("VolleyCooldown", volleyCooldown);
        tag.putInt("ScorpionCooldown", scorpionCooldown);
        tag.putBoolean("Sandworm75Spawned", sandworm75Spawned);
        tag.putBoolean("Sandworm50Spawned", sandworm50Spawned);
        tag.putBoolean("AutoDeathTriggered", autoDeathTriggered);
        tag.putBoolean("DeathSummoned", deathSummoned);
        tag.putInt("LastSeenPlayerTick", lastSeenPlayerTick);
        if (pendingDamageTarget != null) tag.putUUID("PendingDamageTarget", pendingDamageTarget);
        tag.putInt("PendingDamageDelay", pendingDamageDelay);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        normalAttackCooldown = tag.getInt("NormalAttackCooldown");
        volleyCooldown = tag.contains("VolleyCooldown") ? tag.getInt("VolleyCooldown") : CleopatraConfig.volleyFirstCd.get();
        scorpionCooldown = tag.contains("ScorpionCooldown") ? tag.getInt("ScorpionCooldown") : CleopatraConfig.scorpionFirstCd.get();
        sandworm75Spawned = tag.getBoolean("Sandworm75Spawned");
        sandworm50Spawned = tag.getBoolean("Sandworm50Spawned");
        autoDeathTriggered = tag.getBoolean("AutoDeathTriggered");
        deathSummoned = tag.getBoolean("DeathSummoned");
        lastSeenPlayerTick = tag.getInt("LastSeenPlayerTick");
        if (tag.hasUUID("PendingDamageTarget")) pendingDamageTarget = tag.getUUID("PendingDamageTarget");
        pendingDamageDelay = tag.getInt("PendingDamageDelay");
    }
}
