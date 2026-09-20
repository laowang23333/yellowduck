package com.yourname.yellowduck.cleopatra;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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

/** 艳后在生命阈值阶段召唤的固定炮台沙虫。 */
public class CleopatraSandworm extends Monster implements GeoEntity {
    public static final int ST_ACTIVE = 2;
    public static final int ST_ATTACKING = 3;

    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("uppos");
    private static final RawAnimation ANIM_ATTACK = RawAnimation.begin().thenPlay("att");

    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(CleopatraSandworm.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int state = ST_ACTIVE;
    private int stateTimer;
    private int attackCooldown;
    private int bulletDelay;
    private boolean bulletFired;
    private final List<UUID> pendingTargets = new ArrayList<>();
    private UUID preferredTarget;
    private boolean initialized;
    private double fixedX;
    private double fixedZ;
    private boolean fixedPositionReady;

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
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 50.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_STATE, ST_ACTIVE);
    }

    public void setPreferredTarget(Player player) {
        preferredTarget = player == null ? null : player.getUUID();
    }

    private void setState(int value) {
        state = value;
        if (!level().isClientSide) entityData.set(DATA_STATE, value);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        if (!initialized && tickCount >= 1) {
            initialized = true;
            var hp = getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) hp.setBaseValue(CleopatraConfig.sandwormHealth.get());
            var follow = getAttribute(Attributes.FOLLOW_RANGE);
            if (follow != null) follow.setBaseValue(CleopatraConfig.sandwormRange.get());
            setHealth(getMaxHealth());
            setState(state);
            fixedX = getX();
            fixedZ = getZ();
            fixedPositionReady = true;
        }
        if (!isAlive()) return;

        // 沙虫是固定炮台，不参与寻路，也不会被水平推走。
        getNavigation().stop();
        Vec3 motion = getDeltaMovement();
        if (motion.x != 0.0D || motion.z != 0.0D) {
            setDeltaMovement(0.0D, motion.y, 0.0D);
        }
        if (fixedPositionReady && (Math.abs(getX() - fixedX) > 1.0E-4D || Math.abs(getZ() - fixedZ) > 1.0E-4D)) {
            setPos(fixedX, getY(), fixedZ);
        }

        if (state == ST_ACTIVE) {
            if (attackCooldown > 0) attackCooldown--;
            if (attackCooldown > 0) return;

            List<Player> picked = pickVolleyTargets(CleopatraConfig.sandwormTargets.get());
            if (picked.isEmpty()) {
                attackCooldown = CleopatraConfig.sandwormRetryCd.get();
                return;
            }
            beginVolley(picked);
            return;
        }

        if (state == ST_ATTACKING) {
            facePendingTarget();

            if (bulletDelay > 0) {
                bulletDelay--;
                if (bulletDelay == 0 && !bulletFired) {
                    bulletFired = true;
                    fireVolley();
                }
            } else if (!bulletFired) {
                bulletFired = true;
                fireVolley();
            }

            stateTimer--;
            if (stateTimer <= 0) {
                pendingTargets.clear();
                setState(ST_ACTIVE);
                attackCooldown = CleopatraConfig.sandwormAttackCd.get();
            }
        }
    }

    private List<Player> pickVolleyTargets(int max) {
        double range = CleopatraConfig.sandwormRange.get();
        AABB box = new AABB(
                getX() - range, getY() - range, getZ() - range,
                getX() + range, getY() + range, getZ() + range
        );

        List<Player> available = new ArrayList<>(level().getEntitiesOfClass(
                Player.class, box, CleopatraUtil::validPlayer));
        List<Player> selected = new ArrayList<>();

        // 艳后刚召唤沙虫时，优先保留当时的主要目标，避免沙虫生成后第一轮找不到目标。
        if (preferredTarget != null) {
            Player preferred = findPlayer(preferredTarget);
            if (preferred != null && CleopatraUtil.validPlayer(preferred)
                    && preferred.level() == level() && preferred.distanceToSqr(this) <= range * range) {
                selected.add(preferred);
                available.remove(preferred);
            }
        }

        while (!available.isEmpty() && selected.size() < max) {
            selected.add(available.remove(random.nextInt(available.size())));
        }
        return selected;
    }

    private void beginVolley(List<Player> targets) {
        pendingTargets.clear();
        for (Player player : targets) {
            if (player != null && player.isAlive()) pendingTargets.add(player.getUUID());
        }
        if (pendingTargets.isEmpty()) {
            attackCooldown = CleopatraConfig.sandwormRetryCd.get();
            return;
        }

        // 首轮锁定目标只用于保证召唤后的第一次攻击，之后恢复随机选择。
        preferredTarget = null;
        setState(ST_ATTACKING);
        stateTimer = CleopatraConfig.sandwormAttackAnimTicks.get();
        bulletDelay = CleopatraConfig.sandwormBulletDelay.get();
        bulletFired = false;
        facePendingTarget();
    }

    private void facePendingTarget() {
        if (pendingTargets.isEmpty()) return;
        Player player = findPlayer(pendingTargets.get(0));
        if (player == null || !player.isAlive()) return;

        double dx = player.getX() - getX();
        double dz = player.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0E-6D) return;
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private Player findPlayer(UUID id) {
        if (!(level() instanceof ServerLevel serverLevel) || id == null) return null;
        Player player = serverLevel.getServer().getPlayerList().getPlayer(id);
        return player != null && player.level() == level() ? player : null;
    }

    private void fireVolley() {
        if (!(level() instanceof ServerLevel)) return;

        for (UUID id : new ArrayList<>(pendingTargets)) {
            Player player = findPlayer(id);
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
        pendingTargets.clear();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, animationState -> {
            int synced = entityData.get(DATA_STATE);
            animationState.getController().setAnimation(synced == ST_ATTACKING ? ANIM_ATTACK : ANIM_IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("WormState", state);
        tag.putInt("WormStateTimer", stateTimer);
        tag.putInt("WormAttackCD", attackCooldown);
        tag.putInt("WormBulletDelay", bulletDelay);
        tag.putBoolean("WormBulletFired", bulletFired);
        if (preferredTarget != null) tag.putUUID("PreferredTarget", preferredTarget);
        if (fixedPositionReady) {
            tag.putDouble("WormFixedX", fixedX);
            tag.putDouble("WormFixedZ", fixedZ);
            tag.putBoolean("WormFixedReady", true);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        state = Math.max(ST_ACTIVE, tag.contains("WormState") ? tag.getInt("WormState") : ST_ACTIVE);
        stateTimer = tag.getInt("WormStateTimer");
        attackCooldown = tag.contains("WormAttackCD") ? tag.getInt("WormAttackCD") : CleopatraConfig.sandwormAttackCd.get();
        bulletDelay = tag.getInt("WormBulletDelay");
        bulletFired = tag.getBoolean("WormBulletFired");
        preferredTarget = tag.hasUUID("PreferredTarget") ? tag.getUUID("PreferredTarget") : null;
        if (tag.getBoolean("WormFixedReady")) {
            fixedX = tag.getDouble("WormFixedX");
            fixedZ = tag.getDouble("WormFixedZ");
            fixedPositionReady = true;
        }

        // 存档不会保存正在攻击的玩家引用，重载后直接回到待机并重新找目标。
        pendingTargets.clear();
        if (state == ST_ATTACKING) {
            state = ST_ACTIVE;
            stateTimer = 0;
            bulletDelay = 0;
            bulletFired = false;
            attackCooldown = Math.min(20, CleopatraConfig.sandwormRetryCd.get());
        }
        if (!level().isClientSide) entityData.set(DATA_STATE, state);
    }
}
