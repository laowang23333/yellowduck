package com.yourname.yellowduck.entity;

import com.yourname.yellowduck.registry.ModItems;
import com.yourname.yellowduck.util.RabbitFlight;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 玉兔/竹马共用的飞行坐骑实体。
 *
 * 关键点：
 * 1. 不再强制“仅服务端控制”，恢复 Minecraft 原生的本地骑乘预测。
 * 2. 本地玩家和服务端使用同一套飞行位移公式，服务器仍负责最终校正。
 * 3. 双击跳跃在客户端先做短时预测，避免高延迟服务器上按键后半拍才起飞。
 */
public class RabbitMountEntity extends MountEntity {
    private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(RabbitMountEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ENERGY = SynchedEntityData.defineId(RabbitMountEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RESTING = SynchedEntityData.defineId(RabbitMountEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> REST_TICKS = SynchedEntityData.defineId(RabbitMountEntity.class, EntityDataSerializers.INT);

    // 服务端输入状态。
    private boolean jumpHeld;
    private boolean downHeld;
    private int lastPress = -100;
    private int lastInput = -100;
    private int flightTicks;

    // 仅客户端本地骑手使用的预测状态，不写入 SynchedEntityData。
    private boolean clientJumpHeld;
    private boolean clientDownHeld;
    private boolean clientPredictedFlying;
    private int clientLastPress = -100;
    private int clientFlightTicks;
    private int clientPredictionAge;

    public RabbitMountEntity(EntityType<? extends RabbitMountEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(FLYING, false);
        entityData.define(ENERGY, RabbitFlight.MAX);
        entityData.define(RESTING, false);
        entityData.define(REST_TICKS, 0);
    }

    @Override
    public Component getName() {
        return Component.literal("玉兔");
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.RABBIT_MOUNT.get());
    }

    @Override
    protected double getRiderYOffset() {
        return 0.50D;
    }

    @Override
    protected double getRiderForwardOffset() {
        return -0.20D;
    }

    @Override
    public double getPassengersRidingOffset() {
        return getRiderYOffset();
    }

    @Override
    public void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        if (!hasPassenger(passenger)) {
            super.positionRider(passenger, moveFunction);
            return;
        }

        // 本地渲染使用旋转插值，避免服务器角度包到达时乘客位置横向“跳一下”。
        float renderYaw = level().isClientSide
                ? Mth.rotLerp(0.72F, this.yRotO, this.getYRot())
                : this.getYRot();
        double yaw = Math.toRadians(renderYaw);
        double forward = getRiderForwardOffset();
        moveFunction.accept(passenger,
                getX() - Math.sin(yaw) * forward,
                getY() + getPassengersRidingOffset(),
                getZ() + Math.cos(yaw) * forward);

        if (passenger instanceof Player player) {
            player.yBodyRotO = player.yBodyRot;
            player.setYBodyRot(renderYaw);
        }
    }

    /*
     * 不覆盖 isControlledByLocalInstance()。
     * Minecraft 原生实现会让“本地正在骑乘的玩家”在客户端进行预测，
     * 同时服务端仍计算权威结果。旧代码固定返回 !level().isClientSide，
     * 会把本地预测彻底关掉，服务器有 ping 时就会明显顿挫。
     */

    public boolean isFlying() {
        return entityData.get(FLYING);
    }

    public int getEnergy() {
        return entityData.get(ENERGY);
    }

    public boolean isResting() {
        return entityData.get(RESTING);
    }

    public int getRestTicks() {
        return entityData.get(REST_TICKS);
    }

    public void syncEnergy(int energy, boolean resting, int restTicks) {
        entityData.set(ENERGY, energy);
        entityData.set(RESTING, resting);
        entityData.set(REST_TICKS, restTicks);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // NoGravity 会被原版保存，但飞行状态不跨存档恢复。
        setNoGravity(false);
        entityData.set(FLYING, false);
    }

    /** 服务端接收客户端跳跃/下降输入。 */
    public void acceptInput(boolean jump, boolean down) {
        if (level().isClientSide || !(getControllingPassenger() instanceof Player player)) {
            return;
        }

        lastInput = tickCount;
        if (jump && !jumpHeld && !isFlying()) {
            if (tickCount - lastPress <= 7 && !isInWater() && !isInLava()) {
                if (RabbitFlight.canFly(player)) {
                    entityData.set(FLYING, true);
                    flightTicks = 0;
                    setNoGravity(true);
                    setDeltaMovement(getDeltaMovement().x, 0.35D, getDeltaMovement().z);
                    setOnGround(false);
                } else {
                    player.displayClientMessage(Component.literal("§e玉兔正在恢复耐力，回满后才能起飞。"), true);
                }
                lastPress = -100;
            } else {
                lastPress = tickCount;
                if (onGround()) {
                    jumpFromGround();
                }
            }
        }
        jumpHeld = jump;
        downHeld = down;
    }

    /**
     * 客户端本地预测输入。这里只影响自己的实体副本，不会把预测状态发给其他玩家。
     * 服务端随后会通过正常实体同步确认或纠正。
     */
    public void acceptClientInput(boolean jump, boolean down) {
        if (!level().isClientSide || !(getControllingPassenger() instanceof Player)) {
            return;
        }

        if (jump && !clientJumpHeld && !isFlying() && !clientPredictedFlying) {
            if (tickCount - clientLastPress <= 7 && !isInWater() && !isInLava()) {
                // 客户端只在同步到“耐力已满且未恢复中”时才预测起飞，降低误预测概率。
                if (getEnergy() >= RabbitFlight.MAX && !isResting()) {
                    clientPredictedFlying = true;
                    clientFlightTicks = 0;
                    clientPredictionAge = 0;
                    setNoGravity(true);
                    setDeltaMovement(getDeltaMovement().x, 0.35D, getDeltaMovement().z);
                    setOnGround(false);
                }
                clientLastPress = -100;
            } else {
                clientLastPress = tickCount;
                if (onGround()) {
                    jumpFromGround();
                }
            }
        }

        clientJumpHeld = jump;
        clientDownHeld = down;
    }

    public void stopFlying() {
        if (level().isClientSide) {
            return;
        }
        if (isFlying() && getControllingPassenger() instanceof Player player) {
            RabbitFlight.requireRest(player);
        }
        entityData.set(FLYING, false);
        setNoGravity(false);
        jumpHeld = false;
        downHeld = false;
        lastPress = -100;
    }

    private boolean isFlyingForLocalMovement() {
        return isFlying() || (level().isClientSide && clientPredictedFlying);
    }

    @Override
    public void tick() {
        if (!level().isClientSide) {
            // 输入包现在降低了心跳频率，因此给一点更宽的丢包/卡顿容错。
            if (tickCount - lastInput > 30) {
                jumpHeld = false;
                downHeld = false;
            }
            if (isFlying()) {
                flightTicks++;
                if (!(getControllingPassenger() instanceof Player player)
                        || !player.isAlive()
                        || RabbitFlight.energy(player) <= 0
                        || (flightTicks > 4 && onGround())
                        || isInWater()
                        || isInLava()) {
                    stopFlying();
                }
            }
        } else {
            // 服务端确认后结束“预测”标记，但继续保持本地即时移动。
            if (isFlying()) {
                clientPredictedFlying = false;
            }

            if (isFlyingForLocalMovement() && isControlledByLocalInstance()) {
                clientFlightTicks++;
                setNoGravity(true);
            } else {
                clientFlightTicks = 0;
            }

            if (clientPredictedFlying) {
                clientPredictionAge++;
                // 极端情况下服务端拒绝了起飞，但没有产生 FLYING=true 的同步包，
                // 最多预测 1 秒就主动撤销，随后由服务端位置正常纠正。
                if (clientPredictionAge > 20 && !isFlying()) {
                    clientPredictedFlying = false;
                    setNoGravity(false);
                }
            } else if (!isFlying() && isControlledByLocalInstance()) {
                setNoGravity(false);
            }
        }

        super.tick();
    }

    @Override
    public void travel(Vec3 input) {
        if (!isFlyingForLocalMovement()) {
            super.travel(input);
            return;
        }

        // 远程玩家的客户端不自己模拟别人的坐骑，继续使用服务器插值位置。
        if (level().isClientSide && !isControlledByLocalInstance()) {
            return;
        }

        if (!(getControllingPassenger() instanceof Player player)) {
            if (!level().isClientSide) {
                stopFlying();
            }
            return;
        }

        setYRot(player.getYRot());
        yBodyRot = getYRot();
        yHeadRot = getYRot();

        Vec3 horizontal = new Vec3(
                Mth.clamp(player.xxa, -1.0F, 1.0F) * 0.65D,
                0.0D,
                Mth.clamp(player.zza, -1.0F, 1.0F));
        if (horizontal.lengthSqr() > 1.0D) {
            horizontal = horizontal.normalize();
        }

        boolean jumpInput = level().isClientSide ? clientJumpHeld : jumpHeld;
        boolean downInput = level().isClientSide ? clientDownHeld : downHeld;
        double vertical = downInput ? -0.30D : (jumpInput ? 0.30D : 0.0D);
        int sideFlightTicks = level().isClientSide ? clientFlightTicks : flightTicks;
        if (sideFlightTicks <= 4) {
            vertical = 0.35D;
        }

        // 客户端本地玩家和服务端使用完全相同的飞行公式。
        setDeltaMovement(Vec3.ZERO);
        moveRelative(0.42F, horizontal);
        setDeltaMovement(getDeltaMovement().x, vertical, getDeltaMovement().z);
        move(MoverType.SELF, getDeltaMovement());
        fallDistance = 0.0F;
        player.fallDistance = 0.0F;
        calculateEntityAnimation(false);
    }
}
