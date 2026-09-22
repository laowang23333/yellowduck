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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.UUID;

/**
 * 奶块 742 黑暗史莱姆（v1.5）：
 * - 使用自己的目标，不读取教授仇恨；
 * - 与助战火圈同时出现，只有进入火圈才会被安全清除；
 * - 出生 25 秒仍未清除会自爆；
 * - 被玩家直接打死也会自爆；
 * - 每只自爆对周围 30 格玩家造成固定 30 点伤害，并附加 +5 心智和黑暗沸血。
 */
public class SilkDarkSlime extends Slime {
    private UUID owner;
    private boolean detonating;
    private boolean safelyCleared;
    private int nextAttack;
    private int explodeAt;
    private int ownerMissingTicks;

    public SilkDarkSlime(EntityType<? extends SilkDarkSlime> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCustomName(Component.literal("黑暗史莱姆"));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, SilkBalance.SLIME_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, SilkBalance.SLIME_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, SilkBalance.SLIME_MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, SilkBalance.SLIME_FOLLOW_RANGE);
    }

    public void setOwner(SilkBoss boss) {
        owner = boss.getUUID();
        ownerMissingTicks = 0;
        setSize(2, true);
        var maxHealth = getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(SilkBalance.SLIME_HEALTH);
        setHealth((float) SilkBalance.SLIME_HEALTH);
        SilkConfig.reapply(this);
        explodeAt = tickCount + Math.max(1, SilkBalance.SLIME_AUTO_EXPLODE_TICKS);
    }

    public boolean isOwnedBy(SilkBoss boss) {
        return boss != null && owner != null && owner.equals(boss.getUUID()) && isAlive();
    }

    public int secondsUntilExplosion() {
        if (safelyCleared || detonating || explodeAt <= 0) return 0;
        return Math.max(0, (explodeAt - tickCount + 19) / 20);
    }

    private SilkBoss ownerBossRaw() {
        if (owner == null || !(level() instanceof ServerLevel serverLevel)) return null;
        Entity entity = serverLevel.getEntity(owner);
        return entity instanceof SilkBoss boss ? boss : null;
    }

    private ServerPlayer ownTarget(SilkBoss boss, ServerLevel serverLevel) {
        if (getTarget() instanceof ServerPlayer current && boss.valid(current)) return current;

        // 被谁打优先追谁；否则自己寻找最近的场内玩家。完全不读取教授仇恨。
        if (getLastHurtByMob() instanceof ServerPlayer attacker && boss.valid(attacker)) {
            setTarget(attacker);
            return attacker;
        }

        double range = Math.max(4.0D, getAttributeValue(Attributes.FOLLOW_RANGE));
        ServerPlayer nearest = serverLevel.getEntitiesOfClass(
                        ServerPlayer.class,
                        getBoundingBox().inflate(range),
                        boss::valid)
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
        setTarget(nearest);
        return nearest;
    }

    /**
     * 火圈的清除是唯一“安全死亡”：不触发 30 格自爆。
     */
    public void clearByFireCircle(SilkBoss boss) {
        if (safelyCleared || detonating || !isOwnedBy(boss)) return;
        safelyCleared = true;
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.SILK_FIRE.get(), getX(), getY() + 0.6D, getZ(),
                    55, 0.8D, 0.7D, 0.8D, 0.055D);
            serverLevel.sendParticles(ModParticles.SILK_SMOKE.get(), getX(), getY() + 0.5D, getZ(),
                    32, 0.7D, 0.5D, 0.7D, 0.04D);
        }
        discard();
    }

    /**
     * 禁用原版 Slime 的贴身碰撞伤害；7421 的伤害统一由下方 2 秒技能循环结算，
     * 避免 vanilla 碰撞伤害与斯尔克技能伤害叠两次。
     */
    @Override
    public void playerTouch(Player player) {
        // no-op
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (safelyCleared || detonating || amount <= 0.0F) return false;

        // 玩家/其它伤害直接把史莱姆打到 0 血时，不走普通死亡，立刻按 7422 自爆。
        if (!level().isClientSide && amount >= getHealth()) {
            SilkBoss boss = ownerBossRaw();
            if (boss != null && boss.isAlive() && boss.isEncounterActive()) {
                detonate(boss);
                return true;
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || safelyCleared || detonating) return;
        if (!(level() instanceof ServerLevel serverLevel)) return;

        SilkBoss boss = ownerBossRaw();
        if (boss == null) {
            if (++ownerMissingTicks > 100) discard();
            return;
        }
        if (!boss.isAlive()) {
            discard();
            return;
        }
        if (!boss.isEncounterActive()) {
            if (++ownerMissingTicks > 100) discard();
            return;
        }
        ownerMissingTicks = 0;

        if (explodeAt <= 0) {
            explodeAt = tickCount + Math.max(1, SilkBalance.SLIME_AUTO_EXPLODE_TICKS);
        }
        if (tickCount >= explodeAt) {
            detonate(boss);
            return;
        }

        ServerPlayer target = ownTarget(boss, serverLevel);
        if (target == null) return;

        if (distanceToSqr(target) <= 2.5D * 2.5D && tickCount >= nextAttack) {
            nextAttack = tickCount + SilkBalance.SLIME_ATTACK_COOLDOWN; // 7421 默认 2 秒。
            boss.hit(target, SilkBalance.SLIME_DAMAGE, SilkBalance.SLIME_HIT_CORRUPTION);
        }
    }

    /**
     * 7422：每一只史莱姆都单独结算一次 30 格 / 30 固定伤害。
     * 不经过 boss.hit()，避免 P3 倍率把 30 点再次放大。
     */
    private void detonate(SilkBoss boss) {
        if (detonating || safelyCleared || !(level() instanceof ServerLevel serverLevel)) return;
        detonating = true;

        serverLevel.sendParticles(ModParticles.SILK_DARK_FIRE.get(), getX(), getY() + 0.6D, getZ(),
                110, 2.0D, 1.2D, 2.0D, 0.10D);
        serverLevel.sendParticles(ModParticles.SILK_SMOKE.get(), getX(), getY() + 0.6D, getZ(),
                80, 2.3D, 1.0D, 2.3D, 0.07D);

        double radius = SilkBalance.SLIME_EXPLOSION_RADIUS;
        AABB box = getBoundingBox().inflate(radius);
        double radiusSq = radius * radius;
        for (ServerPlayer player : serverLevel.getEntitiesOfClass(
                ServerPlayer.class, box,
                p -> boss.valid(p) && p.distanceToSqr(this) <= radiusSq)) {
            /*
             * 用户指定“固定 30”：不走护甲/P3倍率，也不被普通受伤减免改成别的数值。
             * 每只史莱姆独立扣一次当前生命，所以多只同时爆炸会逐只叠加。
             */
            float fixed = Math.max(0.0F, SilkBalance.SLIME_EXPLOSION_DAMAGE);
            float after = player.getHealth() - fixed;
            if (after <= 0.0F) {
                player.setHealth(0.0F);
                player.kill();
            } else {
                player.setHealth(after);
                player.hurtMarked = true;
            }
            boss.corrupt(player, SilkBalance.SLIME_EXPLODE_CORRUPTION);
            SilkCombatEvents.addBoilingBlood(player, SilkBalance.BOILING_BLOOD_TICKS);
        }

        discard();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("SilkOwner", owner);
        tag.putInt("SilkNextAttack", Math.max(0, nextAttack - tickCount));
        tag.putInt("SilkExplodeRemaining", Math.max(1, explodeAt - tickCount));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = tag.hasUUID("SilkOwner") ? tag.getUUID("SilkOwner") : null;
        nextAttack = tickCount + Math.max(0, tag.getInt("SilkNextAttack"));
        int remaining = Math.max(1, tag.getInt("SilkExplodeRemaining"));
        explodeAt = tickCount + remaining;
        safelyCleared = false;
        detonating = false;
    }
}
