package com.yourname.yellowduck.silk;

import com.yourname.yellowduck.particle.ModParticles;
import com.yourname.yellowduck.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** 奶块 741 黑暗泰迪：普攻 +5 心智腐蚀，30 秒一次怒吼 +2 心智并减速。 */
public class SilkDarkTeddy extends PathfinderMob {
    private UUID owner;
    private int nextHit;
    private int nextRoar;

    public SilkDarkTeddy(EntityType<? extends SilkDarkTeddy> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCustomName(Component.literal("黑暗泰迪"));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, SilkBalance.TEDDY_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, SilkBalance.TEDDY_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, SilkBalance.TEDDY_MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, SilkBalance.TEDDY_FOLLOW_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ARMOR, SilkBalance.TEDDY_ARMOR);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.05D, true));
    }

    public void setOwner(SilkBoss boss) {
        owner = boss.getUUID();
        SilkConfig.reapply(this);
    }

    private SilkBoss boss() {
        if (owner == null || !(level() instanceof ServerLevel serverLevel)) return null;
        Entity entity = serverLevel.getEntity(owner);
        return entity instanceof SilkBoss boss && boss.isAlive() ? boss : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        SilkBoss boss = boss();
        if (boss == null) {
            discard();
            return;
        }

        ServerPlayer target = boss.getHatredManager().getHighestHatredTarget() instanceof ServerPlayer player
                && boss.valid(player) ? player : null;
        if (getTarget() != target) setTarget(target);

        if (nextRoar == 0) nextRoar = tickCount + SilkBalance.TEDDY_ROAR_COOLDOWN;
        if (tickCount >= nextRoar) {
            roar(boss);
            nextRoar = tickCount + SilkBalance.TEDDY_ROAR_COOLDOWN;
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!(target instanceof ServerPlayer player) || tickCount < nextHit) return false;
        SilkBoss boss = boss();
        if (boss == null || !boss.valid(player)) return false;
        nextHit = tickCount + SilkBalance.TEDDY_HIT_COOLDOWN; // 7411 默认 2 秒。
        boolean hit = boss.hit(player, SilkBalance.TEDDY_DAMAGE, SilkBalance.TEDDY_HIT_CORRUPTION);
        if (hit && level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, blockPosition(), ModSounds.SILK_BEAR_HURT.get(), SoundSource.HOSTILE, 1.2F, 1.0F);
        }
        return hit;
    }

    private void roar(SilkBoss boss) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        serverLevel.playSound(null, blockPosition(), ModSounds.SILK_BEAR_ROAR.get(), SoundSource.HOSTILE, 1.8F, 1.0F);
        serverLevel.sendParticles(ModParticles.SILK_GUSH.get(), getX(), getY() + 1.0D, getZ(),
                120, 2.5D, 1.2D, 2.5D, 0.025D);
        serverLevel.sendParticles(ModParticles.SILK_STONE_SMOKE.get(), getX(), getY() + 1.0D, getZ(),
                70, 2.5D, 1.2D, 2.5D, 0.02D);
        AABB box = getBoundingBox().inflate(SilkBalance.TEDDY_ROAR_RADIUS);
        for (ServerPlayer player : serverLevel.getEntitiesOfClass(ServerPlayer.class, box, boss::valid)) {
            boss.corrupt(player, SilkBalance.TEDDY_ROAR_CORRUPTION);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 10 * 20, 1));
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("SilkOwner", owner);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = tag.hasUUID("SilkOwner") ? tag.getUUID("SilkOwner") : null;
    }
}
