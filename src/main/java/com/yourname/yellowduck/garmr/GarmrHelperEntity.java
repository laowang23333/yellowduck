package com.yourname.yellowduck.garmr;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * 恐惧之地专用的原资源模型承载实体。
 *
 * <p>V5 把阿努比斯、小恶魔、骷髅射手、死亡召唤骷髅守卫、冰/火亡灵夫人
 * 从 vanilla 占位实体换到同一个轻量实体类型；真正战斗状态仍由 {@link GarmrBoss}
 * 服务器权威逻辑控制，客户端只根据 variant 选择对应 Native GLTF。</p>
 */
public final class GarmrHelperEntity extends Monster {
    private static final EntityDataAccessor<Integer> VARIANT =
            SynchedEntityData.defineId(GarmrHelperEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ARCHER_ATTACK_START =
            SynchedEntityData.defineId(GarmrHelperEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ARCHER_MOVING =
            SynchedEntityData.defineId(GarmrHelperEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int ANUBIS = 1;
    public static final int DEVIL = 2;
    public static final int P1_ARCHER = 3;
    public static final int DEATH_GUARD = 4;
    public static final int LADY_ICE = 5;
    public static final int LADY_FIRE = 6;
    public static final int LAVA_GUARD = 7;

    public GarmrHelperEntity(EntityType<? extends GarmrHelperEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1024.0D)
                .add(Attributes.ATTACK_DAMAGE, 100.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(VARIANT, 0);
        entityData.define(ARCHER_ATTACK_START, -100000);
        entityData.define(ARCHER_MOVING, false);
    }

    public int getVariant() {
        return entityData.get(VARIANT);
    }

    public void setVariant(int variant) {
        entityData.set(VARIANT, variant);
    }

    /** 服务端射手发射投射物时调用，客户端据此播放一次拉弓/射击段。 */
    public void triggerArcherAttack() {
        if (getVariant() == P1_ARCHER) entityData.set(ARCHER_ATTACK_START, tickCount);
    }

    public float archerAttackSeconds(float partialTick) {
        if (getVariant() != P1_ARCHER) return -1.0F;
        int start = entityData.get(ARCHER_ATTACK_START);
        return (tickCount + partialTick - start) / 20.0F;
    }

    public void setArcherMoving(boolean moving) {
        if (getVariant() == P1_ARCHER) entityData.set(ARCHER_MOVING, moving);
    }

    public boolean isArcherMoving() {
        return getVariant() == P1_ARCHER && entityData.get(ARCHER_MOVING);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("GarmrVariant", getVariant());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("GarmrVariant")) setVariant(tag.getInt("GarmrVariant"));
    }

    public boolean isLady() {
        int v = getVariant();
        return v == LADY_ICE || v == LADY_FIRE;
    }

    @Override
    protected void registerGoals() {
        // 熔岩守卫、亡灵战士允许近战 Goal；骷髅射手由 Boss 统一发射远程投射物。
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.player.Player.class, true));
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (getVariant() != DEATH_GUARD && getVariant() != LAVA_GUARD) return false;
        if (!(target instanceof ServerPlayer player)) return false;
        GarmrBoss boss = ownerBoss();
        return boss != null && boss.isParticipant(player) && super.canAttack(target);
    }

    public GarmrBoss ownerBoss() {
        if (!(level() instanceof ServerLevel server)) return null;
        if (!getPersistentData().hasUUID(GarmrBoss.TAG_OWNER)) return null;
        Entity owner = server.getEntity(getPersistentData().getUUID(GarmrBoss.TAG_OWNER));
        return owner instanceof GarmrBoss boss && boss.isAlive() ? boss : null;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
