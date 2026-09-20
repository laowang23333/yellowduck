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

import java.util.UUID;

/** 三蛇炸弹头顶标记。效果被正确元素圈移除时自动消失；300 tick 未解除则爆炸。 */
public class CleopatraBombMark extends Entity {
    private UUID carrierId;
    private int life;
    private float damageMult = 1.0F;

    public CleopatraBombMark(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public void setCarrier(Player player) {
        carrierId = player.getUUID();
        setPos(player.getX(), player.getY() + 2.6D, player.getZ());
    }
    public UUID getCarrierUuid() { return carrierId; }
    public void setDamageMult(float value) { damageMult = value; }

    public MobEffect getBombEffect() {
        if (getType() == CleopatraEntities.BOMB_MARK_BURNING.get()) return CleopatraEffects.VENOM_BOMB_BURNING.get();
        if (getType() == CleopatraEntities.BOMB_MARK_FROZEN.get()) return CleopatraEffects.VENOM_BOMB_FROZEN.get();
        return CleopatraEffects.VENOM_BOMB_POISON.get();
    }

    public int getKind() {
        if (getType() == CleopatraEntities.BOMB_MARK_BURNING.get()) return 1;
        if (getType() == CleopatraEntities.BOMB_MARK_FROZEN.get()) return 2;
        return 0;
    }

    @Override protected void defineSynchedData() {}
    @Override public boolean isPickable() { return false; }
    @Override public boolean isAttackable() { return false; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || carrierId == null) return;
        life++;
        if (!(level() instanceof ServerLevel serverLevel)) return;
        Player carrier = serverLevel.getServer().getPlayerList().getPlayer(carrierId);
        if (!CleopatraUtil.validPlayer(carrier)) {
            discard();
            return;
        }
        if (!carrier.hasEffect(getBombEffect())) {
            discard();
            return;
        }
        setPos(carrier.getX(), carrier.getY() + 2.6D, carrier.getZ());
        if (life >= CleopatraConfig.bombFuseTicks.get()) explode(serverLevel, carrier);
    }

    private void explode(ServerLevel serverLevel, Player carrier) {
        float damage = carrier.getMaxHealth()
                * CleopatraConfig.bombDamageMaxHealthMultiplier.get().floatValue() * damageMult;
        double r = CleopatraConfig.bombRadius.get();
        AABB area = new AABB(getX() - r, getY() - r, getZ() - r,
                getX() + r, getY() + r, getZ() + r);
        for (Player player : level().getEntitiesOfClass(Player.class, area, CleopatraUtil::validPlayer)) {
            player.hurt(player.damageSources().magic(), damage);
        }
        serverLevel.sendParticles(ParticleTypes.POOF, getX(), getY(), getZ(), 6,
                2.0D, 1.0D, 2.0D, 0.02D);
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (carrierId != null) tag.putUUID("Carrier", carrierId);
        tag.putInt("BombLife", life);
        tag.putFloat("DamageMult", damageMult);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Carrier")) carrierId = tag.getUUID("Carrier");
        life = tag.getInt("BombLife");
        damageMult = tag.contains("DamageMult") ? tag.getFloat("DamageMult") : 1.0F;
    }
}
