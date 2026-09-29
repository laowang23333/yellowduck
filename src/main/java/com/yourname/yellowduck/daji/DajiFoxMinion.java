package com.yourname.yellowduck.daji;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** 妲己召唤的小狐、青狐精英、白狐精英。 */
public final class DajiFoxMinion extends Monster {
    public static final int SMALL = 0, BLUE = 1, WHITE = 2;
    public static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(DajiFoxMinion.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> ATTACK_SERIAL = SynchedEntityData.defineId(DajiFoxMinion.class, EntityDataSerializers.INT);

    private UUID ownerId;
    private boolean initialized;
    private double appliedHealth = -1.0D;
    private double appliedAttack = -1.0D;

    public DajiFoxMinion(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 0;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 500.0D)
                .add(Attributes.ATTACK_DAMAGE, 60.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D);
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(VARIANT, SMALL);
        entityData.define(ATTACK_SERIAL, 0);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15D, true));
        goalSelector.addGoal(7, new RandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true,
                p -> p instanceof Player player && !player.isCreative() && !player.isSpectator()));
    }

    public int variant() { return entityData.get(VARIANT); }
    public UUID ownerId() { return ownerId; }

    public void setup(int variant, UUID owner) {
        entityData.set(VARIANT, Math.max(SMALL, Math.min(WHITE, variant)));
        ownerId = owner;
        initialized = false;
        if (ownerId != null && level() instanceof ServerLevel server) {
            Entity ownerEntity = server.getEntity(ownerId);
            if (ownerEntity != null && ownerEntity.getPersistentData().hasUUID("YellowDuckDungeon")) {
                getPersistentData().putUUID("YellowDuckDungeon",
                        ownerEntity.getPersistentData().getUUID("YellowDuckDungeon"));
            }
        }
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) return;

        DajiConfig.reloadIfChanged();
        if (!initialized || tickCount % 20 == 0) applyConfig(!initialized);
        if (ownerDead()) discard();
    }

    private void applyConfig(boolean first) {
        initialized = true;
        double hp = variant() == SMALL ? DajiConfig.smallFoxHealth : DajiConfig.eliteFoxHealth;
        double attack = variant() == SMALL ? DajiConfig.smallFoxAttack : DajiConfig.eliteFoxAttack;
        float ratio = getMaxHealth() > 0.0F ? getHealth() / getMaxHealth() : 1.0F;

        if (appliedHealth != hp && getAttribute(Attributes.MAX_HEALTH) != null) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
            appliedHealth = hp;
            setHealth(first ? getMaxHealth() : Math.max(0.1F, Math.min(getMaxHealth(), getMaxHealth() * ratio)));
        }
        if (appliedAttack != attack && getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(attack);
            appliedAttack = attack;
        }
        if (first) {
            setCustomName(Component.literal(variant() == SMALL ? "小狐" : variant() == BLUE ? "青狐精英" : "白狐精英"));
        }
    }

    private boolean ownerDead() {
        if (ownerId == null || !(level() instanceof ServerLevel server)) return false;
        Entity e = server.getEntity(ownerId);
        return e == null || !e.isAlive();
    }

    @Override public boolean doHurtTarget(Entity entity) {
        if (!(entity instanceof LivingEntity target) || !target.isAlive()) return false;
        entityData.set(ATTACK_SERIAL, entityData.get(ATTACK_SERIAL) + 1);
        Vec3 motion = target.getDeltaMovement();
        boolean hit = target.hurt(damageSources().mobAttack(this), (float)getAttributeValue(Attributes.ATTACK_DAMAGE));
        if (hit) {
            target.setDeltaMovement(motion);
            target.hurtMarked = true;
            // 白狐之力：客户端表明确为 1.5 秒承伤 +30%。
            if (variant() == WHITE) {
                target.addEffect(new MobEffectInstance(DajiContent.VULNERABILITY.get(), 30, 0, false, true));
            }
        }
        return hit;
    }

    @Override public boolean isPushable() { return false; }
    @Override public void push(Entity e) {}
    @Override public void push(double x,double y,double z) {}

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("DajiVariant", variant());
        if (ownerId != null) tag.putUUID("DajiOwner", ownerId);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(VARIANT, Math.max(SMALL, Math.min(WHITE, tag.getInt("DajiVariant"))));
        if (tag.hasUUID("DajiOwner")) ownerId = tag.getUUID("DajiOwner");
        initialized = false;
    }
}
