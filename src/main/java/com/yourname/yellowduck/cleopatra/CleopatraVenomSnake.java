package com.yourname.yellowduck.cleopatra;

import com.yourname.yellowduck.boss.NetcraftBossBase;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 艳后二阶段三蛇。
 *
 * v1：仇恨改回 YellowDuck 已还原的 NetCraft 1.4.18 通用 HatredManager：
 * - 不再每 tick 给范围内玩家硬加 +10 原始仇恨；
 * - 不再“打任意一条蛇，就把原始伤害 1:1 同时加给三条蛇”；
 * - 每条蛇只根据自己实际受到的伤害计算仇恨；
 * - 伤害仇恨 = 实际扣血 × NetCraft 玩家仇恨倍率；
 * - 保留 NetCraft 的距离仇恨、每秒 25% 衰减、10 秒过期、切换阈值/观察时间；
 * - 三蛇仍保留站桩、元素圈、炸弹、献祭强化等原有战斗机制。
 *
 * 三蛇属于固定战斗阶段，因此额外使用 NetCraft 的 detection hook：
 * 在 snake_target_range 内给玩家 1 点基础侦测仇恨，让蛇出生后能正常进入战斗。
 */
public class CleopatraVenomSnake extends NetcraftBossBase {
    public static final int ANIM_IDLE = 0;
    public static final int ANIM_APPEAR = 1;
    public static final int ANIM_ATTACK = 2;
    public static final int ANIM_DEATH = 3;

    private static final EntityDataAccessor<Integer> DATA_ANIM =
            SynchedEntityData.defineId(CleopatraVenomSnake.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ANIM_SERIAL =
            SynchedEntityData.defineId(CleopatraVenomSnake.class, EntityDataSerializers.INT);

    private int normalAttackCooldown;
    private int poolCooldown;
    private int bombCooldown;
    private boolean appearPlaying;
    private int appearTimer;
    private boolean attackAnimPlaying;
    private int attackAnimDuration;
    private float damageMult = 1.0F;
    private final List<UUID> bombMarks = new ArrayList<>();
    private boolean initialized;
    private boolean stationaryAnchorSet;
    private double stationaryX;
    private double stationaryZ;

    public CleopatraVenomSnake(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        normalAttackCooldown = 0;
        poolCooldown = CleopatraConfig.snakeRingCd.get();
        bombCooldown = CleopatraConfig.snakeBombFirstCd.get();
        setBaseTier(4);
        setBaseDamage(30);
        setPersistenceRequired();

        String name = switch (getSnakeKind()) {
            case 1 -> "火蛇";
            case 2 -> "冰蛇";
            default -> "毒蛇";
        };
        setCustomName(Component.literal(name));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20000.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 30.0D)
                .add(Attributes.FOLLOW_RANGE, 50.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ANIM, ANIM_IDLE);
        entityData.define(DATA_ANIM_SERIAL, 0);
    }

    public int getAttackState() {
        return entityData.get(DATA_ANIM);
    }

    public int getAnimationSerial() {
        return entityData.get(DATA_ANIM_SERIAL);
    }

    private void setAnimState(int state) {
        if (getAttackState() != state) {
            entityData.set(DATA_ANIM, state);
            entityData.set(DATA_ANIM_SERIAL, entityData.get(DATA_ANIM_SERIAL) + 1);
        }
    }

    @Override public boolean isPushable() { return false; }

    @Override
    public void knockback(double strength, double x, double z) {
        // 三蛇是固定炮台，禁止任何伤害击退。
    }

    /** 使用通用 NetCraft HatredManager，不再走三蛇自定义 raw hatred。 */
    @Override public boolean useAutomaticHatredManagerTick() { return true; }

    /**
     * 必须取消锁定。
     * HatredManager 在 isHatredLocked()==true 时不会执行距离仇恨、衰减和目标切换。
     */
    @Override public boolean isHatredLocked() { return false; }

    /** 三蛇固定站桩，不因为离出生点判定回位。 */
    @Override public boolean shouldIgnoreSpawnDistanceLimit() { return true; }

    /** 三蛇是副本阶段怪，不使用普通 Boss 的脱战回满流程。 */
    @Override public boolean shouldDisengageOnDistance() { return false; }
    @Override public boolean shouldDisengageOnLowHatred() { return false; }
    @Override public boolean shouldDisengageOnAttackTimeout() { return false; }
    @Override public double getNoPlayerDisengageRadius() { return 100000.0D; }

    /**
     * NetCraft detection hook：让三蛇在自己的配置攻击范围内建立最低 1 点侦测仇恨。
     * 真正高仇恨仍主要来自实际伤害和 1/2/3 格的距离仇恨。
     */
    @Override public double getDetectionRadius() { return CleopatraConfig.snakeTargetRange.get(); }
    @Override public double getDetectionHatred() { return 1.0D; }

    /** 离开三蛇配置攻击范围后，从该蛇仇恨表中清掉。 */
    @Override public double getHatredClearRadius() { return CleopatraConfig.snakeTargetRange.get(); }

    @Override public boolean isPlayingAttackAnimation() {
        int state = getAttackState();
        return state != ANIM_IDLE && state != ANIM_APPEAR && state != ANIM_DEATH;
    }

    @Override public int getMeleeDefense() { return CleopatraConfig.snakeMeleeDefense.get().intValue(); }
    @Override public int getRangedDefense() { return CleopatraConfig.snakeRangedDefense.get().intValue(); }
    @Override public int getMagicDefense() { return CleopatraConfig.snakeMagicDefense.get().intValue(); }
    @Override public float getDamageReductionRatio() { return CleopatraConfig.snakeReduction.get().floatValue(); }

    // boss_map_icon.png：毒蛇=左上第一行第二个绿图标；火蛇=左上第二行第二个红图标；
    // 寒冰蛇=左下第二行第二个蓝图标。
    @Override
    public int getIconAtlasU() {
        return switch (getSnakeKind()) {
            case 2 -> 109;
            default -> 180;
        };
    }

    @Override
    public int getIconAtlasV() {
        return switch (getSnakeKind()) {
            case 1 -> 99;
            case 2 -> 827;
            default -> 2;
        };
    }

    @Override public int getIconWidth() { return 106; }
    @Override public int getIconHeight() { return 95; }

    /** 0=毒，1=火，2=冰。 */
    public int getSnakeKind() {
        if (getType() == CleopatraEntities.SNAKE_FIRE.get()) return 1;
        if (getType() == CleopatraEntities.SNAKE_ICE.get()) return 2;
        return 0;
    }

    public MobEffect getLayerEffect() {
        return switch (getSnakeKind()) {
            case 1 -> CleopatraEffects.VENOM_BURNING.get();
            case 2 -> CleopatraEffects.VENOM_FROZEN.get();
            default -> CleopatraEffects.VENOM_POISON.get();
        };
    }

    public MobEffect getBombEffect() {
        return switch (getSnakeKind()) {
            case 1 -> CleopatraEffects.VENOM_BOMB_BURNING.get();
            case 2 -> CleopatraEffects.VENOM_BOMB_FROZEN.get();
            default -> CleopatraEffects.VENOM_BOMB_POISON.get();
        };
    }

    /** 交叉净化关系：毒蛇->火圈，火蛇->冰圈，冰蛇->普通/毒圈。 */
    public EntityType<CleopatraVenomRing> getRingType() {
        return switch (getSnakeKind()) {
            case 1 -> CleopatraEntities.VENOM_RING_ICE.get();
            case 2 -> CleopatraEntities.VENOM_RING_PLAIN.get();
            default -> CleopatraEntities.VENOM_RING_FIRE.get();
        };
    }

    public float getDamageMult() {
        return damageMult;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        if (!stationaryAnchorSet) {
            stationaryAnchorSet = true;
            stationaryX = getX();
            stationaryZ = getZ();
        }

        // 强制站桩：允许 Y 方向受重力落地，但 X/Z 永远锁在出生点。
        getNavigation().stop();
        Vec3 motion = getDeltaMovement();
        setDeltaMovement(0.0D, motion.y, 0.0D);
        if (Math.abs(getX() - stationaryX) > 1.0E-6D || Math.abs(getZ() - stationaryZ) > 1.0E-6D) {
            setPos(stationaryX, getY(), stationaryZ);
        }

        if (!initialized && tickCount >= 1) {
            initialized = true;
            fixConfiguredAttributes();
            setAnimState(ANIM_APPEAR);
            appearPlaying = true;
            appearTimer = CleopatraConfig.snakeAppearTicks.get();
            poolCooldown = CleopatraConfig.snakeRingCd.get();

            // 出生第一 tick 立即生成一个元素圈。
            placePoolRing();
        }
        if (!isAlive()) return;

        if (appearPlaying) {
            appearTimer--;
            if (appearTimer <= 0) {
                appearPlaying = false;
                setAnimState(ANIM_IDLE);
            }
        }

        if (normalAttackCooldown > 0) normalAttackCooldown--;
        if (poolCooldown > 0) poolCooldown--;
        if (bombCooldown > 0) bombCooldown--;

        tickAttackAnimReset();
        tickSkills();
    }

    private void fixConfiguredAttributes() {
        var hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(CleopatraConfig.snakeHealth.get());
        var attack = getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) attack.setBaseValue(CleopatraConfig.snakeAttributeAttack.get());
        var follow = getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null) follow.setBaseValue(CleopatraConfig.snakeTargetRange.get());
        setHealth(getMaxHealth());
    }

    private void tickAttackAnimReset() {
        if (!attackAnimPlaying) return;
        attackAnimDuration--;
        if (attackAnimDuration <= 0) {
            attackAnimPlaying = false;
            setAnimState(ANIM_IDLE);
        }
    }

    private List<CleopatraVenomSnake> packSnakes() {
        double range = CleopatraConfig.snakePackRange.get();
        return level().getEntitiesOfClass(CleopatraVenomSnake.class, getBoundingBox().inflate(range),
                snake -> snake.isAlive() && !snake.isRemoved());
    }

    /**
     * 直接使用这条蛇自己的 NetCraft 当前目标。
     * 不再把三条蛇仇恨相加，也不再把一条蛇收到的伤害复制到其它两条蛇。
     */
    private Player pickNetcraftTarget() {
        LivingEntity target = getAttackTargetEntity();
        if (target instanceof Player player
                && CleopatraUtil.validPlayer(player)
                && distanceTo(player) <= CleopatraConfig.snakeTargetRange.get()) {
            return player;
        }
        return null;
    }

    private void faceTargetInstant(Player player) {
        Vec3 direction = player.position().subtract(position()).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(direction.z, direction.x)) + 90.0F;
        setYRot(yaw);
        setYHeadRot(yaw);
        yBodyRot = yaw;
        yBodyRotO = yaw;
    }

    private void tickSkills() {
        if (appearPlaying || attackAnimPlaying) return;

        Player target = pickNetcraftTarget();
        if (target == null) {
            setAnimState(ANIM_IDLE);
            return;
        }

        // 元素圈、炸弹和普通攻击可以在同一 tick 分别触发。
        if (poolCooldown <= 0) {
            poolCooldown = CleopatraConfig.snakeRingCd.get();
            placePoolRing();
        }

        if (bombCooldown <= 0 && isBombCoordinator()) {
            // 三蛇共享一轮炸弹仍保留；这只是技能协作，不再共享仇恨数值。
            List<CleopatraVenomSnake> pack = packSnakes();
            if (!hasActiveBombNearby()) {
                CleopatraVenomSnake caster = pack.get(random.nextInt(pack.size()));
                if (caster.placeBombOnFarthest()) {
                    int nextCooldown = CleopatraConfig.snakeBombCd.get();
                    for (CleopatraVenomSnake snake : pack) snake.bombCooldown = nextCooldown;
                } else {
                    // 当前没有可点名玩家时短暂重试，不直接浪费整轮冷却。
                    bombCooldown = 20;
                }
            } else {
                // 场上已有元素炸弹时不再追加第二名玩家。
                bombCooldown = 20;
            }
        }

        if (normalAttackCooldown <= 0 && !attackAnimPlaying) {
            performNormalAttack(target);
            return;
        }

        setAnimState(ANIM_IDLE);
    }

    private void performNormalAttack(Player player) {
        faceTargetInstant(player);
        setAnimState(ANIM_ATTACK);
        attackAnimPlaying = true;
        attackAnimDuration = CleopatraConfig.snakeAttackAnimTicks.get();
        normalAttackCooldown = CleopatraConfig.snakeNormalCd.get();
        notifyAttackAction();

        MobEffect layer = getLayerEffect();
        MobEffectInstance existing = player.getEffect(layer);
        int maxStacks = Math.max(1, CleopatraConfig.layerMaxStacks.get());
        boolean fullStacks = existing != null && existing.getAmplifier() >= maxStacks - 1;

        float damage = CleopatraConfig.snakeAttack.get().floatValue() * damageMult;
        if (fullStacks) damage *= CleopatraConfig.maxStackDamageMultiplier.get().floatValue();
        CleopatraUtil.magicHurt(player, this, damage);

        /*
         * 元素层数计算：
         * existing amplifier + 1 被当作“层数”，随后再次 -1 写回 amplifier。
         * 因此默认行为会刷新同层持续时间；这里保持现有层数计算规则。
         */
        int layers = existing != null ? existing.getAmplifier() + 1 : 1;
        layers = Math.min(layers, maxStacks);
        player.addEffect(new MobEffectInstance(layer, CleopatraConfig.layerDuration.get(),
                Math.max(0, layers - 1), false, false, true));
    }

    private void placePoolRing() {
        int attempts = Math.max(1, CleopatraConfig.snakeRingAttempts.get());
        double diameter = Math.max(0.0D, CleopatraConfig.snakeRingRandomDiameter.get());

        // 优先在竞技场随机位置寻找可站立地面。
        for (int i = 0; i < attempts; i++) {
            double x = getX() + (random.nextDouble() - 0.5D) * diameter;
            double z = getZ() + (random.nextDouble() - 0.5D) * diameter;
            double y = findGroundYWithin((int) Math.floor(x), (int) Math.floor(z));
            if (!Double.isNaN(y)) {
                spawnPoolRing(x, y, z);
                return;
            }
        }

        // 随机点全部失败时，从蛇周围逐圈寻找合法地面，避免某种元素圈整轮缺失。
        int maxRadius = Math.max(4, Math.min(16, (int) Math.ceil(diameter * 0.25D)));
        int baseX = (int) Math.floor(getX());
        int baseZ = (int) Math.floor(getZ());

        for (int radius = 0; radius <= maxRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    double y = findGroundYWithin(baseX + dx, baseZ + dz);
                    if (!Double.isNaN(y)) {
                        spawnPoolRing(baseX + dx + 0.5D, y, baseZ + dz + 0.5D);
                        return;
                    }
                }
            }
        }

        // 极端情况下也保证技能实体生成，不再直接放弃。
        spawnPoolRing(getX(), getY(), getZ());
    }

    private void spawnPoolRing(double x, double y, double z) {
        CleopatraVenomRing ring = getRingType().create(level());
        if (ring == null) return;
        ring.setPos(x, y, z);
        ring.setDamageMult(damageMult);
        level().addFreshEntity(ring);
    }

    private double findGroundYWithin(int x, int z) {
        int baseY = (int) Math.floor(getY());
        int yRange = Math.max(0, (int) Math.round(CleopatraConfig.snakeRingGroundYRange.get()));
        int minY = Math.max(level().getMinBuildHeight(), baseY - yRange);
        int maxY = Math.min(level().getMaxBuildHeight() - 2, baseY + yRange);

        // 从高往低寻找“有碰撞地面 + 上方可站立空间”。
        for (int y = maxY; y >= minY; y--) {
            BlockPos groundPos = new BlockPos(x, y, z);
            BlockPos abovePos = groundPos.above();
            BlockState ground = level().getBlockState(groundPos);
            BlockState above = level().getBlockState(abovePos);

            boolean solidGround = ground.getFluidState().isEmpty()
                    && !ground.getCollisionShape(level(), groundPos).isEmpty();
            boolean clearAbove = above.getFluidState().isEmpty()
                    && above.getCollisionShape(level(), abovePos).isEmpty();

            if (solidGround && clearAbove) {
                return y + 1.0D;
            }
        }
        return Double.NaN;
    }
    private boolean placeBombOnFarthest() {
        double radius = CleopatraConfig.bombCandidateRadius.get();
        AABB box = new AABB(getX() - radius, getY() - radius, getZ() - radius,
                getX() + radius, getY() + radius, getZ() + radius);
        List<Player> all = level().getEntitiesOfClass(Player.class, box, CleopatraUtil::validPlayer);
        if (all.isEmpty()) return false;

        // BossIce2 1.1.3：盾牌武器/龙爪副手玩家优先免点；
        // 只有场上所有可选玩家都属于这类职业时才允许点到他们。
        List<Player> normal = new ArrayList<>();
        List<Player> immune = new ArrayList<>();
        for (Player player : all) {
            (isBombImmune(player) ? immune : normal).add(player);
        }
        List<Player> base = normal.isEmpty() ? immune : normal;
        if (base.isEmpty()) return false;

        // 优先排除身上已经存在任意元素炸弹的玩家；如果全都有才退回原候选池。
        List<Player> candidates = new ArrayList<>();
        for (Player player : base) {
            if (!hasAnyBombEffect(player)) candidates.add(player);
        }
        if (candidates.isEmpty()) candidates = base;

        // 原版不是“最远3人随机”，而是权重随机：当前主要仇恨目标权重1，其余玩家权重10。
        Player hatredTarget = pickNetcraftTarget();
        int totalWeight = 0;
        for (Player player : candidates) totalWeight += player == hatredTarget ? 1 : 10;
        if (totalWeight <= 0) return false;
        int roll = random.nextInt(totalWeight);
        Player carrier = candidates.get(0);
        for (Player player : candidates) {
            int weight = player == hatredTarget ? 1 : 10;
            if (roll < weight) { carrier = player; break; }
            roll -= weight;
        }

        carrier.addEffect(new MobEffectInstance(getBombEffect(), CleopatraConfig.bombEffectDuration.get(),
                0, false, false, true));

        EntityType<CleopatraBombMark> markType = switch (getSnakeKind()) {
            case 1 -> CleopatraEntities.BOMB_MARK_BURNING.get();
            case 2 -> CleopatraEntities.BOMB_MARK_FROZEN.get();
            default -> CleopatraEntities.BOMB_MARK_POISON.get();
        };
        CleopatraBombMark mark = markType.create(level());
        if (mark == null) {
            carrier.removeEffect(getBombEffect());
            return false;
        }
        mark.setCarrier(carrier);
        mark.setDamageMult(damageMult);
        mark.setPos(carrier.getX(), carrier.getY() + 2.6D, carrier.getZ());
        level().addFreshEntity(mark);
        bombMarks.add(mark.getUUID());
        return true;
    }

    private static boolean isBombImmune(Player player) {
        if (player == null) return false;
        // 避免 YellowDuck 编译期强依赖 NetCraft 具体类；运行时按原类名兼容 1.4.x。
        String className = player.getOffhandItem().getItem().getClass().getName();
        return className.endsWith(".ShieldWeapon") || className.endsWith(".DragonClawWeapon");
    }

    private boolean isBombCoordinator() {
        List<CleopatraVenomSnake> pack = packSnakes();
        if (pack.isEmpty()) return false;
        CleopatraVenomSnake coordinator = pack.stream()
                .min(Comparator.comparingInt(Entity::getId))
                .orElse(this);
        return coordinator == this;
    }

    private boolean hasActiveBombNearby() {
        double radius = Math.max(CleopatraConfig.snakePackRange.get(), CleopatraConfig.bombCandidateRadius.get());
        AABB box = getBoundingBox().inflate(radius);
        return !level().getEntitiesOfClass(Player.class, box,
                player -> CleopatraUtil.validPlayer(player) && hasAnyBombEffect(player)).isEmpty();
    }

    private static boolean hasAnyBombEffect(Player player) {
        return player.hasEffect(CleopatraEffects.VENOM_BOMB_POISON.get())
                || player.hasEffect(CleopatraEffects.VENOM_BOMB_BURNING.get())
                || player.hasEffect(CleopatraEffects.VENOM_BOMB_FROZEN.get());
    }

    private void cleanupBombs() {
        if (!(level() instanceof ServerLevel serverLevel)) return;

        for (UUID id : new ArrayList<>(bombMarks)) {
            Entity entity = serverLevel.getEntity(id);
            if (!(entity instanceof CleopatraBombMark mark) || mark.isRemoved()) continue;

            MobEffect effect = mark.getBombEffect();
            Player carrier = serverLevel.getServer().getPlayerList().getPlayer(mark.getCarrierUuid());
            if (carrier != null && carrier.hasEffect(effect)) {
                carrier.removeEffect(effect);
            }
            mark.discard();
        }

        bombMarks.clear();
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            cleanupBombs();
            grantSacrificeBuff();
            setAnimState(ANIM_DEATH);
        }
        super.die(source);
    }

    private void grantSacrificeBuff() {
        double range = CleopatraConfig.snakeSacrificeRange.get();
        List<CleopatraVenomSnake> others = level().getEntitiesOfClass(
                CleopatraVenomSnake.class,
                getBoundingBox().inflate(range),
                snake -> snake != this && snake.isAlive()
        );

        others.sort(Comparator.comparingDouble(snake -> snake.distanceToSqr(this)));

        int count = Math.min(CleopatraConfig.snakeSacrificeTargets.get(), others.size());
        float factor = CleopatraConfig.snakeDamageMultPerDeath.get().floatValue();
        for (int i = 0; i < count; i++) {
            others.get(i).damageMult *= factor;
        }
    }

    public void addBombMark(UUID id) {
        if (id != null) bombMarks.add(id);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        if (stationaryAnchorSet) {
            tag.putDouble("StationaryX", stationaryX);
            tag.putDouble("StationaryZ", stationaryZ);
        }

        tag.putInt("NormalAttackCD", normalAttackCooldown);
        tag.putInt("PoolCD", poolCooldown);
        tag.putInt("BombCD", bombCooldown);
        tag.putFloat("DamageMult", damageMult);
        tag.putInt("AppearTimer", appearTimer);
        tag.putBoolean("AppearPlaying", appearPlaying);

        ListTag list = new ListTag();
        for (UUID id : bombMarks) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            list.add(entry);
        }
        tag.put("BombMarks", list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (tag.contains("StationaryX") && tag.contains("StationaryZ")) {
            stationaryX = tag.getDouble("StationaryX");
            stationaryZ = tag.getDouble("StationaryZ");
            stationaryAnchorSet = true;
        }

        normalAttackCooldown = tag.getInt("NormalAttackCD");
        poolCooldown = tag.getInt("PoolCD");
        bombCooldown = tag.getInt("BombCD");
        damageMult = tag.contains("DamageMult") ? tag.getFloat("DamageMult") : 1.0F;
        appearTimer = tag.getInt("AppearTimer");
        appearPlaying = tag.getBoolean("AppearPlaying");

        bombMarks.clear();
        ListTag list = tag.getList("BombMarks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID("Id")) {
                bombMarks.add(entry.getUUID("Id"));
            }
        }

        if (!level().isClientSide) {
            setAnimState(appearPlaying ? ANIM_APPEAR : ANIM_IDLE);
        }
    }
}
