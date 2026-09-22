package com.yourname.yellowduck.silk;

import com.yourname.yellowduck.particle.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

/** 奶块 744 黑暗能量球：200 HP，半径 3，每 2 秒 AOE 并 +10 心智腐蚀。 */
public class SilkBlackBall extends PathfinderMob {
    private UUID owner;
    private int ownerMissingTicks;

    public SilkBlackBall(EntityType<? extends SilkBlackBall> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setPersistenceRequired();
        setCustomName(Component.literal("黑暗能量球"));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, SilkBalance.BLACK_BALL_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ARMOR, SilkBalance.BLACK_BALL_ARMOR);
    }

    public void setOwner(SilkBoss boss) {
        owner = boss.getUUID();
        ownerMissingTicks = 0;
        SilkConfig.reapply(this);
    }

    private SilkBoss ownerBossRaw() {
        if (owner == null || !(level() instanceof ServerLevel serverLevel)) return null;
        Entity entity = serverLevel.getEntity(owner);
        return entity instanceof SilkBoss boss ? boss : null;
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public void travel(net.minecraft.world.phys.Vec3 input) {
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        setDeltaMovement(0.0D, 0.0D, 0.0D);
        SilkBoss boss = ownerBossRaw();
        if (boss == null) {
            if (++ownerMissingTicks > 100) discard();
            return;
        }
        if (!boss.isAlive()) {
            discard();
            return;
        }
        // Boss 与召唤物的区块加载 tick 顺序不固定；给未进入战斗状态 5 秒宽限。
        if (!boss.isEncounterActive()) {
            if (++ownerMissingTicks > 100) discard();
            return;
        }
        ownerMissingTicks = 0;

        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.SILK_DARK_FIRE.get(), getX(), getY() + 0.7D, getZ(),
                    3, 0.45D, 0.45D, 0.45D, 0.01D);
        }

        if (SilkBalance.BLACK_BALL_PULSE_COOLDOWN > 0
                && tickCount % SilkBalance.BLACK_BALL_PULSE_COOLDOWN == 0
                && level() instanceof ServerLevel serverLevel) {
            AABB area = getBoundingBox().inflate(SilkBalance.BLACK_BALL_RADIUS);
            for (ServerPlayer player : serverLevel.getEntitiesOfClass(ServerPlayer.class, area, boss::valid)) {
                boss.hit(player, SilkBalance.BLACK_BALL_DAMAGE, SilkBalance.BLACK_BALL_CORRUPTION);
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("SilkOwner", owner);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = tag.hasUUID("SilkOwner") ? tag.getUUID("SilkOwner") : null;
    }
}
