package com.yourname.yellowduck.silk;

import com.yourname.yellowduck.particle.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * 奶块黑暗流星：10 秒紫圈/禁锢结束后从上方落下；命中时在 5 格内分摊伤害并给分摊者 +5 黑暗能量。
 */
public class SilkMeteor extends Entity {
    private UUID owner;
    private Vec3 impact = Vec3.ZERO;
    private int life;

    public SilkMeteor(EntityType<? extends SilkMeteor> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public void configure(SilkBoss boss, Vec3 impact) {
        this.owner = boss.getUUID();
        this.impact = impact;
        moveTo(impact.x, impact.y + 14.0D, impact.z, 0.0F, 0.0F);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (owner != null) tag.putUUID("SilkOwner", owner);
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

        life++;
        double remaining = getY() - impact.y;
        double drop = Math.max(0.55D, remaining * 0.18D);
        setPos(impact.x, Math.max(impact.y, getY() - drop), impact.z);
        serverLevel.sendParticles(ModParticles.SILK_DARK_FIRE.get(), getX(), getY(), getZ(),
                8, 0.35D, 0.35D, 0.35D, 0.02D);
        serverLevel.sendParticles(ModParticles.SILK_SMOKE.get(), getX(), getY(), getZ(),
                4, 0.25D, 0.25D, 0.25D, 0.01D);

        if (getY() <= impact.y + 0.15D || life >= 40) {
            boss.resolveMeteorImpact(impact);
            discard();
        }
    }
}
