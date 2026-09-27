package com.yourname.yellowduck.mixin;

import com.yourname.yellowduck.garmr.GarmrBoss;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 加姆召唤物安全清理。
 *
 * 清理时先完整收集目标实体，再统一 discard，避免一边遍历 ServerLevel 实体表
 * 一边删除实体导致 Mohist/Forge 混合环境中的迭代器失效或空实体引用。
 */
@Mixin(GarmrBoss.class)
public abstract class GarmrSafeCleanupMixin {
    private static final String YD_TAG_OWNER = "GarmrOwner";
    private static final String YD_TAG_ROLE = "GarmrRole";
    private static final String YD_ROLE_ANUBIS = "anubis_netcraft";

    @Shadow(remap = false) private UUID coreAddId;
    @Shadow(remap = false) private UUID anubisId;
    @Shadow(remap = false) private boolean anubisLost;
    @Shadow(remap = false) @Final private List<UUID> p1SkeletonIds;
    @Shadow(remap = false) @Final private Map<UUID, Integer> ladies;
    @Shadow(remap = false) @Final private List<UUID> devils;
    @Shadow(remap = false) private UUID blessingTargetId;
    @Shadow(remap = false) private int blessingUntilTick;
    @Shadow(remap = false) private UUID carrierId;
    @Shadow(remap = false) private int protectionUntilTick;
    @Shadow(remap = false) private UUID pendingCloneTargetId;
    @Shadow(remap = false) private int cloneReadyTick;

    @Inject(method = "clearOwnedSummons", at = @At("HEAD"), cancellable = true, remap = false)
    private void yellowduck$safeClearOwnedSummons(ServerLevel server, CallbackInfo ci) {
        GarmrBoss boss = (GarmrBoss) (Object) this;
        List<Entity> remove = new ArrayList<>();

        for (Entity entity : server.getAllEntities()) {
            if (entity == null || entity == boss || entity.isRemoved()) continue;
            CompoundTag data = entity.getPersistentData();
            if (data.hasUUID(YD_TAG_OWNER) && boss.getUUID().equals(data.getUUID(YD_TAG_OWNER))) {
                remove.add(entity);
            }
        }

        for (Entity entity : remove) {
            if (entity != null && !entity.isRemoved()) entity.discard();
        }

        this.coreAddId = null;
        this.anubisId = null;
        this.anubisLost = true;
        this.p1SkeletonIds.clear();
        this.ladies.clear();
        this.devils.clear();
        this.blessingTargetId = null;
        this.blessingUntilTick = 0;
        this.carrierId = null;
        this.protectionUntilTick = 0;
        this.pendingCloneTargetId = null;
        this.cloneReadyTick = 0;
        ci.cancel();
    }

    @Inject(method = "clearPhaseSummons", at = @At("HEAD"), cancellable = true, remap = false)
    private void yellowduck$safeClearPhaseSummons(ServerLevel server, CallbackInfo ci) {
        GarmrBoss boss = (GarmrBoss) (Object) this;
        List<Entity> remove = new ArrayList<>();

        for (Entity entity : server.getAllEntities()) {
            if (entity == null || entity == boss || entity.isRemoved()) continue;
            CompoundTag data = entity.getPersistentData();
            if (!data.hasUUID(YD_TAG_OWNER) || !boss.getUUID().equals(data.getUUID(YD_TAG_OWNER))) continue;
            if (YD_ROLE_ANUBIS.equals(data.getString(YD_TAG_ROLE))) continue;
            remove.add(entity);
        }

        for (Entity entity : remove) {
            if (entity != null && !entity.isRemoved()) entity.discard();
        }

        this.coreAddId = null;
        this.p1SkeletonIds.clear();
        this.ladies.clear();
        this.devils.clear();
        ci.cancel();
    }
}
