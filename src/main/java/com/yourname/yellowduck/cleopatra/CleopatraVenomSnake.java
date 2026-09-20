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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 艳后二阶段三蛇。
 *
 * 主要机制：完全站桩、三蛇共享仇恨、持续给范围内玩家增加原始仇恨；
 * 元素圈/炸弹是独立计时器，和普通攻击可以在同一 tick 同时触发。
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
    @Override public void knockback(double strength, double x, double z) {
        // 三蛇是固定炮台，禁止任何伤害击退。
    }
    @Override public boolean useAutomaticHatredManagerTick() { return false; }
    @Override public boolean isHatredLocked() { return true; }
    @Override public boolean shouldIgnoreSpawnDistanceLimit() { return true; }
    @Override public boolean shouldDisengageOnDistance() { return false; }
    @Override public boolean shouldDisengageOnLowHatred() { return false; }
    @Override public boolean shouldDisengageOnAttackTimeout() { return false; }
    @Override public double getNoPlayerDisengageRadius() { return 100000.0D; }
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
        updateTargetingAndHatred();
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

    private List<Player> playersInTargetRange() {
        List<CleopatraVenomSnake> pack = packSnakes();
        List<Player> result = new ArrayList<>();
        double packRange = CleopatraConfig.snakePackRange.get();
        double targetRange = CleopatraConfig.snakeTargetRange.get();

        for (Player player : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(packRange))) {
            if (!CleopatraUtil.validPlayer(player)) continue;
            for (CleopatraVenomSnake snake : pack) {
                if (snake.distanceTo(player) <= targetRange) {
                    result.add(player);
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (!level().isClientSide && hit && amount > 0.0F) {
            Player attacker = null;
            Entity sourceEntity = source.getEntity();
            if (sourceEntity instanceof Player player) {
                attacker = player;
            } else if (sourceEntity instanceof Projectile projectile && projectile.getOwner() instanceof Player player) {
                attacker = player;
            } else if (source.getDirectEntity() instanceof Projectile projectile && projectile.getOwner() instanceof Player player) {
                attacker = player;
            }
            if (CleopatraUtil.validPlayer(attacker)) {
                for (CleopatraVenomSnake snake : packSnakes()) {
                    snake.getHatredManager().addRawHatred(attacker, amount);
                }
            }
        }
        return hit;
    }

    private void updateTargetingAndHatred() {
        List<Player> players = playersInTargetRange();
        if (players.isEmpty()) {
            setTarget(null);
            getHatredManager().resetRawHatred();
            if (getHealth() < getMaxHealth()) setHealth(getMaxHealth());
            return;
        }

        Set<UUID> valid = new HashSet<>();
        for (Player player : players) {
            valid.add(player.getUUID());
            // 每个服务器 tick 对范围内玩家增加原始仇恨。
            getHatredManager().addRawHatred(player, 10.0D);
        }
        getHatredManager().retainHatred(valid);
        setTarget(pickHatredTarget());
    }

    private Player pickHatredTarget() {
        List<CleopatraVenomSnake> pack = packSnakes();
        Player best = null;
        double highest = -1.0D;
        for (Player player : playersInTargetRange()) {
            double total = 0.0D;
            UUID id = player.getUUID();
            for (CleopatraVenomSnake snake : pack) {
                total += snake.getHatredManager().getHatred(id);
            }
            if (total > highest) {
                highest = total;
                best = player;
            }
        }
        return best;
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
        Player target = pickHatredTarget();
        if (target == null) {
            setAnimState(ANIM_IDLE);
            return;
        }

        // 元素圈、炸弹和普通攻击可以在同一 tick 分别触发。
        if (poolCooldown <= 0) {
            poolCooldown = CleopatraConfig.snakeRingCd.get();
            placePoolRing();
        }
        if (bombCooldown <= 0) {
            bombCooldown = CleopatraConfig.snakeBombCd.get();
            placeBombOnFarthest();
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
        int attempts = CleopatraConfig.snakeRingAttempts.get();
        double diameter = CleopatraConfig.snakeRingRandomDiameter.get();
        for (int i = 0; i < attempts; i++) {
            double x = getX() + (random.nextDouble() - 0.5D) * diameter;
            double z = getZ() + (random.nextDouble() - 0.5D) * diameter;
            double y = findGroundYWithin((int) Math.floor(x), (int) Math.floor(z));
            if (y >= 0.0D) {
                spawnPoolRing(x, y, z);
                return;
            }
        }

        double y = findGroundYWithin((int) Math.floor(getX()), (int) Math.floor(getZ()));
        if (y >= 0.0D) spawnPoolRing(getX(), y, getZ());
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
        int solidCount = 0;
        int minY = baseY - yRange;
        int maxY = baseY + yRange;

        for (int y = minY; y <= maxY; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level().getBlockState(pos);
            if (!state.getFluidState().isEmpty()) return -1.0D;

            if (!state.isAir()) {
                solidCount++;
                continue;
            }

            if (solidCount >= 2) {
                double ringY = y;
                boolean headroom = y + 1 > maxY || level().getBlockState(new BlockPos(x, y + 1, z)).isAir();
                if (headroom && Math.abs(ringY - getY()) <= 2.0D) return ringY;
                return -1.0D;
            }
            solidCount = 0;
        }
        return -1.0D;
    }

    private void placeBombOnFarthest() {
        double radius = CleopatraConfig.bombCandidateRadius.get();
        AABB box = new AABB(getX() - radius, getY() - radius, getZ() - radius,
                getX() + radius, getY() + radius, getZ() + radius);
        List<Player> all = level().getEntitiesOfClass(Player.class, box, CleopatraUtil::validPlayer);
        if (all.isEmpty()) return;

        List<Player> noBomb = new ArrayList<>();
        for (Player player : all) if (!hasAnyBombEffect(player)) noBomb.add(player);
        List<Player> candidates = noBomb.isEmpty() ? new ArrayList<>(all) : noBomb;
        candidates.sort(Comparator.comparingDouble(player -> -distanceTo(player)));

        int topCount = Math.min(Math.max(1, CleopatraConfig.bombFarthestPoolSize.get()), candidates.size());
        int selectedIndex = random.nextInt(topCount);
        Player hatredTarget = pickHatredTarget();
        if (hatredTarget != null && topCount > 1 && candidates.get(selectedIndex) == hatredTarget
                && random.nextInt(100) < CleopatraConfig.tankAvoidPercent.get()) {
            int alternate = random.nextInt(topCount - 1);
            selectedIndex = alternate < selectedIndex ? alternate : alternate + 1;
        }

        Player carrier = candidates.get(selectedIndex);
        carrier.addEffect(new MobEffectInstance(getBombEffect(), CleopatraConfig.bombEffectDuration.get(),
                0, false, false, true));

        EntityType<CleopatraBombMark> markType = switch (getSnakeKind()) {
            case 1 -> CleopatraEntities.BOMB_MARK_BURNING.get();
            case 2 -> CleopatraEntities.BOMB_MARK_FROZEN.get();
            default -> CleopatraEntities.BOMB_MARK_POISON.get();
        };
        CleopatraBombMark mark = markType.create(level());
        if (mark == null) return;
        mark.setCarrier(carrier);
        mark.setDamageMult(damageMult);
        mark.setPos(carrier.getX(), carrier.getY() + 2.6D, carrier.getZ());
        level().addFreshEntity(mark);
        bombMarks.add(mark.getUUID());
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
            if (carrier != null && carrier.hasEffect(effect)) carrier.removeEffect(effect);
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
        List<CleopatraVenomSnake> others = level().getEntitiesOfClass(CleopatraVenomSnake.class,
                getBoundingBox().inflate(range), snake -> snake != this && snake.isAlive());
        others.sort(Comparator.comparingDouble(snake -> snake.distanceToSqr(this)));
        int count = Math.min(CleopatraConfig.snakeSacrificeTargets.get(), others.size());
        float factor = CleopatraConfig.snakeDamageMultPerDeath.get().floatValue();
        for (int i = 0; i < count; i++) others.get(i).damageMult *= factor;
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
            if (entry.hasUUID("Id")) bombMarks.add(entry.getUUID("Id"));
        }
        if (!level().isClientSide) setAnimState(appearPlaying ? ANIM_APPEAR : ANIM_IDLE);
    }
}
