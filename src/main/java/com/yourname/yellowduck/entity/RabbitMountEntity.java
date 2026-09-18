package com.yourname.yellowduck.entity;

import com.yourname.yellowduck.registry.ModItems;
import com.yourname.yellowduck.util.RabbitFlight;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** 服务端计算移动、起飞条件及耐力，客户端仅上传按键。 */
public class RabbitMountEntity extends MountEntity {
    private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(RabbitMountEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ENERGY = SynchedEntityData.defineId(RabbitMountEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RESTING = SynchedEntityData.defineId(RabbitMountEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> REST_TICKS = SynchedEntityData.defineId(RabbitMountEntity.class, EntityDataSerializers.INT);
    private boolean jumpHeld, downHeld;
    private int lastPress = -100, lastInput = -100, flightTicks;
    public RabbitMountEntity(EntityType<? extends RabbitMountEntity> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(FLYING, false); entityData.define(ENERGY, RabbitFlight.MAX); entityData.define(RESTING, false); entityData.define(REST_TICKS, 0);
    }
    @Override public Component getName() { return Component.literal("玉兔"); }
    @Override public ItemStack getPickResult() { return new ItemStack(ModItems.RABBIT_MOUNT.get()); }
    @Override protected double getRiderYOffset() { return 1.11D; }
    @Override protected double getRiderForwardOffset() { return 0.0D; }
    @Override public boolean isControlledByLocalInstance() { return !level().isClientSide; }
    public boolean isFlying() { return entityData.get(FLYING); }
    public int getEnergy() { return entityData.get(ENERGY); }
    public boolean isResting() { return entityData.get(RESTING); }
    public int getRestTicks() { return entityData.get(REST_TICKS); }
    public void syncEnergy(int energy, boolean resting, int restTicks) { entityData.set(ENERGY, energy); entityData.set(RESTING, resting); entityData.set(REST_TICKS, restTicks); }
    @Override public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // NoGravity 会被原版保存，但飞行状态不跨存档恢复。
        setNoGravity(false);
        entityData.set(FLYING, false);
    }
    public void acceptInput(boolean jump, boolean down) {
        if (level().isClientSide || !(getControllingPassenger() instanceof Player player)) return;
        lastInput = tickCount;
        if (jump && !jumpHeld && !isFlying()) {
            if (tickCount - lastPress <= 7 && !isInWater() && !isInLava()) {
                if (RabbitFlight.canFly(player)) {
                    entityData.set(FLYING, true); flightTicks = 0; setNoGravity(true);
                    setDeltaMovement(getDeltaMovement().x, 0.35D, getDeltaMovement().z); setOnGround(false);
                } else player.displayClientMessage(Component.literal("§e玉兔正在恢复耐力，回满后才能起飞。"), true);
                lastPress = -100;
            } else { lastPress = tickCount; if (onGround()) jumpFromGround(); }
        }
        jumpHeld = jump; downHeld = down;
    }
    public void stopFlying() {
        if (level().isClientSide) return;
        if (isFlying() && getControllingPassenger() instanceof Player player) RabbitFlight.requireRest(player);
        entityData.set(FLYING, false); setNoGravity(false);
        jumpHeld = false; downHeld = false; lastPress = -100;
    }
    @Override public void tick() {
        if (!level().isClientSide) {
            if (tickCount - lastInput > 10) { jumpHeld = false; downHeld = false; }
            if (isFlying()) {
                flightTicks++;
                if (!(getControllingPassenger() instanceof Player player) || !player.isAlive()
                        || RabbitFlight.energy(player) <= 0 || (flightTicks > 4 && onGround())
                        || isInWater() || isInLava()) stopFlying();
            }
        }
        super.tick();
    }
    @Override public void travel(Vec3 input) {
        if (!isFlying()) { super.travel(input); return; }
        if (level().isClientSide) return;
        if (!(getControllingPassenger() instanceof Player player)) { stopFlying(); return; }
        setYRot(player.getYRot()); yBodyRot = getYRot(); yHeadRot = getYRot();
        Vec3 horizontal = new Vec3(Mth.clamp(player.xxa, -1.0F, 1.0F) * 0.65D, 0, Mth.clamp(player.zza, -1.0F, 1.0F));
        if (horizontal.lengthSqr() > 1) horizontal = horizontal.normalize();
        double vertical = downHeld ? -0.30D : (jumpHeld ? 0.30D : 0.0D);
        if (flightTicks <= 4) vertical = 0.35D;
        setDeltaMovement(Vec3.ZERO); moveRelative(0.42F, horizontal);
        setDeltaMovement(getDeltaMovement().x, vertical, getDeltaMovement().z);
        move(MoverType.SELF, getDeltaMovement());
        fallDistance = 0; player.fallDistance = 0; calculateEntityAnimation(false);
    }
}
