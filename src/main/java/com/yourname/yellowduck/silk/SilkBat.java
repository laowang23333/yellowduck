package com.yourname.yellowduck.silk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** 向点名时的位置飞行，可侧移躲避；沿移动线段检测碰撞，避免高速穿过玩家。 */
public class SilkBat extends Bat {
    private UUID owner;
    private Vec3 velocity = Vec3.ZERO;
    public SilkBat(EntityType<? extends SilkBat> type, Level level) { super(type, level); setNoGravity(true); setResting(false); }
    public void launch(SilkBoss boss, Vec3 target) {
        owner = boss.getUUID(); velocity = target.subtract(position()).normalize().scale(0.48);
    }
    @Override protected void customServerAiStep() {}
    @Override public void travel(Vec3 input) {}
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (!(level() instanceof ServerLevel sl) || owner == null || !(sl.getEntity(owner) instanceof SilkBoss boss) || !boss.isAlive() || tickCount > 100) { discard(); return; }
        setResting(false); setNoGravity(true);
        Vec3 start = position(), end = start.add(velocity);
        net.minecraft.server.level.ServerPlayer victim = null; double nearest = Double.MAX_VALUE;
        for (var p : boss.targets()) {
            var box = p.getBoundingBox().inflate(0.35);
            var intercept = box.clip(start, end);
            if (box.contains(start) || intercept.isPresent()) {
                double d = box.contains(start) ? 0 : start.distanceToSqr(intercept.get());
                if (d < nearest) { nearest = d; victim = p; }
            }
        }
        var block = level().clip(new net.minecraft.world.level.ClipContext(start, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
        if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS && start.distanceToSqr(block.getLocation()) <= nearest) { discard(); return; }
        if (victim != null) { boss.hit(victim, SilkBalance.BAT_DAMAGE, SilkBalance.SKILL_CORRUPTION); discard(); return; }
        move(MoverType.SELF, velocity); setYRot((float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z)));
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, getX(), getY(), getZ(), 2, 0.1, 0.1, 0.1, 0);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); if (owner != null) tag.putUUID("SilkOwner", owner); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); discard(); }
}
