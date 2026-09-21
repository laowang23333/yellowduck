package com.yourname.yellowduck.silk;

import com.yourname.yellowduck.particle.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

/** 奶块 742 黑暗史莱姆：初始无敌；助战火雨解除无敌；近身自爆叠 5 心智并附带黑暗沸血。 */
public class SilkDarkSlime extends Slime {
    private UUID owner;
    private boolean shielded = true;
    private boolean detonating;
    private int nextAttack;

    public SilkDarkSlime(EntityType<? extends SilkDarkSlime> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCustomName(Component.literal("黑暗史莱姆"));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.ATTACK_DAMAGE, SilkBalance.SLIME_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, 0.24D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    public void setOwner(SilkBoss boss) {
        owner = boss.getUUID();
        // setSize 是受保护方法，子类可直接调用。
        setSize(2, true);
        var maxHealth = getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(300.0D);
        setHealth(300.0F);
    }

    public boolean isShielded() {
        return shielded;
    }

    public void breakShield() {
        if (!shielded) return;
        shielded = false;
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.SILK_FIRE.get(), getX(), getY() + 0.5D, getZ(),
                    30, 0.7D, 0.6D, 0.7D, 0.04D);
        }
    }

    private SilkBoss boss() {
        if (owner == null || !(level() instanceof ServerLevel serverLevel)) return null;
        Entity entity = serverLevel.getEntity(owner);
        return entity instanceof SilkBoss boss && boss.isAlive() ? boss : null;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!detonating && shielded) return false;
        return super.hurt(source, amount);
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
        if (target == null) return;
        setTarget(target);

        if (distanceToSqr(target) <= 2.5D * 2.5D && tickCount >= nextAttack) {
            nextAttack = tickCount + 40; // 7421/7422 都是 2 秒内部 CD。
            if (random.nextFloat() < 0.35F) detonate(boss);
            else boss.hit(target, SilkBalance.SLIME_DAMAGE, SilkBalance.SLIME_HIT_CORRUPTION);
        }
    }

    private void detonate(SilkBoss boss) {
        if (detonating || !(level() instanceof ServerLevel serverLevel)) return;
        detonating = true;
        serverLevel.sendParticles(ModParticles.SILK_DARK_FIRE.get(), getX(), getY() + 0.5D, getZ(),
                65, 1.2D, 0.8D, 1.2D, 0.08D);
        AABB box = getBoundingBox().inflate(3.0D);
        for (ServerPlayer player : serverLevel.getEntitiesOfClass(ServerPlayer.class, box, boss::valid)) {
            boss.hit(player, SilkBalance.SLIME_DAMAGE, SilkBalance.SLIME_EXPLODE_CORRUPTION);
            SilkCombatEvents.addBoilingBlood(player, 10 * 20);
        }
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("SilkOwner", owner);
        tag.putBoolean("SilkShielded", shielded);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = tag.hasUUID("SilkOwner") ? tag.getUUID("SilkOwner") : null;
        shielded = tag.getBoolean("SilkShielded");
    }
}
