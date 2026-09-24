package com.yourname.yellowduck.tengu;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Boss 奖励生成的未驯服天狗。
 * 当前版本只负责展示、计时和保留后续驯服入口，不提供坐骑蛋，也不能直接绑定。
 */
public final class TenguWildMountEntity extends PathfinderMob {
    private static final String NBT_SPAWN_TIME = "TenguUntamedSpawnGameTime";
    private long spawnGameTime = -1L;

    public TenguWildMountEntity(EntityType<? extends TenguWildMountEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.24D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public Component getName() {
        return Component.literal("天狗");
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        long now = level().getGameTime();
        if (spawnGameTime < 0L) {
            spawnGameTime = now;
        }

        if (now - spawnGameTime >= TenguConfig.UNTAMED_LIFETIME_TICKS) {
            discard();
        }
    }

    public int remainingUntamedTicks() {
        if (spawnGameTime < 0L) return TenguConfig.UNTAMED_LIFETIME_TICKS;
        long left = TenguConfig.UNTAMED_LIFETIME_TICKS - (level().getGameTime() - spawnGameTime);
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, left));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (spawnGameTime >= 0L) tag.putLong(NBT_SPAWN_TIME, spawnGameTime);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        spawnGameTime = tag.contains(NBT_SPAWN_TIME) ? tag.getLong(NBT_SPAWN_TIME) : -1L;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        // 没有蛋，只能由 Boss 击杀奖励获得。
        return ItemStack.EMPTY;
    }
}
