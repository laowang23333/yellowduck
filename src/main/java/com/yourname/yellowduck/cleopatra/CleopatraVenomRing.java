package com.yourname.yellowduck.cleopatra;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** 三蛇生成的元素净化圈。有人进入时全场受伤，圈内玩家对应炸弹被解除。 */
public class CleopatraVenomRing extends Entity {
    private float damageMult = 1.0F;

    public CleopatraVenomRing(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public void setDamageMult(float value) { damageMult = value; }
    public float getDamageMult() { return damageMult; }

    /** 0=火圈，1=冰圈，2=净化/普通圈，仅用于客户端选择模型。 */
    public int getKind() {
        if (getType() == CleopatraEntities.VENOM_RING_ICE.get()) return 1;
        if (getType() == CleopatraEntities.VENOM_RING_PLAIN.get()) return 2;
        return 0;
    }

    public MobEffect getClearedBombEffect() {
        if (getType() == CleopatraEntities.VENOM_RING_ICE.get()) return CleopatraEffects.VENOM_BOMB_FROZEN.get();
        if (getType() == CleopatraEntities.VENOM_RING_PLAIN.get()) return CleopatraEffects.VENOM_BOMB_POISON.get();
        return CleopatraEffects.VENOM_BOMB_BURNING.get();
    }

    @Override protected void defineSynchedData() {}
    @Override public boolean isPickable() { return false; }
    @Override public boolean isAttackable() { return false; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        double exist = CleopatraConfig.ringSnakeExistRadius.get();
        if (level().getEntitiesOfClass(CleopatraVenomSnake.class, getBoundingBox().inflate(exist),
                snake -> snake.isAlive() && !snake.isRemoved()).isEmpty()) {
            discard();
            return;
        }

        double triggerR = CleopatraConfig.ringTriggerRadius.get();
        AABB triggerBox = new AABB(getX() - triggerR, getY() - 0.5D, getZ() - triggerR,
                getX() + triggerR, getY() + 2.5D, getZ() + triggerR);
        var inside = level().getEntitiesOfClass(Player.class, triggerBox, CleopatraUtil::validPlayer);
        if (inside.isEmpty()) return;

        float damage = CleopatraConfig.ringDamage.get().floatValue() * damageMult;
        double damageR = CleopatraConfig.ringDamageRadius.get();
        AABB damageBox = new AABB(getX() - damageR, getY() - damageR, getZ() - damageR,
                getX() + damageR, getY() + damageR, getZ() + damageR);
        for (Player player : level().getEntitiesOfClass(Player.class, damageBox, CleopatraUtil::validPlayer)) {
            player.hurt(player.damageSources().magic(), damage);
        }

        MobEffect effect = getClearedBombEffect();
        for (Player player : inside) player.removeEffect(effect);

        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.1D, getZ(),
                    8, 1.8D, 0.2D, 1.8D, 0.02D);
        }
        discard();
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putFloat("DamageMult", damageMult); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        damageMult = tag.contains("DamageMult") ? tag.getFloat("DamageMult") : 1.0F;
    }
}
