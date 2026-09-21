package com.yourname.yellowduck.silk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

/** 奶块 743/751 腐蚀黑水：每秒 +3 心智，10 秒后向十字四方向各扩散 3 格。 */
public class SilkBlackWater extends Entity {
    private static final EntityDataAccessor<Integer> DEPTH =
            SynchedEntityData.defineId(SilkBlackWater.class, EntityDataSerializers.INT);

    private UUID owner;
    private boolean split;

    public SilkBlackWater(EntityType<? extends SilkBlackWater> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public void configure(SilkBoss boss, int depth) {
        owner = boss.getUUID();
        entityData.set(DEPTH, depth);
    }

    public int depth() {
        return entityData.get(DEPTH);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DEPTH, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // 场地技能不跨重启继续扩散。
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (owner != null) tag.putUUID("SilkOwner", owner);
        tag.putInt("Depth", depth());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (!(level() instanceof ServerLevel serverLevel)
                || owner == null
                || !(serverLevel.getEntity(owner) instanceof SilkBoss boss)
                || !boss.isAlive()) {
            discard();
            return;
        }

        AABB area = getBoundingBox().inflate(SilkBalance.BLACK_WATER_RADIUS, 1.5D, SilkBalance.BLACK_WATER_RADIUS);

        // 2281：每层心火庇护可清掉一格黑水。
        for (ServerPlayer player : serverLevel.getEntitiesOfClass(ServerPlayer.class, area, boss::valid)) {
            if (boss.consumeHeartFire(player)) {
                discard();
                return;
            }
        }

        if (tickCount % 20 == 0) {
            for (ServerPlayer player : serverLevel.getEntitiesOfClass(ServerPlayer.class, area, boss::valid)) {
                boss.corrupt(player, SilkBalance.BLACK_WATER_CORRUPTION);
            }
        }

        if (!split && tickCount >= SilkBalance.BLACK_WATER_SPLIT_TICKS) {
            split = true;
            boss.spreadBlackWater(position(), depth() + 1);
        }

        // 原版黑水可持续扩散；这里仅限制单格最长 90 秒，避免副本异常结束时无限残留。
        if (tickCount >= 90 * 20) discard();
    }
}
