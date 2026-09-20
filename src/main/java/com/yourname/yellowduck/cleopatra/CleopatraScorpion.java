package com.yourname.yellowduck.cleopatra;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/** 艳后召唤的自爆蝎子：回到艳后的召唤点，蓄力后爆炸并生成毒池。 */
public class CleopatraScorpion extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> DATA_CHARGE =
            SynchedEntityData.defineId(CleopatraScorpion.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID ownerId;
    private Entity ownerRef;
    private double ownerLastX;
    private double ownerLastY;
    private double ownerLastZ;
    private boolean ownerPosSet;
    private int ownerScanCooldown;
    private int explodeCharge;
    private boolean initialized;
    private boolean poolSpawned;

    public CleopatraScorpion(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.34D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_CHARGE, 0);
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
        refreshOwnerRef();
    }

    public void setOwnerPos(double x, double y, double z) {
        ownerLastX = x;
        ownerLastY = y;
        ownerLastZ = z;
        ownerPosSet = true;
    }

    private void setCharge(int charge) {
        explodeCharge = charge;
        if (!level().isClientSide) entityData.set(DATA_CHARGE, charge);
    }

    private void refreshOwnerRef() {
        if (ownerId == null || !(level() instanceof ServerLevel serverLevel)) return;
        if (ownerRef != null && ownerRef.isAlive()) return;
        if (ownerScanCooldown-- > 0) return;
        ownerScanCooldown = CleopatraConfig.scorpionOwnerRefreshTicks.get();

        Entity found = serverLevel.getEntity(ownerId);
        if (found != null && found.isAlive()) {
            ownerRef = found;
            ownerLastX = found.getX();
            ownerLastY = found.getY();
            ownerLastZ = found.getZ();
            ownerPosSet = true;
        } else {
            ownerRef = null;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        if (!initialized && tickCount >= 1) {
            initialized = true;
            var hp = getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) hp.setBaseValue(CleopatraConfig.scorpionHealth.get());
            var speed = getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) speed.setBaseValue(CleopatraConfig.scorpionSpeed.get());
            var kb = getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (kb != null) kb.setBaseValue(CleopatraConfig.scorpionKnockbackResistance.get());
            setHealth(getMaxHealth());
        }
        if (!isAlive()) return;

        if (explodeCharge > 0) {
            setCharge(explodeCharge - 1);
            if (explodeCharge <= 0) explodeNow();
            return;
        }

        refreshOwnerRef();
        if (!ownerPosSet) return;

        double dx = ownerLastX - getX();
        double dz = ownerLastZ - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal <= CleopatraConfig.scorpionArrivalDistance.get()) {
            setCharge(CleopatraConfig.scorpionChargeTicks.get());
            getNavigation().stop();
            return;
        }

        double speed = getAttributeValue(Attributes.MOVEMENT_SPEED);
        getNavigation().moveTo(ownerLastX, ownerLastY, ownerLastZ, speed);
    }

    private void explodeNow() {
        if (level().isClientSide) return;
        double r = CleopatraConfig.scorpionExplosionRange.get();
        AABB area = new AABB(getX() - r, getY() - 1.0D, getZ() - r,
                getX() + r, getY() + 2.0D, getZ() + r);
        for (Player player : level().getEntitiesOfClass(Player.class, area, CleopatraUtil::validPlayer)) {
            boolean sick = player.hasEffect(CleopatraEffects.VENOM_SICKNESS.get());
            float damage = player.getMaxHealth() * (sick
                    ? CleopatraConfig.poolSicknessRatio.get().floatValue()
                    : CleopatraConfig.poolNormalRatio.get().floatValue());
            CleopatraUtil.magicHurt(player, player, damage);
        }
        spawnPoolAtFeet();
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.5D, getZ(),
                    4, 1.2D, 0.6D, 1.2D, 0.02D);
        }
        discard();
    }

    private void spawnPoolAtFeet() {
        if (poolSpawned || level().isClientSide) return;
        poolSpawned = true;
        CleopatraVenomPool pool = CleopatraEntities.VENOM_POOL.get().create(level());
        if (pool != null) {
            pool.setPos(getX(), getY(), getZ());
            level().addFreshEntity(pool);
        }
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) spawnPoolAtFeet();
        super.die(source);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> {
            String animation;
            if (isDeadOrDying() || getHealth() <= 0.0F) animation = "death";
            else if (entityData.get(DATA_CHARGE) > 0) animation = "att";
            else if (state.isMoving()) animation = "walk";
            else animation = "free";
            state.getController().setAnimation(RawAnimation.begin().thenLoop(animation));
            return PlayState.CONTINUE;
        }));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.putBoolean("OwnerPosSet", ownerPosSet);
        tag.putDouble("OwnerLastX", ownerLastX);
        tag.putDouble("OwnerLastY", ownerLastY);
        tag.putDouble("OwnerLastZ", ownerLastZ);
        tag.putInt("ExplodeCharge", explodeCharge);
        tag.putBoolean("PoolSpawned", poolSpawned);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
        ownerPosSet = tag.getBoolean("OwnerPosSet");
        ownerLastX = tag.getDouble("OwnerLastX");
        ownerLastY = tag.getDouble("OwnerLastY");
        ownerLastZ = tag.getDouble("OwnerLastZ");
        explodeCharge = tag.getInt("ExplodeCharge");
        poolSpawned = tag.getBoolean("PoolSpawned");
        if (!level().isClientSide) entityData.set(DATA_CHARGE, explodeCharge);
    }
}
