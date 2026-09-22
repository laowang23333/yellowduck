package com.yourname.yellowduck.silk;

import com.yourname.yellowduck.particle.ModParticles;
import com.yourname.yellowduck.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

/** 奶块 741 黑暗泰迪：拥有独立仇恨；普攻 +5 心智，30 秒一次怒吼 +2 心智并减速。 */
public class SilkDarkTeddy extends PathfinderMob {
    /** 0=无动作，1=普攻，2=怒吼。当前复用 ToyBearAttack 动画表现 1/2。 */
    public static final EntityDataAccessor<Integer> ACTION =
            SynchedEntityData.defineId(SilkDarkTeddy.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> ACTION_SERIAL =
            SynchedEntityData.defineId(SilkDarkTeddy.class, EntityDataSerializers.INT);

    private UUID owner;
    private int nextHit;
    private int nextRoar;
    private int actionUntil;
    private int ownerMissingTicks;

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
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ACTION, 0);
        entityData.define(ACTION_SERIAL, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.05D, true));

        // 741 使用自己的仇恨：被谁打就优先反击；平时自己寻找场内玩家。
        targetSelector.addGoal(0, new HurtByTargetGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(
                this, Player.class, 10, true, false,
                living -> living instanceof ServerPlayer player && isValidOwnTarget(player)));
    }

    public void setOwner(SilkBoss boss) {
        owner = boss.getUUID();
        ownerMissingTicks = 0;
        SilkConfig.reapply(this);
    }

    private SilkBoss ownerBossRaw() {
        if (owner == null || !(level() instanceof ServerLevel serverLevel)) return null;
        Entity entity = serverLevel.getEntity(owner);
        return entity instanceof SilkBoss boss ? boss : null;
    }

    private boolean isValidOwnTarget(ServerPlayer player) {
        SilkBoss boss = ownerBossRaw();
        return boss != null && boss.isAlive() && boss.isEncounterActive() && boss.valid(player);
    }

    private void triggerAction(int action, int durationTicks) {
        entityData.set(ACTION, action);
        entityData.set(ACTION_SERIAL, entityData.get(ACTION_SERIAL) + 1);
        actionUntil = tickCount + Math.max(1, durationTicks);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        SilkBoss boss = ownerBossRaw();
        if (boss == null) {
            // 区块重载时允许 Boss 稍晚加载，避免同 tick 误删；找不到 5 秒才清理。
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

        // 不再用教授最高仇恨覆盖泰迪自身 target。
        if (getTarget() instanceof ServerPlayer player) {
            if (!boss.valid(player)) {
                setTarget(null);
                getNavigation().stop();
            }
        } else if (getTarget() != null) {
            setTarget(null);
        }

        if (entityData.get(ACTION) != 0 && tickCount >= actionUntil) {
            entityData.set(ACTION, 0);
        }

        if (nextRoar == 0) nextRoar = tickCount + SilkBalance.TEDDY_ROAR_COOLDOWN;
        if (tickCount >= nextRoar) {
            roar(boss);
            nextRoar = tickCount + SilkBalance.TEDDY_ROAR_COOLDOWN;
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!(target instanceof ServerPlayer player) || tickCount < nextHit) return false;
        SilkBoss boss = ownerBossRaw();
        if (boss == null || !boss.isAlive() || !boss.isEncounterActive() || !boss.valid(player)) return false;

        nextHit = tickCount + SilkBalance.TEDDY_HIT_COOLDOWN; // 7411 默认 2 秒。
        triggerAction(1, 14);
        boolean hit = boss.hit(player, SilkBalance.TEDDY_DAMAGE, SilkBalance.TEDDY_HIT_CORRUPTION);
        if (hit && level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, blockPosition(), ModSounds.SILK_BEAR_HURT.get(), SoundSource.HOSTILE, 1.2F, 1.0F);
        }
        return hit;
    }

    private void roar(SilkBoss boss) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        triggerAction(2, 24);
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
        tag.putInt("SilkNextHit", Math.max(0, nextHit - tickCount));
        tag.putInt("SilkNextRoar", Math.max(0, nextRoar - tickCount));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = tag.hasUUID("SilkOwner") ? tag.getUUID("SilkOwner") : null;
        nextHit = tickCount + Math.max(0, tag.getInt("SilkNextHit"));
        nextRoar = tickCount + Math.max(0, tag.getInt("SilkNextRoar"));
        entityData.set(ACTION, 0);
    }
}
