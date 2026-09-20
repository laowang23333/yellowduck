package com.yourname.yellowduck.cleopatra;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** 蝎子爆炸/死亡后留下的 30 秒毒池。 */
public class CleopatraVenomPool extends Entity {
    private int life;

    public CleopatraVenomPool(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override protected void defineSynchedData() {}
    @Override public boolean isPickable() { return false; }
    @Override public boolean isAttackable() { return false; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        life++;
        if (life > CleopatraConfig.poolLife.get()) {
            discard();
            return;
        }
        if (life % CleopatraConfig.poolDamageInterval.get() != 0) return;

        double r = CleopatraConfig.poolRadius.get();
        AABB box = new AABB(getX() - r, getY() - 0.5D, getZ() - r,
                getX() + r, getY() + 2.0D, getZ() + r);
        for (Player player : level().getEntitiesOfClass(Player.class, box, CleopatraUtil::validPlayer)) {
            boolean sick = player.hasEffect(CleopatraEffects.VENOM_SICKNESS.get());
            float damage = player.getMaxHealth() * (float) (sick
                    ? CleopatraConfig.poolSicknessRatio.get()
                    : CleopatraConfig.poolNormalRatio.get());
            CleopatraUtil.magicHurt(player, player, damage);
        }
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putInt("PoolLife", life); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { life = tag.getInt("PoolLife"); }
}
