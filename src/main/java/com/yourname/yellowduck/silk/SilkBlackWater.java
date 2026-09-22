package com.yourname.yellowduck.silk;

import com.yourname.yellowduck.particle.ModParticles;
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
    /** 原 NPC743/751 配置存在时间为 1800 秒；副本正常结束时会更早由 Boss 清理。 */
    private static final int MAX_LIFE_TICKS = 1800 * 20;

    private UUID owner;
    private boolean split;
    private int ageTicks;
    private int ownerMissingTicks;

    public SilkBlackWater(EntityType<? extends SilkBlackWater> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public void configure(SilkBoss boss, int depth) {
        owner = boss.getUUID();
        entityData.set(DEPTH, depth);
        ageTicks = 0;
        ownerMissingTicks = 0;
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
        owner = tag.hasUUID("SilkOwner") ? tag.getUUID("SilkOwner") : null;
        entityData.set(DEPTH, Math.max(0, tag.getInt("Depth")));
        split = tag.getBoolean("SilkSplit");
        ageTicks = Math.max(0, tag.getInt("SilkAge"));
        ownerMissingTicks = 0;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (owner != null) tag.putUUID("SilkOwner", owner);
        tag.putInt("Depth", depth());
        tag.putBoolean("SilkSplit", split);
        tag.putInt("SilkAge", ageTicks);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (!(level() instanceof ServerLevel serverLevel)) return;

        ageTicks++;

        SilkBoss boss = null;
        if (owner != null && serverLevel.getEntity(owner) instanceof SilkBoss found) {
            boss = found;
        }
        if (boss == null) {
            // 区块加载顺序可能不同，给拥有者 5 秒加载宽限；之后清掉孤儿场地技能。
            if (++ownerMissingTicks > 100) discard();
            return;
        }
        if (!boss.isAlive()) {
            discard();
            return;
        }
        if (!boss.isEncounterActive()) {
            // 区块加载时 Boss 可能晚几个 tick 恢复；5 秒后仍未重新进入战斗才清理上一场黑水。
            if (++ownerMissingTicks > 100) discard();
            return;
        }
        ownerMissingTicks = 0;

        AABB area = getBoundingBox().inflate(SilkBalance.BLACK_WATER_RADIUS, 1.5D, SilkBalance.BLACK_WATER_RADIUS);

        // 2281：每层心火庇护可清掉一格黑水。
        for (ServerPlayer player : serverLevel.getEntitiesOfClass(ServerPlayer.class, area, boss::valid)) {
            if (boss.consumeHeartFire(player)) {
                discard();
                return;
            }
        }

        if (ageTicks % 20 == 0) {
            for (ServerPlayer player : serverLevel.getEntitiesOfClass(ServerPlayer.class, area, boss::valid)) {
                boss.corrupt(player, SilkBalance.BLACK_WATER_CORRUPTION);
            }
        }

        // 2299：10 秒后在 X±3 / Z±3 各生成一滩 743；子黑水也按同一规则继续扩散。
        if (!split && ageTicks >= SilkBalance.BLACK_WATER_SPLIT_TICKS) {
            split = true;
            serverLevel.sendParticles(ModParticles.SILK_DARK_FIRE.get(), getX(), getY() + 0.08D, getZ(),
                    26, SilkBalance.BLACK_WATER_SPREAD_DISTANCE * 0.55D, 0.08D,
                    SilkBalance.BLACK_WATER_SPREAD_DISTANCE * 0.55D, 0.018D);
            serverLevel.sendParticles(ModParticles.SILK_SMOKE.get(), getX(), getY() + 0.10D, getZ(),
                    14, 1.4D, 0.06D, 1.4D, 0.01D);
            boss.spreadBlackWater(position(), depth() + 1);
        }

        if (ageTicks >= MAX_LIFE_TICKS) discard();
    }
}
