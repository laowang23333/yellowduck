package com.yourname.yellowduck.cleopatra;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** 艳后/沙虫毒弹。艳后齐射不附加中毒，沙虫齐射附加 VENOM_SICKNESS。 */
public class CleopatraVenomBullet extends ThrowableProjectile {
    private float damage = 30.0F;
    private boolean applySickness;

    public CleopatraVenomBullet(EntityType<? extends ThrowableProjectile> type, Level level) {
        super(type, level);
    }

    public void setDamage(float damage) { this.damage = damage; }
    public void setApplySickness(boolean apply) { this.applySickness = apply; }

    @Override protected void defineSynchedData() {}
    @Override protected float getGravity() { return 0.0F; }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && (tickCount > 200 || isInWater())) discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (level().isClientSide) return;
        Entity entity = hit.getEntity();
        if (entity instanceof Player player && CleopatraUtil.validPlayer(player)) {
            LivingEntity owner = getOwner() instanceof LivingEntity living && living.isAlive() ? living : player;
            CleopatraUtil.magicHurt(player, owner, damage);
            if (applySickness) {
                player.addEffect(new MobEffectInstance(CleopatraEffects.VENOM_SICKNESS.get(),
                        CleopatraConfig.sicknessDuration.get(), 0, false, false, true));
            }
        }
        discard();
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!level().isClientSide && hit.getType() != HitResult.Type.ENTITY) discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("BulletDamage", damage);
        tag.putBoolean("ApplySickness", applySickness);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("BulletDamage");
        applySickness = tag.getBoolean("ApplySickness");
    }
}
