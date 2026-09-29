package com.yourname.yellowduck.daji;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/** 妲己技能的短生命周期表现/判定实体。 */
public final class DajiEffectEntity extends Entity {
    public static final int WIND = 0;
    public static final int LIGHTNING = 1;
    public static final int SPIRAL = 2;
    public static final int FIRE = 3;
    public static final int WIND_BURST = 4;
    public static final int SHIELD = 5;
    public static final int TRANSFORM_TS = 6;
    public static final int TRANSFORM_QQ = 7;
    public static final int TRANSFORM_YS = 8;
    public static final int AURA_TS = 9;
    public static final int AURA_QQ = 10;
    public static final int AURA_YS = 11;
    public static final int RELEASE = 12;

    public static final EntityDataAccessor<Integer> VARIANT =
            SynchedEntityData.defineId(DajiEffectEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> LIFE =
            SynchedEntityData.defineId(DajiEffectEntity.class, EntityDataSerializers.INT);

    private UUID ownerId;
    private int age;

    public DajiEffectEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override protected void defineSynchedData() {
        entityData.define(VARIANT, WIND);
        entityData.define(LIFE, 40);
    }

    public int variant(){return entityData.get(VARIANT);}
    public int life(){return entityData.get(LIFE);}
    public int localAge(){return age;}
    public UUID ownerId(){return ownerId;}

    public void setup(int variant, int lifeTicks, UUID owner) {
        entityData.set(VARIANT, variant);
        entityData.set(LIFE, Math.max(1, lifeTicks));
        ownerId = owner;
        if (ownerId != null && level() instanceof ServerLevel server) {
            Entity ownerEntity = server.getEntity(ownerId);
            if (ownerEntity != null && ownerEntity.getPersistentData().hasUUID("YellowDuckDungeon")) {
                getPersistentData().putUUID("YellowDuckDungeon",
                        ownerEntity.getPersistentData().getUUID("YellowDuckDungeon"));
            }
        }
    }

    public static DajiEffectEntity spawn(Level level, int variant, Vec3 pos, int life, UUID owner) {
        DajiEffectEntity fx = DajiContent.EFFECT.get().create(level);
        if (fx == null) return null;
        fx.setup(variant, life, owner);
        fx.setPos(pos.x, pos.y, pos.z);
        level.addFreshEntity(fx);
        return fx;
    }

    @Override public void tick() {
        super.tick();
        age++;
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide) return;

        Entity owner = ownerEntityOrNull();
        if (followsOwner()) {
            if (owner == null || !owner.isAlive()) {
                discard();
                return;
            }
            setPos(owner.getX(), owner.getY(), owner.getZ());
        }

        if (variant() == SHIELD && owner instanceof DajiBoss boss && boss.shield() <= 0.0F) {
            discard();
            return;
        }

        // 风雷引原表现顺序：旋风 -> 闪电 -> 独立爆炸粒子图。
        if (variant() == WIND && age == 2) {
            DajiEffectEntity.spawn(level(), LIGHTNING, position(), 40, ownerId);
        }
        if (variant() == WIND && age == 4) {
            DajiEffectEntity.spawn(level(), WIND_BURST, position(), 8, ownerId);
        }

        // 魔火持续伤害判定仍由同一实体负责；视觉层由客户端读取原技能贴图绘制。
        if (variant() == FIRE) {
            if (age == 1 || (age > 1 && (age - 1) % 40 == 0)) pulseFire();
        }

        if (age >= life()) discard();
    }

    private boolean followsOwner() {
        return switch (variant()) {
            case SPIRAL, SHIELD,
                    TRANSFORM_TS, TRANSFORM_QQ, TRANSFORM_YS,
                    AURA_TS, AURA_QQ, AURA_YS, RELEASE -> true;
            default -> false;
        };
    }

    private void pulseFire() {
        double r = DajiConfig.firePatchRadius;
        AABB box = getBoundingBox().inflate(r, 1.5D, r);
        Entity owner = ownerEntity();
        for (Player p : level().getEntitiesOfClass(Player.class, box,
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator())) {
            if (p.position().distanceToSqr(position()) > r * r) continue;
            float damage = DajiConfig.fireDamage;
            if (owner instanceof DajiBoss boss) damage = boss.getAttackDamageFor(p, damage);
            Vec3 old = p.getDeltaMovement();
            p.hurt(damageSources().indirectMagic(this, owner), damage);
            p.setDeltaMovement(old);
            p.hurtMarked = true;
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    DajiConfig.fireSlowTicks, DajiConfig.slowAmplifier, false, true));
        }
    }

    private Entity ownerEntityOrNull() {
        if (ownerId != null && level() instanceof ServerLevel server) {
            return server.getEntity(ownerId);
        }
        return null;
    }

    private Entity ownerEntity() {
        Entity owner = ownerEntityOrNull();
        return owner != null ? owner : this;
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        age = tag.getInt("Age");
        entityData.set(VARIANT, tag.getInt("Variant"));
        entityData.set(LIFE, Math.max(1, tag.getInt("Life")));
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", age);
        tag.putInt("Variant", variant());
        tag.putInt("Life", life());
        if (ownerId != null) tag.putUUID("Owner", ownerId);
    }

    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
