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

import java.util.Comparator;
import java.util.UUID;

/** 奶块 742 黑暗史莱姆：独立选人；初始无敌；火雨解除无敌；近身普通攻击并可靠触发自爆。 */
public class SilkDarkSlime extends Slime {
    private UUID owner;
    private boolean shielded = true;
    private boolean detonating;
    private int nextAttack;
    private int closeActionCount;
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
        // setSize 是受保护方法，子类可直接调用。
        setSize(2, true);
        var maxHealth = getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(SilkBalance.SLIME_HEALTH);
        setHealth((float) SilkBalance.SLIME_HEALTH);
        SilkConfig.reapply(this);
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

    private SilkBoss ownerBossRaw() {
        if (owner == null || !(level() instanceof ServerLevel serverLevel)) return null;
        Entity entity = serverLevel.getEntity(owner);
        return entity instanceof SilkBoss boss ? boss : null;
    }

    private ServerPlayer ownTarget(SilkBoss boss, ServerLevel serverLevel) {
        if (getTarget() instanceof ServerPlayer current && boss.valid(current)) return current;

        // 先优先攻击最近打过史莱姆的玩家，再自己找最近玩家；完全不读取教授仇恨表。
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

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!detonating && shielded) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
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
        // Boss 与召唤物的区块加载 tick 顺序不固定；给未进入战斗状态 5 秒宽限。
        if (!boss.isEncounterActive()) {
            if (++ownerMissingTicks > 100) discard();
            return;
        }
        ownerMissingTicks = 0;

        ServerPlayer target = ownTarget(boss, serverLevel);
        if (target == null) return;

        if (distanceToSqr(target) <= 2.5D * 2.5D && tickCount >= nextAttack) {
            nextAttack = tickCount + SilkBalance.SLIME_ATTACK_COOLDOWN; // 7421/7422 默认 2 秒。

            /*
             * 客户端资源只证明 7421/7422 都存在，没有暴露服务器 AI 的选择条件。
             * 旧版随机 35% 会出现长时间完全不自爆。这里改为稳定节奏：
             * 前两次近身使用 7421，第三次近身使用 7422 自爆，然后实体结束。
             */
            if (closeActionCount >= 2) {
                detonate(boss);
            } else {
                closeActionCount++;
                boss.hit(target, SilkBalance.SLIME_DAMAGE, SilkBalance.SLIME_HIT_CORRUPTION);
            }
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
            SilkCombatEvents.addBoilingBlood(player, SilkBalance.BOILING_BLOOD_TICKS);
        }
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("SilkOwner", owner);
        tag.putBoolean("SilkShielded", shielded);
        tag.putInt("SilkNextAttack", Math.max(0, nextAttack - tickCount));
        tag.putInt("SilkCloseActions", closeActionCount);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = tag.hasUUID("SilkOwner") ? tag.getUUID("SilkOwner") : null;
        shielded = tag.getBoolean("SilkShielded");
        nextAttack = tickCount + Math.max(0, tag.getInt("SilkNextAttack"));
        closeActionCount = Math.max(0, tag.getInt("SilkCloseActions"));
    }
}
