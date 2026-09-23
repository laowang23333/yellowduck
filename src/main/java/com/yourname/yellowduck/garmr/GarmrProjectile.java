package com.yourname.yellowduck.garmr;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** P1 三人点名/远程补偿共用的服务端权威投射物。 */
public final class GarmrProjectile extends ThrowableProjectile {
    private float damage = GarmrConfig.RANGED_DAMAGE;
    private float radius = (float) GarmrConfig.PROJECTILE_AOE_RADIUS;

    public GarmrProjectile(EntityType<? extends GarmrProjectile> type, Level level) {
        super(type, level);
    }

    public void configure(float damage, float radius) {
        this.damage = damage;
        this.radius = radius;
    }

    @Override protected void defineSynchedData() {}
    @Override protected float getGravity() { return 0.015F; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY(), getZ(), 2,
                    0.08D, 0.08D, 0.08D, 0.01D);
        }
        if (tickCount > 160) explode();
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (!level().isClientSide) explode();
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!level().isClientSide && hit.getType() != HitResult.Type.ENTITY) explode();
    }

    private void explode() {
        if (!(level() instanceof ServerLevel server) || isRemoved()) return;
        Entity owner = getOwner();
        if (owner instanceof GarmrBoss boss && boss.isAlive()) {
            for (LivingEntity living : server.getEntitiesOfClass(LivingEntity.class,
                    getBoundingBox().inflate(radius), e -> e != boss && e.isAlive())) {
                if (living instanceof net.minecraft.server.level.ServerPlayer player && boss.isParticipant(player)) {
                    boss.damageNoKnockback(player, damage);
                }
            }
        }
        server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("GarmrDamage", damage);
        tag.putFloat("GarmrRadius", radius);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("GarmrDamage")) damage = tag.getFloat("GarmrDamage");
        if (tag.contains("GarmrRadius")) radius = tag.getFloat("GarmrRadius");
    }
}
