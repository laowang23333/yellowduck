package com.yourname.yellowduck.change;

import com.yourname.yellowduck.boss.NetcraftBossBase;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class ChangeClone extends NetcraftBossBase {
    private static final EntityDataAccessor<Boolean> ATTACKING =
            SynchedEntityData.defineId(ChangeClone.class, EntityDataSerializers.BOOLEAN);

    private int cooldown;
    private int hitTimer;
    private java.util.UUID hitTarget;

    public ChangeClone(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setBaseTier(4);
        setBaseDamage(120);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 50000.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 120.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    public Component getName() {
        return Component.literal("嫦娥分身");
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ATTACKING, false);
    }

    public boolean attacking() {
        return entityData.get(ATTACKING);
    }

    @Override public int getMeleeDefense() { return ChangeConfig.cloneMeleeDefense(); }
    @Override public int getRangedDefense() { return ChangeConfig.cloneRangedDefense(); }
    @Override public int getMagicDefense() { return ChangeConfig.cloneMagicDefense(); }
    @Override public float getDamageReductionRatio() { return ChangeConfig.cloneDamageReduction(); }
    @Override public boolean shouldIgnoreSpawnDistanceLimit() { return true; }
    @Override public double getDetectionRadius() { return 10.0D; }
    @Override public double getDetectionHatred() { return 4.0D; }
    @Override public boolean shouldDisengageOnDistance() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public void push(net.minecraft.world.entity.Entity entity) {}
    @Override public void push(double x, double y, double z) {}
    @Override public boolean isPlayingAttackAnimation() { return attacking(); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !isAlive()) return;

        ChangeConfig.applyClone(this);

        if (cooldown > 0) cooldown--;

        if (hitTimer > 0 && --hitTimer == 0) {
            Player player = level().getPlayerByUUID(hitTarget);
            if (player != null && player.isAlive() && distanceToSqr(player) <= 25.0D) {
                hurtWithoutKnockback(
                        player,
                        damageSources().mobAttack(this),
                        getNetcraftAttackDamage()
                );
            }
            entityData.set(ATTACKING, false);
        }

        Player player = getHatredManager().getCurrentTarget();
        if (player == null) {
            player = level().getNearestPlayer(this, 10.0D);
        }

        if (player == null || player.isCreative() || player.isSpectator()) return;

        if (distanceToSqr(player) > 9.0D) {
            getNavigation().moveTo(player, 1.0D);
        } else if (cooldown <= 0 && hitTimer <= 0) {
            getNavigation().stop();
            faceTargetForAttack(player);
            cooldown = 40;
            hitTimer = 15;
            hitTarget = player.getUUID();
            entityData.set(ATTACKING, true);
        }
    }
}
