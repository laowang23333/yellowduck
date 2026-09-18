package com.yourname.yellowduck.silk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.UUID;

/** 40生命，可被近战/远程摧毁。追踪最近参与者，接触或20秒后爆炸；摧毁则不爆炸。 */
public class SilkMeteor extends PathfinderMob {
    private UUID owner;
    public SilkMeteor(EntityType<? extends SilkMeteor> type, Level level) {
        super(type, level); setNoGravity(true); setCustomName(Component.literal("追踪陨石")); setCustomNameVisible(true);
    }
    public void setOwner(SilkBoss boss) { owner = boss.getUUID(); }
    @Override protected void registerGoals() {}
    @Override public void travel(Vec3 input) {}
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide || !isAlive()) return;
        if (!(level() instanceof ServerLevel sl) || owner == null || !(sl.getEntity(owner) instanceof SilkBoss boss) || !boss.isAlive()) { discard(); return; }
        var target = boss.targets().stream().min(Comparator.comparingDouble(p -> distanceToSqr(p))).orElse(null);
        if (target == null) { discard(); return; }
        if (distanceToSqr(target) < 6.25 || tickCount >= 400) {
            boss.aoe(position(), 5, SilkBalance.METEOR_DAMAGE, 10); discard(); return;
        }
        Vec3 movement = target.position().add(0, 0.8, 0).subtract(position()).normalize().scale(0.16);
        setNoGravity(true); move(MoverType.SELF, movement);
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH, getX(), getY() + 0.5, getZ(), 4, 0.5, 0.5, 0.5, 0);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); discard(); }
}
