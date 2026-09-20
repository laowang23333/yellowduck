package com.yourname.yellowduck.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * YellowDuck 通用 NetCraft 风格 Boss 基类。
 *
 * 将 Boss 战斗常用机制集中到一个可复用基类，后续 Boss 只需要继承此类并覆写数值/技能。
 *
 * 通用机制：
 * - 出生点记录、NBT 持久化、回出生点；
 * - NetCraft 风格仇恨管理；
 * - 脱战判定与回满血；
 * - Tier / 基础伤害 / 三类防御 / 固定减伤；
 * - Tier 对普通近战的额外减伤；
 * - 狂暴开关、狂暴友军和 32 格狂暴目标列表；
 * - 攻击动作超时通知；
 * - “造成伤害但不击退目标”的统一攻击接口；
 * - Boss HUD 图集坐标接口。
 *
 * 其余系统通过扩展钩子接入，本类只负责 Boss 战斗相关能力。
 */
public abstract class NetcraftBossBase extends Monster {

    public static final double RAMPAGE_TARGET_RANGE = 32.0D;
    public static final double RAMPAGE_TARGET_RANGE_SQ = RAMPAGE_TARGET_RANGE * RAMPAGE_TARGET_RANGE;

    private static final ResourceLocation PROVOCATION_ID = new ResourceLocation("netcraft", "provocation");
    private static final ResourceLocation STEALTH_ID = new ResourceLocation("netcraft", "stealth");

    private final NetcraftHatredManager hatredManager;
    private final Set<UUID> rampageAllies = new HashSet<>();

    private Vec3 spawnPosition;
    private boolean spawnPositionSet;
    private boolean rampageActive;

    // NetCraft BossBase 的默认值。
    private int modTier = 1;
    private int baseDamage = 1;
    private int baseDefense = 5;

    protected NetcraftBossBase(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.hatredManager = new NetcraftHatredManager(this);
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide) {
            if (!spawnPositionSet) {
                setSpawnPosition(position());
            }
            if (isAlive()) {
                if (useAutomaticHatredManagerTick()) {
                    hatredManager.tick();
                }
                tickRampageTargeting();
                if (rampageActive && tickCount % 200 == 0) {
                    cleanupRampageAllies();
                }
            }
        }

        // NetCraft BossBase：攻击动画期间锁住身体朝向，避免动画播放时模型被寻路扭转。
        if (!level().isClientSide && isAlive() && isPlayingAttackAnimation()) {
            yBodyRotO = yBodyRot;
            yBodyRot = getYRot();
        }
    }

    /** 子类在攻击/施法动画期间返回 true，可沿用 NetCraft 的身体朝向锁定。 */
    public boolean isPlayingAttackAnimation() {
        return false;
    }

    /** 特殊 Boss 可关闭通用仇恨 tick，使用自己的仇恨算法。 */
    public boolean useAutomaticHatredManagerTick() {
        return true;
    }

    /** NetCraft BossBase 同款水平朝向角检测。 */
    public boolean isFacingPosition(Vec3 position, float toleranceDegrees) {
        if (position == null) return false;
        float wantedYaw = (float) (Mth.atan2(position.z - getZ(), position.x - getX()) * (180.0D / Math.PI)) - 90.0F;
        float delta = Mth.wrapDegrees(wantedYaw - yBodyRot);
        return Math.abs(delta) <= toleranceDegrees;
    }

    public boolean isFacingTarget(Player player, float toleranceDegrees) {
        return player != null && player.isAlive() && isFacingPosition(player.position(), toleranceDegrees);
    }

    /** NetCraft BossBase：开始攻击时立即正对目标，同时刷新攻击超时计时。 */
    public void faceTargetForAttack(LivingEntity target) {
        if (target == null || !target.isAlive()) return;
        notifyAttackAction();
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0E-6D) return;
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yBodyRotO = yaw;
        getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    /* ---------------- NetCraft Tier / damage / defense ---------------- */

    public final void setBaseTier(int tier) {
        this.modTier = tier * 6;
    }

    public final int getModTier() {
        return modTier;
    }

    public final void setModTier(int modTier) {
        this.modTier = modTier;
    }

    public final int getBaseDamage() {
        return baseDamage;
    }

    public final void setBaseDamage(int baseDamage) {
        this.baseDamage = baseDamage;
    }

    public final int getBaseDefense() {
        return baseDefense;
    }

    public final void setBaseDefense(int baseDefense) {
        this.baseDefense = baseDefense;
    }

    /** NetCraft: baseDamage + (modTier / 6) * 2. */
    public int getActualDamage() {
        return baseDamage + (modTier / 6) * 2;
    }

    public float getNetcraftAttackDamage() {
        if (getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)) {
            return (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        }
        return baseDamage;
    }

    /** 供后续职业、难度、服务器规则修改 Boss 对某个目标的伤害。 */
    public float getAttackDamageFor(LivingEntity target, float originalDamage) {
        return originalDamage;
    }

    public boolean isBoss() {
        return true;
    }

    protected boolean isWorldBoss() {
        return false;
    }

    /** 子类按 NetCraft 数值覆写。 */
    public int getMeleeDefense() {
        return 0;
    }

    /** 子类按 NetCraft 数值覆写。 */
    public int getRangedDefense() {
        return 0;
    }

    /** 子类按 NetCraft 数值覆写。 */
    public int getMagicDefense() {
        return 0;
    }

    /** 0.25 = 最终再减 25%。 */
    public float getDamageReductionRatio() {
        return 0.0F;
    }

    /**
     * NetCraft BossBase：Tier2 普通近战额外减 5%，Tier3/4 额外减 10%。
     * 其它 Tier 默认 0。
     */
    public float getBossMeleeReductionRatio() {
        int tier = getModTier() / 6;
        return switch (tier) {
            case 2 -> 0.05F;
            case 3, 4 -> 0.10F;
            default -> 0.0F;
        };
    }

    /**
     * 对进入 Boss 的伤害执行 NetCraft 风格的三类防御 + 固定减伤。
     * NetCraft 的炼金腐蚀/虚无/破防属于另一个系统，因此留出 hook 给以后接。
     */
    protected float applyNetcraftIncomingDamage(DamageSource source, float amount) {
        float damage = amount;

        if (isNetcraftTrueDamage(source)) {
            float ratio = getDamageReductionRatio();
            if (ratio > 0.0F) {
                damage *= 1.0F - ratio;
            }
            return Math.max(0.1F, damage);
        }

        DamageClass damageClass = classifyDamage(source);
        float defense;
        if (damageClass == DamageClass.MAGIC) {
            defense = getMagicDefense();
        } else if (damageClass == DamageClass.RANGED) {
            defense = getRangedDefense();
        } else {
            damage *= 1.0F - getBossMeleeReductionRatio();
            defense = getMeleeDefense();
        }

        defense = Math.max(0.0F, modifyNetcraftDefense(source, damageClass, defense));
        damage = Math.max(0.0F, damage - defense);

        float ratio = getDamageReductionRatio();
        if (ratio > 0.0F) {
            damage *= 1.0F - ratio;
        }
        return Math.max(0.1F, damage);
    }

    /** 给后续破防/腐蚀等机制留的接口。 */
    protected float modifyNetcraftDefense(DamageSource source, DamageClass damageClass, float defense) {
        return defense;
    }

    /** YellowDuck 暂时没有 NetCraft TRUE_DAMAGE，后续自定义伤害可覆写。 */
    protected boolean isNetcraftTrueDamage(DamageSource source) {
        return false;
    }

    protected DamageClass classifyDamage(DamageSource source) {
        Entity direct = source.getDirectEntity();
        if (direct instanceof net.minecraft.world.entity.projectile.Projectile) {
            return DamageClass.RANGED;
        }

        String id = source.getMsgId().toLowerCase(java.util.Locale.ROOT);
        if (id.contains("magic") || id.contains("wither") || id.contains("dragonbreath")
                || id.contains("dragon_breath") || id.contains("sonic")) {
            return DamageClass.MAGIC;
        }
        return DamageClass.MELEE;
    }

    public enum DamageClass {
        MELEE,
        RANGED,
        MAGIC
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) {
            return super.hurt(source, amount);
        }

        float before = getHealth();
        float adjusted = applyNetcraftIncomingDamage(source, amount);
        boolean hit = super.hurt(source, adjusted);

        if (hit) {
            noActionTime = 0;
            float dealt = Math.max(0.0F, before - getHealth());
            Player attacker = resolvePlayerAttacker(source);
            if (attacker != null && dealt > 0.0F) {
                hatredManager.addDamageHatred(attacker, dealt);
            }
        }
        return hit;
    }

    protected Player resolvePlayerAttacker(DamageSource source) {
        if (source.getEntity() instanceof Player player) {
            return player;
        }
        if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile
                && projectile.getOwner() instanceof Player player) {
            return player;
        }
        return null;
    }

    /* ---------------- Hatred ---------------- */

    public final NetcraftHatredManager getHatredManager() {
        return hatredManager;
    }

    public LivingEntity getAttackTargetEntity() {
        if (rampageActive) {
            LivingEntity target = getTarget();
            if (target != null && isValidRampageTarget(target)) {
                return target;
            }
        }
        return hatredManager.getCurrentTarget();
    }

    public boolean hasAttackTargetEntity() {
        LivingEntity target = getAttackTargetEntity();
        return target != null && target.isAlive() && !target.isRemoved();
    }

    public boolean isValidHatredPlayer(Player player) {
        return player != null
                && player.isAlive()
                && !player.isRemoved()
                && !player.isCreative()
                && !player.isSpectator()
                && player.level() == level();
    }

    /** NetCraft 普通 Boss 的默认玩家仇恨倍率。 */
    public double getHatredMultiplier(Player player) {
        double multiplier = 0.01D;

        // 如果服务器恰好装着 NetCraft，则兼容其原版骑士甲/剑/挑衅/隐蔽倍率；
        // 没安装时这些注册项为空，不产生依赖。
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        }) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty() && isNetcraftKnightArmor(stack.getItem())) {
                multiplier += 0.005D;
            }
        }

        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty()
                && "com.jiufeng.netcraft.item.weapon.SwordWeapon".equals(mainHand.getItem().getClass().getName())) {
            multiplier += 0.03D;
        }

        Enchantment provocation = ForgeRegistries.ENCHANTMENTS.getValue(PROVOCATION_ID);
        Enchantment stealth = ForgeRegistries.ENCHANTMENTS.getValue(STEALTH_ID);
        int provocationLevel = 0;
        int stealthLevel = 0;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
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
            return "knight".equals(method.invoke(item));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public boolean isHatredLocked() {
        return rampageActive;
    }

    public boolean shouldIgnoreSpawnDistanceLimit() {
        return false;
    }

    public double getSpawnDistanceLimit() {
        return 12.0D;
    }

    public boolean isEliteHatredMode() {
        return false;
    }

    /** 普通 Boss 默认 0：不会因为“看见玩家”自动建立基础仇恨。 */
    public double getDetectionRadius() {
        return 0.0D;
    }

    public double getDetectionHatred() {
        return 1.0D;
    }

    public double getHatredClearRadius() {
        return Double.MAX_VALUE;
    }

    public double getHatredSwitchThreshold() {
        return 6.0D;
    }

    public boolean isHatredSwitchInstant() {
        return false;
    }

    /** NetCraft 接口默认 0；阈值内切换时 HatredManager 自动使用 60 tick。 */
    public int getHatredSwitchObservationTicks() {
        return 0;
    }

    public boolean shouldInstantHealOnDisengage() {
        return false;
    }

    public boolean shouldDisengageOnDistance() {
        return true;
    }

    public boolean shouldDisengageOnLowHatred() {
        return true;
    }

    public boolean shouldDisengageOnAttackTimeout() {
        return true;
    }

    public double getNoPlayerDisengageRadius() {
        return 32.0D;
    }

    public int getNoPlayerDisengageDelay() {
        return 200;
    }

    /** NetCraft 仇恨扫描只关心出生点 100 格内玩家。 */
    public double getHatredSpawnPlayerLimit() {
        return 100.0D;
    }

    public double getReturnToSpawnSpeed() {
        return 0.5D;
    }

    /** 回位移动时进入 2 格内就不再寻路，等待最终重置。 */
    public double getReturnToSpawnStopDistance() {
        return 2.0D;
    }

    /** HatredManager 刚清仇恨时调用一次。 */
    protected void onNetcraftDisengageStarted() {
    }

    /** 瞬移出生点并回满血后调用一次。 */
    protected void onNetcraftFightReset() {
    }

    public void notifyAttackAction() {
        hatredManager.notifyAttackAction();
    }

    /* ---------------- Spawn position / reset ---------------- */

    public Vec3 getSpawnPosition() {
        return spawnPosition;
    }

    protected final void setSpawnPosition(Vec3 position) {
        this.spawnPosition = position;
        this.spawnPositionSet = position != null;
    }

    public void teleportToSpawn() {
        if (spawnPosition != null) {
            moveTo(spawnPosition.x, spawnPosition.y, spawnPosition.z, getYRot(), getXRot());
        }
    }

    public boolean shouldForceLoadChunk() {
        return false;
    }

    /**
     * 所有 NetCraft 风格技能伤害都建议走这个接口：
     * hurt() 前保存目标速度，hurt() 后恢复，因此伤害本身不产生击退。
     */
    protected final boolean hurtWithoutKnockback(LivingEntity target, DamageSource source, float damage) {
        if (target == null || !target.isAlive() || damage <= 0.0F) {
            return false;
        }
        Vec3 oldMotion = target.getDeltaMovement();
        boolean hit = target.hurt(source, getAttackDamageFor(target, damage));
        if (hit) {
            target.setDeltaMovement(oldMotion);
            target.hurtMarked = true;
        }
        return hit;
    }

    /* ---------------- Rampage ---------------- */

    public boolean isRampageActive() {
        return rampageActive;
    }

    public boolean toggleRampage() {
        rampageActive = !rampageActive;
        if (rampageActive) {
            notifyAttackAction();
        } else {
            setTarget(null);
        }
        return rampageActive;
    }

    public void registerRampageAlly(Mob ally) {
        if (ally != null && ally.isAlive()) {
            rampageAllies.add(ally.getUUID());
        }
    }

    public boolean isValidRampageTarget(LivingEntity target) {
        if (target == null || target == this || !target.isAlive() || target.isRemoved()) return false;
        if (rampageAllies.contains(target.getUUID())) return false;
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        return true;
    }

    public boolean hasRampageTarget() {
        return rampageActive && getTarget() != null && isValidRampageTarget(getTarget());
    }

    public List<LivingEntity> getHatredStyleTargets() {
        List<LivingEntity> result = new ArrayList<>();
        for (UUID id : hatredManager.getTrackedPlayerIds()) {
            Player player = level().getPlayerByUUID(id);
            if (isValidHatredPlayer(player)) {
                result.add(player);
            }
        }
        if (rampageActive) {
            AABB box = getBoundingBox().inflate(RAMPAGE_TARGET_RANGE);
            for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, box, this::isValidRampageTarget)) {
                if (!result.contains(living)) result.add(living);
            }
        }
        return result;
    }

    private void tickRampageTargeting() {
        if (!rampageActive || tickCount % 10 != 0) return;
        LivingEntity current = getTarget();
        if (current != null && isValidRampageTarget(current) && distanceToSqr(current) <= RAMPAGE_TARGET_RANGE_SQ) {
            return;
        }
        LivingEntity nearest = level().getEntitiesOfClass(
                        LivingEntity.class,
                        getBoundingBox().inflate(RAMPAGE_TARGET_RANGE),
                        this::isValidRampageTarget
                ).stream()
                .min(java.util.Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
        setTarget(nearest);
        syncRampageAllyTargets(nearest);
    }

    private void syncRampageAllyTargets(LivingEntity target) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        for (UUID id : rampageAllies) {
            Entity entity = serverLevel.getEntity(id);
            if (entity instanceof Mob ally && ally.isAlive()) {
                ally.setTarget(target != null && isValidRampageTarget(target) ? target : null);
            }
        }
    }

    private void cleanupRampageAllies() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        rampageAllies.removeIf(id -> {
            Entity entity = serverLevel.getEntity(id);
            return !(entity instanceof LivingEntity living) || !living.isAlive();
        });
    }

    /* ---------------- HUD atlas hooks ---------------- */

    /** 可选：返回独立 Boss 头像纹理；null 时仍使用 NetCraft 图集裁剪。 */
    public ResourceLocation getBossHudStandaloneIcon() {
        return null;
    }

    public int getIconAtlasU() {
        return 110;
    }

    public int getIconAtlasV() {
        return 536;
    }

    public int getIconWidth() {
        return 106;
    }

    public int getIconHeight() {
        return 95;
    }

    /* ---------------- NBT ---------------- */

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (spawnPosition != null) {
            tag.putDouble("NetcraftBossSpawnX", spawnPosition.x);
            tag.putDouble("NetcraftBossSpawnY", spawnPosition.y);
            tag.putDouble("NetcraftBossSpawnZ", spawnPosition.z);
            tag.putBoolean("NetcraftBossSpawnSet", true);
        }
        tag.putBoolean("NetcraftBossRampage", rampageActive);

        ListTag allies = new ListTag();
        for (UUID id : rampageAllies) {
            allies.add(StringTag.valueOf(id.toString()));
        }
        tag.put("NetcraftBossRampageAllies", allies);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.getBoolean("NetcraftBossSpawnSet")) {
            setSpawnPosition(new Vec3(
                    tag.getDouble("NetcraftBossSpawnX"),
                    tag.getDouble("NetcraftBossSpawnY"),
                    tag.getDouble("NetcraftBossSpawnZ")
            ));
        }
        rampageActive = tag.getBoolean("NetcraftBossRampage");
        rampageAllies.clear();
        ListTag allies = tag.getList("NetcraftBossRampageAllies", Tag.TAG_STRING);
        for (int i = 0; i < allies.size(); i++) {
            try {
                rampageAllies.add(UUID.fromString(allies.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // 忽略损坏的旧存档项。
            }
        }
    }
}
