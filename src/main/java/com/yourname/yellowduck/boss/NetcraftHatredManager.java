package com.yourname.yellowduck.boss;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * NetCraft 1.4.18 HatredManager 的 YellowDuck 通用实现。
 * 所有数值均按普通 Boss 默认值还原；可由 NetcraftBossBase 的 hook 覆写。
 */
public final class NetcraftHatredManager {
    private static final int UPDATE_INTERVAL = 20;
    private static final int BASE_HATRED_EXPIRE_TICKS = 200;
    private static final int ATTACK_TIMEOUT_TICKS = 200;
    private static final int LOW_HATRED_DELAY_TICKS = 100;
    private static final int RETURN_HEAL_DELAY_TICKS = 60;
    private static final int DEFAULT_SWITCH_OBSERVATION_TICKS = 60;
    private static final double LOW_HATRED_THRESHOLD = 3.0D;

    private final NetcraftBossBase boss;
    private final Map<UUID, HateEntry> hatred = new HashMap<>();

    // NetCraft 还会记录每个玩家累计伤害；保留这份通用统计，供以后 Boss/结算使用。
    private final Map<UUID, Double> damageStatistics = new HashMap<>();

    private long lastUpdateTick = Long.MIN_VALUE;
    private long lastPlayerHatredTick = -1L;
    private long lastAttackActionTick = 0L;
    private long lowHatredSince = -1L;
    private long noPlayerSince = -1L;

    private UUID currentTarget;
    private UUID pendingTarget;
    private long pendingTargetSince;

    private boolean returning;
    private long returningSince;

    NetcraftHatredManager(NetcraftBossBase boss) {
        this.boss = boss;
    }

    public void tick() {
        if (boss.level().isClientSide || !boss.isAlive()) return;
        long now = boss.level().getGameTime();

        if (returning && now - returningSince >= RETURN_HEAL_DELAY_TICKS) {
            teleportHomeAndHeal();
            returning = false;
        }

        tickReturnMovement();

        if (lastUpdateTick != Long.MIN_VALUE && now - lastUpdateTick < UPDATE_INTERVAL) return;
        lastUpdateTick = now;

        if (boss.isHatredLocked()) {
            lowHatredSince = -1L;
            removeInvalidEntries();
            checkNoPlayersNearby(now);
            return;
        }

        if (boss.isEliteHatredMode()) {
            tickEliteMode(now);
            return;
        }

        if (checkSpawnDistance(now)) return;

        updateProximityHatred();
        addDetectionHatred();
        decayBaseHatred();
        expireOldBaseHatred(now);
        removeInvalidEntries();
        clearOutsideConfiguredRadius();

        if (checkAttackTimeout(now)) return;
        if (checkLowHatred(now)) return;
        if (checkNoPlayersNearby(now)) return;

        updateTarget(now);
    }

    /** 玩家实际对 Boss 造成伤害后调用。 */
    public void addDamageHatred(Player player, double actualDamage) {
        if (!boss.isValidHatredPlayer(player) || actualDamage <= 0.0D) return;

        damageStatistics.merge(player.getUUID(), actualDamage, Double::sum);

        double hatredAmount = actualDamage * boss.getHatredMultiplier(player);
        if (hatredAmount <= 0.0D) return;

        long now = boss.level().getGameTime();
        // 回位途中再次被玩家打到时，NetCraft 会重新进入战斗；取消待执行的回城/满血。
        returning = false;
        HateEntry entry = hatred.computeIfAbsent(player.getUUID(), id -> new HateEntry());
        entry.baseHatred += hatredAmount;
        entry.lastBaseHatredTick = now;
        lastPlayerHatredTick = now;

        // NetCraft 受击后会先把攻击者写入 vanilla target，稳定切换仍由仇恨逻辑决定。
        boss.setLastHurtByMob(player);
        boss.setTarget(player);
    }

    public void notifyAttackAction() {
        if (!boss.level().isClientSide) {
            lastAttackActionTick = boss.level().getGameTime();
        }
    }

    public Player getCurrentTarget() {
        if (currentTarget == null) return null;
        Player player = boss.level().getPlayerByUUID(currentTarget);
        return boss.isValidHatredPlayer(player) ? player : null;
    }

    public boolean hasCurrentTarget() {
        return getCurrentTarget() != null;
    }

    public double getHatred(Player player) {
        if (player == null) return 0.0D;
        HateEntry entry = hatred.get(player.getUUID());
        return entry == null ? 0.0D : entry.total();
    }

    public Set<UUID> getTrackedPlayerIds() {
        return Collections.unmodifiableSet(hatred.keySet());
    }

    public Map<UUID, Double> getDamageStatistics() {
        return Collections.unmodifiableMap(damageStatistics);
    }

    public void clearCombatStatistics() {
        damageStatistics.clear();
    }

    public void clearAll() {
        clearHatredOnly();
        returning = false;
        boss.setTarget(null);
        boss.getNavigation().stop();
    }

    private void clearHatredOnly() {
        hatred.clear();
        currentTarget = null;
        pendingTarget = null;
        pendingTargetSince = 0L;
        lowHatredSince = -1L;
        noPlayerSince = -1L;
        lastAttackActionTick = 0L;
    }

    private void tickEliteMode(long now) {
        addDetectionHatred();
        removeInvalidEntries();
        clearOutsideConfiguredRadius();
        updateTarget(now);
        checkNoPlayersNearby(now);
    }

    private void addDetectionHatred() {
        double radius = boss.getDetectionRadius();
        if (radius <= 0.0D) return;
        double radiusSq = radius * radius;
        double value = boss.getDetectionHatred();
        for (Player player : boss.level().players()) {
            if (!boss.isValidHatredPlayer(player)) continue;
            if (boss.distanceToSqr(player) <= radiusSq) {
                hatred.computeIfAbsent(player.getUUID(), ignored -> new HateEntry()).proximityHatred =
                        Math.max(value, hatred.get(player.getUUID()).proximityHatred);
            }
        }
    }

    private void updateProximityHatred() {
        Vec3 bossPos = boss.position();
        Vec3 spawn = boss.getSpawnPosition();
        Vec3 origin = spawn != null ? spawn : bossPos;
        double spawnLimit = boss.getHatredSpawnPlayerLimit();
        double spawnLimitSq = spawnLimit * spawnLimit;

        // 每次更新先清临时仇恨，随后按当前距离重新写入。
        for (HateEntry entry : hatred.values()) entry.proximityHatred = 0.0D;

        for (Player player : boss.level().players()) {
            if (!boss.isValidHatredPlayer(player)) continue;
            if (origin.distanceToSqr(player.position()) > spawnLimitSq) continue;

            double proximity = proximityHatred(bossPos.distanceTo(player.position()));
            if (proximity > 0.0D) {
                hatred.computeIfAbsent(player.getUUID(), ignored -> new HateEntry()).proximityHatred = proximity;
            }
        }
    }

    private static double proximityHatred(double distance) {
        if (distance <= 1.0D) return 4.0D;
        if (distance <= 2.0D) return 2.5D;
        if (distance <= 3.0D) return 1.5D;
        return 0.0D;
    }

    private void decayBaseHatred() {
        for (HateEntry entry : hatred.values()) {
            // NetCraft HatredEntry#decay(0.25): base *= (1 - 0.25)
            entry.baseHatred *= 0.75D;
            if (entry.baseHatred < 0.001D) entry.baseHatred = 0.0D;
        }
    }

    private void expireOldBaseHatred(long now) {
        for (HateEntry entry : hatred.values()) {
            if (entry.baseHatred > 0.0D && entry.lastBaseHatredTick > 0L
                    && now - entry.lastBaseHatredTick >= BASE_HATRED_EXPIRE_TICKS) {
                entry.baseHatred = 0.0D;
            }
        }
    }

    private void removeInvalidEntries() {
        Iterator<Map.Entry<UUID, HateEntry>> it = hatred.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, HateEntry> mapEntry = it.next();
            Player player = boss.level().getPlayerByUUID(mapEntry.getKey());
            if (!boss.isValidHatredPlayer(player) || mapEntry.getValue().total() <= 0.0D) {
                it.remove();
            }
        }
    }

    private void clearOutsideConfiguredRadius() {
        double radius = boss.getHatredClearRadius();
        if (radius > 10000.0D) return;
        double radiusSq = radius * radius;
        hatred.entrySet().removeIf(entry -> {
            Player player = boss.level().getPlayerByUUID(entry.getKey());
            return player == null || boss.distanceToSqr(player) > radiusSq;
        });
    }

    private boolean checkAttackTimeout(long now) {
        if (currentTarget == null || lastAttackActionTick <= 0L || !boss.shouldDisengageOnAttackTimeout()) {
            return false;
        }
        if (now - lastAttackActionTick < ATTACK_TIMEOUT_TICKS) return false;
        disengage(now);
        return true;
    }

    private boolean checkSpawnDistance(long now) {
        if (currentTarget == null || boss.shouldIgnoreSpawnDistanceLimit() || !boss.shouldDisengageOnDistance()) {
            return false;
        }
        Vec3 spawn = boss.getSpawnPosition();
        if (spawn == null) return false;
        if (boss.position().distanceTo(spawn) < boss.getSpawnDistanceLimit()) return false;
        disengage(now);
        return true;
    }

    private boolean checkLowHatred(long now) {
        if (!boss.shouldDisengageOnLowHatred()) return false;

        if (hatred.isEmpty()) {
            if (currentTarget != null || lastAttackActionTick > 0L) {
                disengage(now);
                return true;
            }
            lowHatredSince = -1L;
            return false;
        }

        boolean enough = hatred.values().stream().anyMatch(entry -> entry.total() >= LOW_HATRED_THRESHOLD);
        if (enough) {
            lowHatredSince = -1L;
            return false;
        }

        if (lowHatredSince < 0L) {
            lowHatredSince = now;
            return false;
        }
        if (now - lowHatredSince < LOW_HATRED_DELAY_TICKS) return false;

        disengage(now);
        return true;
    }

    private boolean checkNoPlayersNearby(long now) {
        double radius = boss.getNoPlayerDisengageRadius();
        double radiusSq = radius * radius;
        boolean anyNearby = boss.level().players().stream()
                .filter(boss::isValidHatredPlayer)
                .anyMatch(player -> boss.distanceToSqr(player) <= radiusSq);

        if (anyNearby) {
            noPlayerSince = -1L;
            return false;
        }

        if (noPlayerSince < 0L) {
            noPlayerSince = now;
            return false;
        }
        if (now - noPlayerSince < boss.getNoPlayerDisengageDelay()) return false;

        noPlayerSince = -1L;
        disengage(now);
        return true;
    }

    private void updateTarget(long now) {
        UUID best = findHighestHatredPlayer();
        if (best == null) {
            currentTarget = null;
            pendingTarget = null;
            boss.setTarget(null);
            boss.getNavigation().stop();
            return;
        }

        if (currentTarget == null || !boss.isValidHatredPlayer(boss.level().getPlayerByUUID(currentTarget))) {
            currentTarget = best;
            pendingTarget = null;
            if (lastAttackActionTick == 0L) lastAttackActionTick = now;
            applyVanillaTarget();
            return;
        }

        if (best.equals(currentTarget)) {
            pendingTarget = null;
            applyVanillaTarget();
            return;
        }

        double bestHatred = hatredOf(best);
        double currentHatred = hatredOf(currentTarget);
        double threshold = boss.getHatredSwitchThreshold();
        int configuredObservation = boss.getHatredSwitchObservationTicks();

        if (bestHatred > currentHatred + threshold) {
            if (configuredObservation <= 0) {
                currentTarget = best;
                pendingTarget = null;
                applyVanillaTarget();
                return;
            }
            if (observeCandidate(best, now, configuredObservation)) {
                currentTarget = best;
                pendingTarget = null;
            }
            applyVanillaTarget();
            return;
        }

        if (boss.isHatredSwitchInstant()) {
            currentTarget = best;
            pendingTarget = null;
            applyVanillaTarget();
            return;
        }

        int observation = configuredObservation > 0 ? configuredObservation : DEFAULT_SWITCH_OBSERVATION_TICKS;
        if (observeCandidate(best, now, observation)) {
            currentTarget = best;
            pendingTarget = null;
        }
        applyVanillaTarget();
    }

    private boolean observeCandidate(UUID best, long now, int ticks) {
        if (!best.equals(pendingTarget)) {
            pendingTarget = best;
            pendingTargetSince = now;
            return false;
        }
        return now - pendingTargetSince >= ticks;
    }

    private UUID findHighestHatredPlayer() {
        UUID best = null;
        double bestHatred = 0.0D;
        for (Map.Entry<UUID, HateEntry> entry : hatred.entrySet()) {
            Player player = boss.level().getPlayerByUUID(entry.getKey());
            if (!boss.isValidHatredPlayer(player)) continue;
            double value = entry.getValue().total();
            if (value > bestHatred) {
                bestHatred = value;
                best = entry.getKey();
            }
        }
        return best;
    }

    private double hatredOf(UUID id) {
        HateEntry entry = hatred.get(id);
        return entry == null ? 0.0D : entry.total();
    }

    private void applyVanillaTarget() {
        boss.setTarget(getCurrentTarget());
    }

    private void disengage(long now) {
        clearHatredOnly();
        boss.setTarget(null);
        boss.getNavigation().stop();
        boss.onNetcraftDisengageStarted();

        if (boss.shouldInstantHealOnDisengage()
                || lastPlayerHatredTick < 0L
                || now - lastPlayerHatredTick >= RETURN_HEAL_DELAY_TICKS) {
            teleportHomeAndHeal();
            returning = false;
        } else {
            returning = true;
            returningSince = now;
        }
    }

    private void tickReturnMovement() {
        if (!returning || currentTarget != null) return;
        Vec3 spawn = boss.getSpawnPosition();
        if (spawn == null) return;

        double stop = boss.getReturnToSpawnStopDistance();
        double dx = boss.getX() - spawn.x;
        double dz = boss.getZ() - spawn.z;
        if (dx * dx + dz * dz <= stop * stop) return;

        boss.getNavigation().moveTo(spawn.x, spawn.y, spawn.z, boss.getReturnToSpawnSpeed());
    }

    private void teleportHomeAndHeal() {
        boss.teleportToSpawn();
        boss.setHealth(boss.getMaxHealth());
        boss.onNetcraftFightReset();
    }

    private static final class HateEntry {
        double baseHatred;
        double proximityHatred;
        long lastBaseHatredTick;

        double total() {
            return baseHatred + proximityHatred;
        }
    }
}
