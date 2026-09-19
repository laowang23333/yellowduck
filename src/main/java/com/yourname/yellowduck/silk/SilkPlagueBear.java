package com.yourname.yellowduck.silk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * 疯狂教授斯尔克的“疫病转移之熊”。
 *
 * 外观直接复用小樱布偶熊的 entity_toy_bear.glb；行为独立：
 * - 永远追踪教授当前仇恨最高的玩家；
 * - 正常伤害无法杀死；
 * - 只有玩家身上的暗黑疫病到期并靠近它时，才允许被疫病牺牲。
 */
public class SilkPlagueBear extends PathfinderMob {
    private UUID ownerSilk;
    private boolean plagueSacrifice;

    public SilkPlagueBear(EntityType<? extends SilkPlagueBear> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCustomName(Component.literal("疫病转移之熊"));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200000.0D)
                .add(Attributes.ATTACK_DAMAGE, 18.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.10D, true));
        // 不注册 HurtByTargetGoal / NearestAttackableTargetGoal：目标只由教授仇恨表决定。
    }

    public void setOwner(SilkBoss boss) {
        ownerSilk = boss.getUUID();
        getPersistentData().putUUID("SilkOwner", ownerSilk);
    }

    public SilkBoss getOwnerBoss() {
        if (ownerSilk == null || !(level() instanceof ServerLevel serverLevel)) return null;
        Entity entity = serverLevel.getEntity(ownerSilk);
        return entity instanceof SilkBoss boss && boss.isAlive() ? boss : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !isAlive()) return;

        SilkBoss boss = getOwnerBoss();
        if (boss == null) {
            discard();
            return;
        }

        ServerPlayer highest = boss.getHatredManager().getHighestHatredTarget() instanceof ServerPlayer player
                && boss.valid(player) ? player : null;
        if (getTarget() != highest) {
            setTarget(highest);
        }
        if (highest != null) {
            getLookControl().setLookAt(highest, 30.0F, 30.0F);
        } else {
            getNavigation().stop();
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        Vec3 motion = target.getDeltaMovement();
        boolean hit = super.doHurtTarget(target);
        if (hit) target.setDeltaMovement(motion); // 与 NetCraft Boss 一样，不把目标打退。
        return hit;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 疫病熊在正常战斗中不可被杀；疫病牺牲走 sacrificeForPlague()。
        if (!plagueSacrifice) return false;
        return super.hurt(source, amount);
    }

    public void sacrificeForPlague() {
        if (level().isClientSide || plagueSacrifice || !isAlive()) return;
        plagueSacrifice = true;
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                    getX(), getY() + getBbHeight() * 0.55D, getZ(),
                    40, 0.75D, 0.8D, 0.75D, 0.04D);
        }
        // 直接移除，避免任何外部伤害/掉落逻辑把“只有疫病能消灭”绕过去。
        discard();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerSilk != null) tag.putUUID("SilkOwner", ownerSilk);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerSilk = tag.hasUUID("SilkOwner") ? tag.getUUID("SilkOwner") : null;
        if (ownerSilk != null) getPersistentData().putUUID("SilkOwner", ownerSilk);
    }
}
