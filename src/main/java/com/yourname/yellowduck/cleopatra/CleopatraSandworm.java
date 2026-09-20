package com.yourname.yellowduck.cleopatra;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 艳后在 75%/50% 血量阈值召唤的固定炮台沙虫。 */
public class CleopatraSandworm extends Monster implements GeoEntity {
    public static final int ST_ACTIVE = 2;
    public static final int ST_ATTACKING = 3;
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(CleopatraSandworm.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int state = ST_ACTIVE;
    private int stateTimer;
    private int attackCooldown;
    private int bulletDelay;
    private boolean bulletFired;
    private final List<UUID> pendingTargets = new ArrayList<>();
    private boolean initialized;

    public CleopatraSandworm(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        state = ST_ACTIVE;
        attackCooldown = CleopatraConfig.sandwormAttackCd.get();
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_STATE, ST_ACTIVE);
    }

    private void setState(int value) {
        state = value;
        if (!level().isClientSide) entityData.set(DATA_STATE, value);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        if (!initialized && tickCount >= 1) {
            initialized = true;
            var hp = getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) hp.setBaseValue(CleopatraConfig.sandwormHealth.get());
            setHealth(getMaxHealth());
            setState(state);
        }
        if (!isAlive()) return;

        if (state == ST_ACTIVE) {
            if (attackCooldown > 0) {
                attackCooldown--;
                return;
            }
            List<Player> picked = pickVolleyTargets(CleopatraConfig.sandwormTargets.get());
            if (picked.isEmpty()) {
                attackCooldown = CleopatraConfig.sandwormRetryCd.get();
                return;
            }
            beginVolley(picked);
            return;
        }

        if (state == ST_ATTACKING) {
            if (stateTimer > 0) stateTimer--;
            if (bulletDelay > 0) bulletDelay--;
            if (!bulletFired && bulletDelay <= 0) {
                bulletFired = true;
                fireVolley();
            }
            if (stateTimer <= 0) {
                pendingTargets.clear();
                setState(ST_ACTIVE);
                attackCooldown = CleopatraConfig.sandwormAttackCd.get();
            }
        }
    }

    private List<Player> pickVolleyTargets(int max) {
        List<Player> available = new ArrayList<>(level().getEntitiesOfClass(Player.class,
                getBoundingBox().inflate(CleopatraConfig.sandwormRange.get()), CleopatraUtil::validPlayer));
        List<Player> selected = new ArrayList<>();
        while (!available.isEmpty() && selected.size() < max) {
            selected.add(available.remove(random.nextInt(available.size())));
        }
        return selected;
    }

    private void beginVolley(List<Player> targets) {
        pendingTargets.clear();
        for (Player player : targets) pendingTargets.add(player.getUUID());
        setState(ST_ATTACKING);
        stateTimer = CleopatraConfig.sandwormAttackAnimTicks.get();
        bulletDelay = CleopatraConfig.sandwormBulletDelay.get();
        bulletFired = false;
    }

    private void fireVolley() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        for (UUID id : pendingTargets) {
            Player player = serverLevel.getPlayerByUUID(id);
            if (!CleopatraUtil.validPlayer(player)) continue;

            CleopatraVenomBullet bullet = CleopatraEntities.VENOM_BULLET.get().create(level());
            if (bullet == null) continue;
            bullet.setOwner(this);
            bullet.setDamage(CleopatraConfig.sandwormBulletDamage.get().floatValue());
            bullet.setApplySickness(true);
            bullet.setPos(getX(), getY() + 1.2D, getZ());

            double dx = player.getX() - getX();
            double dy = player.getY() + player.getBbHeight() * 0.6D - getY() - 1.2D;
            double dz = player.getZ() - getZ();
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 0.01D) continue;
            bullet.shoot(dx / len * 1.4D, dy / len * 1.4D, dz / len * 1.4D, 1.4F, 1.0F);
            level().addFreshEntity(bullet);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> {
            int synced = entityData.get(DATA_STATE);
            state.getController().setAnimation(RawAnimation.begin().thenLoop(synced == ST_ATTACKING ? "att" : "uppos"));
            return PlayState.CONTINUE;
        }));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("WormState", state);
        tag.putInt("WormStateTimer", stateTimer);
        tag.putInt("WormAttackCD", attackCooldown);
        tag.putInt("WormBulletDelay", bulletDelay);
        tag.putBoolean("WormBulletFired", bulletFired);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        state = tag.contains("WormState") ? tag.getInt("WormState") : ST_ACTIVE;
        stateTimer = tag.getInt("WormStateTimer");
        attackCooldown = tag.contains("WormAttackCD") ? tag.getInt("WormAttackCD") : CleopatraConfig.sandwormAttackCd.get();
        bulletDelay = tag.getInt("WormBulletDelay");
        bulletFired = tag.getBoolean("WormBulletFired");
        if (!level().isClientSide) entityData.set(DATA_STATE, state);
    }
}
