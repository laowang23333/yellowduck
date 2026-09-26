package com.yourname.yellowduck.change;

import com.yourname.yellowduck.boss.NetcraftBossBase;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class ChangeClone extends NetcraftBossBase {
    private static final EntityDataAccessor<Boolean> ATTACKING =
            SynchedEntityData.defineId(ChangeClone.class, EntityDataSerializers.BOOLEAN);
    private int cooldown, hitTimer;
    private java.util.UUID hitTarget;

    public ChangeClone(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setBaseTier(4);
        setBaseDamage(120);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 50000.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 120.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(ATTACKING,false); }
    public boolean attacking(){ return entityData.get(ATTACKING); }
    @Override public boolean shouldIgnoreSpawnDistanceLimit(){ return true; }
    @Override public double getDetectionRadius(){ return 10.0D; }
    @Override public double getDetectionHatred(){ return 4.0D; }
    @Override public boolean shouldDisengageOnDistance(){ return false; }
    @Override public boolean isPushable(){ return false; }
    @Override public void push(net.minecraft.world.entity.Entity e){}
    @Override public void push(double x,double y,double z){}
    @Override public boolean isPlayingAttackAnimation(){ return attacking(); }

    @Override public void tick(){
        super.tick();
        if(level().isClientSide || !isAlive()) return;
        if(cooldown>0) cooldown--;
        if(hitTimer>0 && --hitTimer==0){
            Player p=level().getPlayerByUUID(hitTarget);
            if(p!=null && p.isAlive() && distanceToSqr(p)<=25) hurtWithoutKnockback(p,damageSources().mobAttack(this),120F);
            entityData.set(ATTACKING,false);
        }
        Player p=getHatredManager().getCurrentTarget();
        if(p==null) p=level().getNearestPlayer(this,10);
        if(p==null || p.isCreative() || p.isSpectator()) return;
        if(distanceToSqr(p)>9) getNavigation().moveTo(p,1.0D);
        else if(cooldown<=0 && hitTimer<=0){
            getNavigation().stop(); faceTargetForAttack(p); cooldown=40; hitTimer=15; hitTarget=p.getUUID(); entityData.set(ATTACKING,true);
        }
    }
}
