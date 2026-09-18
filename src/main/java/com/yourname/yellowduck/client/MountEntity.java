package com.yourname.yellowduck.entity;

import com.yourname.yellowduck.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

import java.util.UUID;

/**
 * 奶块式坐骑基础实体。
 * 坐骑本身不使用随机游走 AI；只有玩家骑乘时才由玩家控制移动。
 */
public class MountEntity extends PathfinderMob {
    public static final EntityDataAccessor<Boolean> IS_WALKING =
            SynchedEntityData.defineId(MountEntity.class, EntityDataSerializers.BOOLEAN);

    private static final double RIDING_SPEED = 0.34D;
    private static final float MAX_HEALTH = 40.0F;
    private static final double SEAT_Y_OFFSET = 0.0D;
    // GLB 的 seat01 在 Polymesh 居中后的实际坐标约为 1.03 格高、向前 1.03 格。
    private static final double RIDER_Y_OFFSET = 1.45D;
    private static final double FORWARD_OFFSET = 0.65D;

    // 乘骑时的轻微上下/左右晃动，让人物不会像“焊死”在坐骑上一样。
    private static final double RIDER_BOB_AMOUNT = 0.12D;
    private static final double RIDER_SWAY_AMOUNT = 0.075D;
    private static final float RIDER_SWAY_YAW = 4.0F;

    private UUID ownerUUID;

    // 仅客户端 GUI 预览使用，不同步到服务端。
    private boolean guiPreview = false;

    public MountEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, RIDING_SPEED)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D);
    }

    @Override
    public Component getName() {
        return Component.literal("魔化天狗坐骑");
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_WALKING, false);
    }

    @Override
    protected void registerGoals() {
        // 坐骑不主动乱跑，等待主人召唤/骑乘。
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean canRiderInteract() {
        return true;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof Player player ? player : null;
    }

    public void setOwner(Player player) {
        if (player != null) {
            this.ownerUUID = player.getUUID();
        }
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    /** GUI 预览实体专用开关。 */
    public void setGuiPreview(boolean guiPreview) {
        this.guiPreview = guiPreview;
    }

    /** 是否为 GUI 中的本地预览实体。 */
    public boolean isGuiPreview() {
        return guiPreview;
    }

    public boolean isOwner(Player player) {
        return player != null && ownerUUID != null && ownerUUID.equals(player.getUUID());
    }

    @Override
    protected float getJumpPower() {
        return 0.6F;
    }

    @Override
    public double getPassengersRidingOffset() {
        return RIDER_Y_OFFSET;
    }

    @Override
    public void positionRider(Entity passenger, MoveFunction moveFunction) {
        if (!this.hasPassenger(passenger)) {
            super.positionRider(passenger, moveFunction);
            return;
        }

        // 客户端用上一帧到当前帧的旋转做一点插值，避免转向时座位跟随出现“一格一格”的顿挫。
        float renderYaw = this.level().isClientSide
                ? Mth.rotLerp(0.72F, this.yRotO, this.getYRot())
                : this.getYRot();

        double yaw = Math.toRadians(renderYaw);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        double dx = -sin * FORWARD_OFFSET;
        double dz = cos * FORWARD_OFFSET;

        double bob = 0.0D;
        double sway = 0.0D;
        float swayYaw = 0.0F;
        if (passenger instanceof Player && this.isVehicle()) {
            if (this.entityData.get(IS_WALKING)) {
                // 用实体 tick + partial tick 无法直接拿到这里的 partialTick，
                // 所以保持连续的相位；同时把幅度提高到肉眼可见。
                double phase = this.tickCount * 0.82D;
                bob = (0.5D - 0.5D * Math.cos(phase)) * RIDER_BOB_AMOUNT;
                sway = Math.sin(phase * 0.5D) * RIDER_SWAY_AMOUNT;
                swayYaw = (float) Math.sin(phase * 0.5D) * RIDER_SWAY_YAW;
            }
        }

        // sway 沿坐骑横向，和前后座位偏移分离。
        double x = this.getX() + dx + cos * sway;
        double y = this.getY() + this.getPassengersRidingOffset() + SEAT_Y_OFFSET + bob;
        double z = this.getZ() + dz + sin * sway;
        moveFunction.accept(passenger, x, y, z);

        if (passenger instanceof Player player) {
            // 只让玩家身体跟着坐骑左右摆，不修改玩家头部/镜头。
            // 同时更新 O 值，避免身体摆动本身产生另一种“卡一下”。
            float bodyYaw = renderYaw + swayYaw;
            player.yBodyRotO = player.yBodyRot;
            player.setYBodyRot(bodyYaw);
        }
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!this.level().isClientSide && passenger instanceof Player player && ownerUUID == null) {
            ownerUUID = player.getUUID();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isVehicle() && source.getEntity() instanceof Player player && isOwner(player)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            Entity controller = getControllingPassenger();
            if (controller instanceof Player player) {
                // 玩家视角驱动坐骑转向，但不要把 yRotO 强行改成当前值；
                // 保留上一帧旋转值，Minecraft 才能进行平滑插值。
                this.setYRot(player.getYRot());
                this.yHeadRot = this.getYRot();
                this.yBodyRot = this.getYRot();
            } else if (this.getPassengers().isEmpty() && this.tickCount % 10 == 0) {
                this.setDeltaMovement(Vec3.ZERO);
            }

            /*
             * 不使用 getDeltaMovement() 判断移动状态。
             *
             * 独立服务器上，骑乘输入经过客户端/服务器同步后，
             * getDeltaMovement() 在部分 tick 可能已经被清零或不能代表
             * 实际的骑乘位移，导致服务端一直把 IS_WALKING 判成 false。
             *
             * 用本 tick 与上一 tick 的实际坐标差判断，服务器真正发生
             * 位移时才同步 IS_WALKING=true，客户端渲染器即可播放 run。
             */
            double dx = this.getX() - this.xo;
            double dz = this.getZ() - this.zo;
            boolean walking = dx * dx + dz * dz > 1.0E-5D;
            this.entityData.set(IS_WALKING, walking);
        }
    }

    @Override
    public void travel(Vec3 travelVector) {
        Entity controller = getControllingPassenger();
        if (controller instanceof Player player && this.isVehicle()) {
            // 不再同步覆盖 yRotO。
            // yRotO 必须保留上一 tick 的角度，否则客户端旋转插值会被截断，
            // 表现就是横向转身时偶发“小卡一下”。
            this.setYRot(player.getYRot());
            this.yBodyRot = this.getYRot();
            this.yHeadRot = this.getYRot();

            float strafe = player.xxa * 0.65F;
            float forward = player.zza;
            if (forward < 0.0F) {
                forward *= 0.35F;
            }

            this.setSpeed((float) this.getAttributeValue(Attributes.MOVEMENT_SPEED));
            super.travel(new Vec3(strafe, travelVector.y, forward));
            return;
        }

        super.travel(Vec3.ZERO);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUUID != null) {
            tag.putUUID("OwnerUUID", ownerUUID);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("OwnerUUID")) {
            ownerUUID = tag.getUUID("OwnerUUID");
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.GHOST_WOLF_MOUNT.get());
    }
}
