package com.yourname.yellowduck.silk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** 奶块技能辅助视觉：流星圈、火雨圈、心火光柱、火元素。 */
public class SilkVisualCircle extends Entity {
    public static final int PURPLE_METEOR = 0;
    public static final int RED_FIRE_RAIN = 1;
    public static final int HEART_PILLAR = 2;
    public static final int FIRE_ORB = 3;

    private static final EntityDataAccessor<Integer> STYLE =
            SynchedEntityData.defineId(SilkVisualCircle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE =
            SynchedEntityData.defineId(SilkVisualCircle.class, EntityDataSerializers.INT);

    public SilkVisualCircle(EntityType<? extends SilkVisualCircle> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public void configure(int style, int lifeTicks) {
        entityData.set(STYLE, style);
        entityData.set(LIFE, lifeTicks);
    }

    public int style() {
        return entityData.get(STYLE);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(STYLE, PURPLE_METEOR);
        entityData.define(LIFE, 200);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount >= entityData.get(LIFE)) discard();
    }
}
