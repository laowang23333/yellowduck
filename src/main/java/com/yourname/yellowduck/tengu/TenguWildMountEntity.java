package com.yourname.yellowduck.tengu;

import com.yourname.yellowduck.network.MountNetwork;
import com.yourname.yellowduck.registry.ModItems;
import com.yourname.yellowduck.util.MountData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
 *
 * 红月晶石：每次随机 +5 / +10 信任。
 * 信任 100 后，用高鞍捕捉并绑定到现有 M 键坐骑系统。
 * 未驯服状态仍会在出生 3 分钟后自动消失。
 */
public final class TenguWildMountEntity extends PathfinderMob {
    private static final String NBT_SPAWN_TIME = "TenguUntamedSpawnGameTime";
    private static final String NBT_TRUST = "TenguTrust";

    public static final EntityDataAccessor<Integer> TRUST =
            SynchedEntityData.defineId(TenguWildMountEntity.class, EntityDataSerializers.INT);

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
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(TRUST, 0);
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

    public int getTrust() {
        return entityData.get(TRUST);
    }

    public void setTrust(int trust) {
        entityData.set(TRUST, Mth.clamp(trust, 0, TenguConfig.MAX_TRUST));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (stack.is(ModItems.RED_MOON_CRYSTAL.get())) {
            if (level().isClientSide) {
                return InteractionResult.SUCCESS;
            }

            int oldTrust = getTrust();
            if (oldTrust >= TenguConfig.MAX_TRUST) {
                player.displayClientMessage(
                        Component.literal("§e天狗的信任值已经满了，请使用“高鞍”捕捉。"),
                        true
                );
                return InteractionResult.CONSUME;
            }

            int rolled = random.nextBoolean()
                    ? TenguConfig.TRUST_GAIN_SMALL
                    : TenguConfig.TRUST_GAIN_LARGE;
            int newTrust = Math.min(TenguConfig.MAX_TRUST, oldTrust + rolled);
            int actualGain = newTrust - oldTrust;
            setTrust(newTrust);

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }

            player.displayClientMessage(
                    Component.literal("§d天狗信任值 §a+" + actualGain
                            + " §7(" + newTrust + "/" + TenguConfig.MAX_TRUST + ")"),
                    true
            );

            if (newTrust >= TenguConfig.MAX_TRUST) {
                player.displayClientMessage(
                        Component.literal("§6天狗已经完全信任你了！现在可以使用“高鞍”捕捉。"),
                        false
                );
            }
            return InteractionResult.CONSUME;
        }

        if (stack.is(ModItems.HIGH_SADDLE.get())) {
            if (level().isClientSide) {
                return InteractionResult.SUCCESS;
            }

            if (getTrust() < TenguConfig.MAX_TRUST) {
                player.displayClientMessage(
                        Component.literal("§c天狗还不够信任你。当前信任值：§e"
                                + getTrust() + "/" + TenguConfig.MAX_TRUST),
                        true
                );
                return InteractionResult.CONSUME;
            }

            if (MountData.hasMount(player, "tengu")) {
                player.displayClientMessage(
                        Component.literal("§e你已经拥有“天狗”坐骑了。"),
                        true
                );
                return InteractionResult.CONSUME;
            }

            MountData.bindMount(player, "tengu");

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                MountNetwork.syncTo(serverPlayer);
            }

            player.displayClientMessage(
                    Component.literal("§a✦ 成功使用高鞍捕捉“天狗”！"),
                    true
            );
            player.displayClientMessage(
                    Component.literal("§7按 M 打开坐骑界面即可召唤天狗。"),
                    false
            );

            discard();
            return InteractionResult.CONSUME;
        }

        return super.mobInteract(player, hand);
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
        tag.putInt(NBT_TRUST, getTrust());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        spawnGameTime = tag.contains(NBT_SPAWN_TIME) ? tag.getLong(NBT_SPAWN_TIME) : -1L;
        setTrust(tag.contains(NBT_TRUST) ? tag.getInt(NBT_TRUST) : 0);
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
