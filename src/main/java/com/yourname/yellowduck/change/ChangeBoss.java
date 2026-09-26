package com.yourname.yellowduck.change;

import com.yourname.yellowduck.boss.NetcraftBossBase;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class ChangeBoss extends NetcraftBossBase {
    public static final int IDLE=0, WALK=1, ATTACK=2, SKILL=3, SUMMON=4, RAGE=5;
    private static final EntityDataAccessor<Integer> ACTION =
            SynchedEntityData.defineId(ChangeBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION_SERIAL =
            SynchedEntityData.defineId(ChangeBoss.class, EntityDataSerializers.INT);

    private int actionTicks;
    private int meleeCooldown;
    private int meleeHitTimer;
    private UUID meleeTarget;
    private int drinkCooldown = 100;
    private int thirstCooldown = 400;
    private int rabbitTimer = 200;
    private boolean rabbit50;
    private boolean rage80;
    private boolean rage50;
    private boolean raging;

    public ChangeBoss(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setBaseTier(4);
        setBaseDamage(120);
        setBaseDefense(60);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 100000.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 120.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ACTION, IDLE);
        entityData.define(ACTION_SERIAL, 0);
    }

    public int action() { return entityData.get(ACTION); }
    public int actionSerial() { return entityData.get(ACTION_SERIAL); }
    public boolean isRaging() { return raging; }

    private void play(int id, int ticks) {
        entityData.set(ACTION, id);
        entityData.set(ACTION_SERIAL, entityData.get(ACTION_SERIAL) + 1);
        actionTicks = ticks;
    }

    @Override public int getMeleeDefense() { return 60; }
    @Override public int getRangedDefense() { return 10; }
    @Override public int getMagicDefense() { return 10; }
    @Override public float getDamageReductionRatio() { return 0.70F; }
    @Override public boolean shouldIgnoreSpawnDistanceLimit() { return true; }
    @Override public double getDetectionRadius() { return 10.0D; }
    @Override public double getDetectionHatred() { return 4.0D; }
    @Override public double getHatredClearRadius() { return 40.0D; }
    @Override public boolean shouldDisengageOnDistance() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public void push(net.minecraft.world.entity.Entity e) {}
    @Override public void push(double x, double y, double z) {}
    @Override public boolean isPlayingAttackAnimation() { return action()==ATTACK || action()==SKILL || action()==SUMMON || action()==RAGE; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !isAlive()) return;

        if (actionTicks > 0 && --actionTicks == 0 && !raging) entityData.set(ACTION, IDLE);
        if (meleeCooldown > 0) meleeCooldown--;
        if (drinkCooldown > 0) drinkCooldown--;
        if (thirstCooldown > 0) thirstCooldown--;
        if (rabbitTimer > 0) rabbitTimer--;

        tickPendingHit();
        checkRage();

        if (raging) {
            getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
            if (tickCount % 20 == 0) rageWave();
            return;
        }

        Player target = getHatredManager().getCurrentTarget();
        if (target == null) target = nearest(10.0D);
        if (target != null) chase(target);
        else getNavigation().stop();

        tickSkills();

        if (rabbitTimer <= 0) {
            rabbitTimer = 200;
            spawnRabbitAxis();
        }
    }

    private void chase(Player target) {
        double d2 = distanceToSqr(target);
        if (d2 > 9.0D) {
            getNavigation().moveTo(target, 1.0D);
            if (actionTicks <= 0) entityData.set(ACTION, WALK);
        } else {
            getNavigation().stop();
            if (meleeCooldown <= 0 && meleeHitTimer <= 0) {
                faceTargetForAttack(target);
                meleeCooldown = 40;
                meleeHitTimer = 15;
                meleeTarget = target.getUUID();
                play(ATTACK, 22);
            }
        }
    }

    private void tickPendingHit() {
        if (meleeHitTimer <= 0) return;
        if (--meleeHitTimer != 0 || meleeTarget == null) return;
        Player p = level().getPlayerByUUID(meleeTarget);
        meleeTarget = null;
        if (p == null || !p.isAlive() || distanceToSqr(p) > 25.0D) return;
        float damage = 120.0F * (1.0F + ChangeStatus.boost(this) * 0.10F);
        hurtWithoutKnockback(p, damageSources().mobAttack(this), damage);
    }

    private void tickSkills() {
        float ratio = getHealth() / getMaxHealth();

        if (!rabbit50 && ratio <= 0.50F) {
            rabbit50 = true;
            summonRabbit(position().add(2.0D, 0.0D, 0.0D));
            play(SUMMON, 25);
        }

        if (drinkCooldown <= 0) {
            Player p = randomNearestThree();
            if (p != null) {
                drinkCooldown = 100;
                ChangeStatus.addDrink(p, 1);
            }
        }

        if (ratio < 0.80F && thirstCooldown <= 0) {
            Player p = randomNearestThree();
            if (p != null) {
                thirstCooldown = 400;
                ChangeStatus.addThirst(p, 1);
                play(SKILL, 30);
                level().playSound(null, blockPosition(), ChangeContent.SKILL.get(), SoundSource.HOSTILE, 1.5F, 1.0F);
            }
        }
    }

    private void checkRage() {
        float ratio = getHealth() / getMaxHealth();
        if (!rage80 && ratio <= 0.80F) {
            rage80 = true;
            enterRage();
        } else if (!rage50 && ratio <= 0.50F) {
            rage50 = true;
            enterRage();
        }
    }

    private void enterRage() {
        raging = true;
        getNavigation().stop();
        play(RAGE, Integer.MAX_VALUE / 4);
    }

    private void rageWave() {
        List<Player> players = level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(40.0D),
                p -> isValidHatredPlayer(p));
        for (Player p : players) {
            hurtWithoutKnockback(p, damageSources().magic(), 50.0F);
            if (distanceToSqr(p) <= 9.0D) hurtWithoutKnockback(p, damageSources().magic(), 50.0F);
        }
        if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
            for (int i=0;i<120;i++) {
                double a = random.nextDouble() * Math.PI * 2.0D;
                double r = 2.0D + random.nextDouble() * 38.0D;
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                        getX()+Math.cos(a)*r, getY()+0.15D, getZ()+Math.sin(a)*r,
                        1, 0, 0.02D, 0, 0);
            }
        }
    }

    public void onBrewDrunk() {
        ChangeStatus.addBoost(this);
        if (raging) {
            raging = false;
            actionTicks = 0;
            entityData.set(ACTION, IDLE);
            spawnClone();
        }
    }

    private void spawnClone() {
        ChangeClone clone = ChangeContent.CLONE.get().create(level());
        if (clone == null) return;
        Vec3 center = getSpawnPosition() == null ? position() : getSpawnPosition();
        Player p = nearest(40.0D);
        Vec3 side = new Vec3(1,0,0);
        if (p != null) {
            double dx=p.getX()-center.x, dz=p.getZ()-center.z;
            double len=Math.sqrt(dx*dx+dz*dz);
            if (len>0.0001D) side=new Vec3(-dz/len,0,dx/len);
        }
        boolean flip=random.nextBoolean();
        Vec3 me=center.add(side.scale(flip?2.5D:-2.5D));
        Vec3 other=center.add(side.scale(flip?-2.5D:2.5D));
        moveTo(me.x, me.y, me.z, getYRot(), getXRot());
        clone.moveTo(other.x, other.y, other.z, getYRot(), 0);
        level().addFreshEntity(clone);
    }

    private void spawnRabbitAxis() {
        Vec3 c = getSpawnPosition() == null ? position() : getSpawnPosition();
        int axis = random.nextInt(4);
        Vec3 p = switch(axis) {
            case 0 -> c.add(15,0,0);
            case 1 -> c.add(-15,0,0);
            case 2 -> c.add(0,0,15);
            default -> c.add(0,0,-15);
        };
        summonRabbit(p);
    }

    private void summonRabbit(Vec3 p) {
        ChangeRabbit rabbit = ChangeContent.RABBIT.get().create(level());
        if (rabbit == null) return;
        rabbit.setOwner(this);
        rabbit.moveTo(p.x, p.y, p.z, getYRot(), 0);
        level().addFreshEntity(rabbit);
    }

    private Player randomNearestThree() {
        List<Player> list = players(10.0D);
        list.sort(Comparator.comparingDouble(this::distanceToSqr));
        if (list.size() > 3) list = new ArrayList<>(list.subList(0,3));
        return list.isEmpty() ? null : list.get(random.nextInt(list.size()));
    }

    private Player nearest(double radius) {
        return players(radius).stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
    }

    private List<Player> players(double radius) {
        return level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(radius), this::isValidHatredPlayer);
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        if (!level().isClientSide) level().playSound(null, blockPosition(), ChangeContent.DEATH.get(), SoundSource.HOSTILE, 1.5F, 1.0F);
        super.die(source);
    }
}
