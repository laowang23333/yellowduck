package com.yourname.yellowduck.entity;

import com.yourname.yellowduck.event.ToyBearEntangleEvents;
import com.yourname.yellowduck.registry.ModEffects;
import com.yourname.yellowduck.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

public class RootVineEntity extends PathfinderMob {
    /* 保留 YellowDuck 当前熊技能的 500 血救援强度。 */
    public static final float ROOT_MAX_HEALTH = 500.0F;

    private UUID targetUUID;
    private UUID ownerBearUUID;

    public RootVineEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
        setPersistenceRequired();
        noPhysics = true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, ROOT_MAX_HEALTH)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D);
    }

    public static RootVineEntity create(ServerLevel level, Player target, UUID ownerBearUUID) {
        RootVineEntity root = ModEntities.ROOT_VINE.get().create(level);
        if (root == null) return null;
        root.targetUUID = target.getUUID();
        root.ownerBearUUID = ownerBearUUID;
        root.moveTo(target.getX(), target.getY(), target.getZ(), target.getYRot(), 0.0F);
        root.setHealth(ROOT_MAX_HEALTH);
        return root;
    }

    public UUID getTargetUUID() {
        return targetUUID;
    }

    public UUID getOwnerBearUUID() {
        return ownerBearUUID;
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);

        if (level().isClientSide) return;

        ServerPlayer target = findTarget();
        if (target == null || !target.isAlive() || target.isRemoved() || target.level() != level()) {
            ToyBearEntangleEvents.releaseByRoot(this);
            discard();
            return;
        }

        // 和原 RootVineEntity 一样，根须始终贴着被缠绕玩家移动。
        moveTo(target.getX(), target.getY(), target.getZ(), target.getYRot(), 0.0F);

        // 牛奶等方式不能单独把 Boss 技能洗掉；根须还活着就刷新效果。
        if (!target.hasEffect(ModEffects.ROOT_ENTANGLE.get())) {
            target.addEffect(new MobEffectInstance(
                    ModEffects.ROOT_ENTANGLE.get(),
                    Integer.MAX_VALUE,
                    0,
                    false,
                    false,
                    true
            ));
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || !isAlive()) return false;

        Entity attacker = source.getEntity();
        if (!(attacker instanceof Player player)) return false;

        if (targetUUID != null && targetUUID.equals(player.getUUID())) return false;

        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            ToyBearEntangleEvents.releaseByRoot(this);
        }
        super.die(source);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }


    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    private ServerPlayer findTarget() {
        if (targetUUID == null || !(level() instanceof ServerLevel serverLevel)) return null;
        return serverLevel.getServer().getPlayerList().getPlayer(targetUUID);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (targetUUID != null) tag.putUUID("TargetUUID", targetUUID);
        if (ownerBearUUID != null) tag.putUUID("OwnerBearUUID", ownerBearUUID);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("TargetUUID")) targetUUID = tag.getUUID("TargetUUID");
        if (tag.hasUUID("OwnerBearUUID")) ownerBearUUID = tag.getUUID("OwnerBearUUID");
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
