package com.yourname.yellowduck.change;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.UUID;

public final class ChangeEffectEntity extends Entity {
    public static final double MARK_HEIGHT = 2.6D;

    private static final EntityDataAccessor<Integer> TARGET_ID =
            SynchedEntityData.defineId(ChangeEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SPAWN_DELAY =
            SynchedEntityData.defineId(ChangeEffectEntity.class, EntityDataSerializers.INT);

    private int life;
    private final int maxLife;
    private UUID targetUuid;

    public ChangeEffectEntity(EntityType<? extends ChangeEffectEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
        this.maxLife = resolveMaxLife(type);
    }

    private static int resolveMaxLife(EntityType<?> type) {
        if (type == ChangeContent.EFFECT_ATTACK.get()) return 20;
        if (type == ChangeContent.EFFECT_SUMMON.get()) return 40;
        if (type == ChangeContent.EFFECT_DRINK.get()) return 30;
        return 0;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(TARGET_ID, -1);
        entityData.define(SPAWN_DELAY, 0);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        life++;

        if (level().isClientSide) return;

        if (maxLife > 0 && life >= maxLife) {
            discard();
            return;
        }

        if (tickCount < entityData.get(SPAWN_DELAY)) {
            life = 0;
            return;
        }

        int targetId = entityData.get(TARGET_ID);
        if (targetId < 0) return;

        Entity targetEntity = level().getEntity(targetId);
        if (!(targetEntity instanceof LivingEntity target) || !target.isAlive()) {
            discard();
            return;
        }

        MobEffect markEffect = markEffect();
        if (markEffect != null && !target.hasEffect(markEffect)) {
            discard();
            return;
        }

        double yOffset = isMark() ? MARK_HEIGHT : 0.0D;
        moveTo(target.getX(), target.getY() + yOffset, target.getZ());
    }

    public boolean isMark() {
        EntityType<?> type = getType();
        return type == ChangeContent.MARK_DRINK.get()
                || type == ChangeContent.MARK_THIRST.get();
    }

    private MobEffect markEffect() {
        EntityType<?> type = getType();
        if (type == ChangeContent.MARK_DRINK.get()) return ChangeContent.DRINK.get();
        if (type == ChangeContent.MARK_THIRST.get()) return ChangeContent.THIRST.get();
        return null;
    }

    public ResourceLocation effectModelPath() {
        return new ResourceLocation(
                YellowDuckMod.MOD_ID,
                "models/gltf/change/" + modelFile() + ".glb"
        );
    }

    private String modelFile() {
        EntityType<?> type = getType();
        if (type == ChangeContent.EFFECT_ATTACK.get()) return "change_boss_skill_01_01";
        if (type == ChangeContent.EFFECT_SUMMON.get()) return "change_boss_skill_01_03";
        if (type == ChangeContent.EFFECT_DRINK.get()) return "change_boss_skill_01_04";
        if (type == ChangeContent.MARK_DRINK.get()) return "change_boss_skill_03";
        return "change_boss_skill_04";
    }

    public float effectScale() {
        return isMark() ? 0.08F : 0.10F;
    }

    public boolean effectLoop() {
        return markEffect() != null;
    }

    public boolean renderVisible() {
        return tickCount >= entityData.get(SPAWN_DELAY);
    }

    public int effectAgeTicks() {
        return Math.max(0, tickCount - entityData.get(SPAWN_DELAY));
    }

    public float hostYawDegrees() {
        int targetId = entityData.get(TARGET_ID);
        if (targetId >= 0 && level().getEntity(targetId) instanceof LivingEntity target) {
            return target.getYRot();
        }
        return 0.0F;
    }

    public boolean followsHostYaw() {
        return getType() == ChangeContent.EFFECT_ATTACK.get();
    }

    public static void spawnAt(Level level, double x, double y, double z,
                               EntityType<? extends ChangeEffectEntity> type) {
        spawnAt(level, x, y, z, type, 0, 0.05D);
    }

    public static void spawnOn(Level level, LivingEntity target,
                               EntityType<? extends ChangeEffectEntity> type,
                               int delay) {
        if (level.isClientSide || target == null) return;

        ChangeEffectEntity effect = type.create(level);
        if (effect == null) return;

        effect.entityData.set(TARGET_ID, target.getId());
        effect.entityData.set(SPAWN_DELAY, Math.max(0, delay));
        effect.targetUuid = target.getUUID();
        effect.moveTo(target.getX(), target.getY(), target.getZ());
        level.addFreshEntity(effect);
    }

    private static void spawnAt(Level level, double x, double y, double z,
                                EntityType<? extends ChangeEffectEntity> type,
                                int delay, double yOffset) {
        if (level.isClientSide) return;

        ChangeEffectEntity effect = type.create(level);
        if (effect == null) return;

        effect.entityData.set(SPAWN_DELAY, Math.max(0, delay));
        effect.moveTo(x, y + yOffset, z);
        level.addFreshEntity(effect);
    }

    public static void ensureMark(ServerLevel level, LivingEntity target,
                                  EntityType<? extends ChangeEffectEntity> type) {
        if (level == null || target == null) return;

        boolean exists = !level.getEntitiesOfClass(
                ChangeEffectEntity.class,
                target.getBoundingBox().inflate(4.0D),
                effect -> effect.getType() == type
                        && target.getUUID().equals(effect.targetUuid)
        ).isEmpty();
        if (exists) return;

        ChangeEffectEntity effect = type.create(level);
        if (effect == null) return;

        effect.entityData.set(TARGET_ID, target.getId());
        effect.targetUuid = target.getUUID();
        effect.moveTo(target.getX(), target.getY() + MARK_HEIGHT, target.getZ());
        level.addFreshEntity(effect);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("TargetId", entityData.get(TARGET_ID));
        tag.putInt("SpawnDelay", entityData.get(SPAWN_DELAY));
        tag.putInt("EffectLife", life);
        if (targetUuid != null) tag.putUUID("TargetUuid", targetUuid);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(TARGET_ID, tag.getInt("TargetId"));
        entityData.set(SPAWN_DELAY, tag.getInt("SpawnDelay"));
        life = tag.getInt("EffectLife");
        targetUuid = tag.hasUUID("TargetUuid") ? tag.getUUID("TargetUuid") : null;
    }
}
