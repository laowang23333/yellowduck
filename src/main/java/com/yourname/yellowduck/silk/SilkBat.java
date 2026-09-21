package com.yourname.yellowduck.silk;

import com.yourname.yellowduck.particle.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** 奶块 7062：锁定点名瞬间的位置直线飞行，可侧移躲避。 */
public class SilkBat extends Bat {
    private UUID owner;
    private Vec3 velocity = Vec3.ZERO;

    public SilkBat(EntityType<? extends SilkBat> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setResting(false);
    }

    public void launch(SilkBoss boss, Vec3 target) {
        owner = boss.getUUID();
        Vec3 delta = target.subtract(position());
        velocity = delta.lengthSqr() < 1.0E-6D ? new Vec3(0, 0, 0.48D) : delta.normalize().scale(0.48D);
    }

    @Override
    protected void customServerAiStep() {
    }

    @Override
    public void travel(Vec3 input) {
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (!(level() instanceof ServerLevel serverLevel)
                || owner == null
                || !(serverLevel.getEntity(owner) instanceof SilkBoss boss)
                || !boss.isAlive()
                || tickCount > 100) {
            discard();
            return;
        }

        setResting(false);
        setNoGravity(true);
        Vec3 start = position();
        Vec3 end = start.add(velocity);

        net.minecraft.server.level.ServerPlayer victim = null;
        double nearest = Double.MAX_VALUE;
        for (var player : boss.targets()) {
            var box = player.getBoundingBox().inflate(0.35D);
            var intercept = box.clip(start, end);
            if (box.contains(start) || intercept.isPresent()) {
                double distance = box.contains(start) ? 0.0D : start.distanceToSqr(intercept.get());
                if (distance < nearest) {
                    nearest = distance;
                    victim = player;
                }
            }
        }

        var block = level().clip(new net.minecraft.world.level.ClipContext(
                start, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                this));
        if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS
                && start.distanceToSqr(block.getLocation()) <= nearest) {
            discard();
            return;
        }

        if (victim != null) {
            if (boss.hit(victim, SilkBalance.BAT_DAMAGE, 0)) {
                boss.addBlackEnergy(victim, SilkBalance.BLACK_ENERGY_PER_HIT);
            }
            serverLevel.sendParticles(ModParticles.SILK_SOUL.get(),
                    victim.getX(), victim.getY() + 1.0D, victim.getZ(),
                    55, 0.85D, 0.65D, 0.85D, 0.045D);
            discard();
            return;
        }

        move(MoverType.SELF, velocity);
        setYRot((float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z)));
        serverLevel.sendParticles(ModParticles.SILK_FEATHER.get(), getX(), getY() + 0.25D, getZ(),
                2, 0.08D, 0.08D, 0.08D, 0.005D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("SilkOwner", owner);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // 这类技能弹体不跨区块卸载恢复，避免旧技能在重启后继续命中。
        discard();
    }
}
