package com.yourname.yellowduck.entity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 魔女小樱专用的 NetCraft 风格仇恨控制器。
 *
 * 按 NetCraft 1.4.18 的 BossBase / HatredManager 默认参数还原：
 * - 每 20 tick 更新一次仇恨；
 * - 伤害按玩家仇恨倍率转换为基础仇恨；
 * - 基础仇恨每秒衰减 25%；
 * - 10 秒未刷新某玩家的基础仇恨时清零；
 * - 1/2/3 格内分别提供 4/2.5/1.5 临时近身仇恨；
 * - 当前最高仇恨若高出目标 6 点则立即切换，否则连续领先约 60 tick 后切换；
 * - 仇恨持续低于 3 点约 100 tick、10 秒没有玩家在 32 格内、
 *   10 秒没有攻击动作，或离出生点达到 12 格时脱战；
 * - 脱战后回出生点并回满血，必要时最多等待约 60 tick 再强制回位。
 */
public final class SakuraHatredManager {

    private static final int UPDATE_INTERVAL = 20;
    private static final int BASE_HATRED_EXPIRE_TICKS = 200;
    private static final int ATTACK_TIMEOUT_TICKS = 200;
    private static final int LOW_HATRED_DELAY_TICKS = 100;
    private static final int NO_PLAYER_DELAY_TICKS = 200;
    private static final int RETURN_HEAL_DELAY_TICKS = 60;
    private static final int DEFAULT_SWITCH_OBSERVATION_TICKS = 60;

    private static final double LOW_HATRED_THRESHOLD = 3.0D;
    private static final double SWITCH_THRESHOLD = 6.0D;
    private static final double SPAWN_DISTANCE_LIMIT = 12.0D;
    private static final double NO_PLAYER_RADIUS = 32.0D;
    private static final double SPAWN_PLAYER_LIMIT_SQ = 10000.0D; // 100 格

    private static final ResourceLocation PROVOCATION_ID =
            new ResourceLocation("netcraft", "provocation");
    private static final ResourceLocation STEALTH_ID =
            new ResourceLocation("netcraft", "stealth");

    private final SakurawitchEntity boss;
    private final Map<UUID, HateEntry> hatred = new HashMap<>();

    private long lastUpdateTick = Long.MIN_VALUE;
    private long lastPlayerHatredTick = -1L;
    private long lastAttackActionTick = 0L;
    private long lowHatredSince = -1L;
    private long noPlayerSince = -1L;

    private UUID currentTarget;
    private UUID pendingTarget;
    private long pendingTargetSince = 0L;

    private boolean returning;
    private long returningSince = 0L;

    public SakuraHatredManager(SakurawitchEntity boss) {
        this.boss = boss;
    }

    public void tick() {
        if (boss.level().isClientSide || !boss.isAlive()) {
            return;
        }

        long now = boss.level().getGameTime();

        if (returning && now - returningSince >= RETURN_HEAL_DELAY_TICKS) {
            teleportHomeAndHeal();
            returning = false;
        }

        tickReturnMovement();

        if (lastUpdateTick != Long.MIN_VALUE && now - lastUpdateTick < UPDATE_INTERVAL) {
            return;
        }
        lastUpdateTick = now;

        updateProximityHatred();
        decayBaseHatred();
        expireOldBaseHatred(now);
        removeInvalidEntries();

        if (checkAttackTimeout(now)) {
            return;
        }
        if (checkSpawnDistance(now)) {
            return;
        }
        if (checkLowHatred(now)) {
            return;
        }
        if (checkNoPlayersNearby(now)) {
            return;
        }

        updateTarget(now);
    }

    public void addDamageHatred(Player player, double damageAmount) {
        if (!isValidPlayer(player) || damageAmount <= 0.0D) {
            return;
        }

        double hatredAmount = damageAmount * getNetcraftHatredMultiplier(player);
        if (hatredAmount <= 0.0D) {
            return;
        }

        long now = boss.level().getGameTime();
        HateEntry entry = hatred.computeIfAbsent(player.getUUID(), id -> new HateEntry());
        entry.baseHatred += hatredAmount;
        entry.lastBaseHatredTick = now;
        lastPlayerHatredTick = now;

        // NetCraft 的 LivingHurtEvent 会让 Boss 立刻把受击玩家设为 vanilla target，
        // 真正的稳定目标仍由仇恨管理器下一次更新决定。
        boss.setLastHurtByMob(player);
        boss.setTarget(player);
    }

    public void notifyAttackAction() {
        if (!boss.level().isClientSide) {
            lastAttackActionTick = boss.level().getGameTime();
        }
    }

    public Player getCurrentTarget() {
        if (currentTarget == null) {
            return null;
        }
        Player player = boss.level().getPlayerByUUID(currentTarget);
        return isValidPlayer(player) ? player : null;
    }

    public boolean hasCurrentTarget() {
        return getCurrentTarget() != null;
    }

    public double getHatred(Player player) {
        if (player == null) {
            return 0.0D;
        }
        HateEntry entry = hatred.get(player.getUUID());
        return entry == null ? 0.0D : entry.total();
    }

    public void clearAll() {
        hatred.clear();
        currentTarget = null;
        pendingTarget = null;
        pendingTargetSince = 0L;
        lowHatredSince = -1L;
        noPlayerSince = -1L;
        lastAttackActionTick = 0L;
        returning = false;
        boss.setTarget(null);
        boss.getNavigation().stop();
    }

    private void updateProximityHatred() {
        Vec3 bossPos = boss.position();
        Vec3 spawn = boss.getNetcraftSpawnPosition();
        Vec3 origin = spawn != null ? spawn : bossPos;

        for (Player player : boss.level().players()) {
            if (!isValidPlayer(player)) {
                continue;
            }
            if (origin.distanceToSqr(player.position()) > SPAWN_PLAYER_LIMIT_SQ) {
                continue;
            }

            double distance = bossPos.distanceTo(player.position());
            double proximityHatred = proximityHatred(distance);
            UUID id = player.getUUID();

            if (proximityHatred > 0.0D) {
                hatred.computeIfAbsent(id, ignored -> new HateEntry()).proximityHatred = proximityHatred;
            } else {
                HateEntry entry = hatred.get(id);
                if (entry != null) {
                    entry.proximityHatred = 0.0D;
                }
            }
        }
    }

    private static double proximityHatred(double distance) {
        if (distance <= 1.0D) {
            return 4.0D;
        }
        if (distance <= 2.0D) {
            return 2.5D;
        }
        if (distance <= 3.0D) {
            return 1.5D;
        }
        return 0.0D;
    }

    private void decayBaseHatred() {
        for (HateEntry entry : hatred.values()) {
            entry.baseHatred *= 0.75D;
            if (entry.baseHatred < 0.001D) {
                entry.baseHatred = 0.0D;
            }
        }
    }

    private void expireOldBaseHatred(long now) {
        for (HateEntry entry : hatred.values()) {
            if (entry.baseHatred > 0.0D
                    && entry.lastBaseHatredTick > 0L
                    && now - entry.lastBaseHatredTick >= BASE_HATRED_EXPIRE_TICKS) {
                entry.baseHatred = 0.0D;
            }
        }
    }

    private void removeInvalidEntries() {
        Iterator<Map.Entry<UUID, HateEntry>> iterator = hatred.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, HateEntry> mapEntry = iterator.next();
            Player player = boss.level().getPlayerByUUID(mapEntry.getKey());
            if (!isValidPlayer(player) || mapEntry.getValue().total() <= 0.0D) {
                iterator.remove();
            }
        }
    }

    private boolean checkAttackTimeout(long now) {
        if (currentTarget == null || lastAttackActionTick <= 0L) {
            return false;
        }
        if (now - lastAttackActionTick < ATTACK_TIMEOUT_TICKS) {
            return false;
        }
        disengage(now);
        return true;
    }

    private boolean checkSpawnDistance(long now) {
        if (currentTarget == null) {
            return false;
        }

        Vec3 spawn = boss.getNetcraftSpawnPosition();
        if (spawn == null) {
            return false;
        }

        if (boss.position().distanceTo(spawn) < SPAWN_DISTANCE_LIMIT) {
            return false;
        }

        disengage(now);
        return true;
    }

    private boolean checkLowHatred(long now) {
        if (hatred.isEmpty()) {
            if (currentTarget != null || lastAttackActionTick > 0L) {
                disengage(now);
                return true;
            }
            lowHatredSince = -1L;
            return false;
        }

        boolean hasEnoughHatred = hatred.values().stream()
                .anyMatch(entry -> entry.total() >= LOW_HATRED_THRESHOLD);

        if (hasEnoughHatred) {
            lowHatredSince = -1L;
            return false;
        }

        if (lowHatredSince < 0L) {
            lowHatredSince = now;
            return false;
        }

        if (now - lowHatredSince < LOW_HATRED_DELAY_TICKS) {
            return false;
        }

        disengage(now);
        return true;
    }

    private boolean checkNoPlayersNearby(long now) {
        boolean anyNearby = boss.level().players().stream()
                .filter(SakuraHatredManager::isValidPlayer)
                .anyMatch(player -> boss.distanceToSqr(player) <= NO_PLAYER_RADIUS * NO_PLAYER_RADIUS);

        if (anyNearby) {
            noPlayerSince = -1L;
            return false;
        }

        if (noPlayerSince < 0L) {
            noPlayerSince = now;
            return false;
        }

        if (now - noPlayerSince < NO_PLAYER_DELAY_TICKS) {
            return false;
        }

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

        if (currentTarget == null || !isValidPlayer(boss.level().getPlayerByUUID(currentTarget))) {
            currentTarget = best;
            pendingTarget = null;
            if (lastAttackActionTick == 0L) {
                lastAttackActionTick = now;
            }
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

        if (bestHatred > currentHatred + SWITCH_THRESHOLD) {
            currentTarget = best;
            pendingTarget = null;
            applyVanillaTarget();
            return;
        }

        if (!best.equals(pendingTarget)) {
            pendingTarget = best;
            pendingTargetSince = now;
            applyVanillaTarget();
            return;
        }

        if (now - pendingTargetSince >= DEFAULT_SWITCH_OBSERVATION_TICKS) {
            currentTarget = best;
            pendingTarget = null;
        }

        applyVanillaTarget();
    }

    private UUID findHighestHatredPlayer() {
        UUID best = null;
        double bestHatred = 0.0D;

        for (Map.Entry<UUID, HateEntry> entry : hatred.entrySet()) {
            Player player = boss.level().getPlayerByUUID(entry.getKey());
            if (!isValidPlayer(player)) {
                continue;
            }
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
        Player player = getCurrentTarget();
        boss.setTarget(player);
    }

    private void disengage(long now) {
        hatred.clear();
        currentTarget = null;
        pendingTarget = null;
        pendingTargetSince = 0L;
        lowHatredSince = -1L;
        noPlayerSince = -1L;
        lastAttackActionTick = 0L;

        boss.setTarget(null);
        boss.getNavigation().stop();
        boss.onNetcraftDisengage(false);

        if (lastPlayerHatredTick < 0L || now - lastPlayerHatredTick >= RETURN_HEAL_DELAY_TICKS) {
            teleportHomeAndHeal();
            returning = false;
        } else {
            returning = true;
            returningSince = now;
        }
    }

    private void tickReturnMovement() {
        if (currentTarget != null) {
            return;
        }

        Vec3 spawn = boss.getNetcraftSpawnPosition();
        if (spawn == null) {
            return;
        }

        double dx = boss.getX() - spawn.x;
        double dz = boss.getZ() - spawn.z;
        if (dx * dx + dz * dz <= 4.0D) {
            return;
        }

        boss.getNavigation().moveTo(spawn.x, spawn.y, spawn.z, 0.5D);
    }

    private void teleportHomeAndHeal() {
        Vec3 spawn = boss.getNetcraftSpawnPosition();
        if (spawn != null) {
            boss.moveTo(spawn.x, spawn.y, spawn.z, boss.getYRot(), boss.getXRot());
        }
        boss.setHealth(boss.getMaxHealth());
        boss.onNetcraftDisengage(true);
    }

    private static boolean isValidPlayer(Player player) {
        return player != null
                && player.isAlive()
                && !player.isRemoved()
                && !player.isCreative()
                && !player.isSpectator();
    }

    /**
     * NetCraft 1.4.18 PlayerBaseStats#getPlayerHatredMultiplier 的可独立运行版。
     * NetCraft 没安装时仍使用原版基础倍率 0.01；如果安装了 NetCraft，
     * 会额外识别其骑士甲、剑武器、挑衅和隐蔽附魔。
     */
    private static double getNetcraftHatredMultiplier(Player player) {
        double multiplier = 0.01D;

        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        }) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty() && isNetcraftKnightArmor(stack.getItem())) {
                multiplier += 0.005D;
            }
        }

        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty()
                && "com.jiufeng.netcraft.item.weapon.SwordWeapon"
                .equals(mainHand.getItem().getClass().getName())) {
            multiplier += 0.03D;
        }

        Enchantment provocation = ForgeRegistries.ENCHANTMENTS.getValue(PROVOCATION_ID);
        Enchantment stealth = ForgeRegistries.ENCHANTMENTS.getValue(STEALTH_ID);

        int provocationLevel = 0;
        int stealthLevel = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (provocation != null) {
                provocationLevel += EnchantmentHelper.getItemEnchantmentLevel(provocation, stack);
            }
            if (stealth != null) {
                stealthLevel += EnchantmentHelper.getItemEnchantmentLevel(stealth, stack);
            }
        }

        if (provocationLevel > 0) {
            multiplier += 0.04D * Math.pow(provocationLevel, 0.38D);
        }
        multiplier -= stealthLevel * 0.0003D;

        return Math.max(0.0D, multiplier);
    }

    private static boolean isNetcraftKnightArmor(Item item) {
        if (!"com.jiufeng.netcraft.item.armor.ClassArmorItem".equals(item.getClass().getName())) {
            return false;
        }

        try {
            Method method = item.getClass().getMethod("getClassType");
            Object result = method.invoke(item);
            return "knight".equals(result);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static final class HateEntry {
        private double baseHatred;
        private double proximityHatred;
        private long lastBaseHatredTick;

        private double total() {
            return baseHatred + proximityHatred;
        }
    }
}
