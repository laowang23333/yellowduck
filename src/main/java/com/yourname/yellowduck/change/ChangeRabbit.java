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

import java.util.UUID;

public final class ChangeRabbit extends NetcraftBossBase {
    private static final EntityDataAccessor<Integer> ACTION =
            SynchedEntityData.defineId(ChangeRabbit.class, EntityDataSerializers.INT);
    private UUID owner;
    private int cooldown;

    public ChangeRabbit(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setBaseTier(4);
        setBaseDamage(50);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH,500)
                .add(Attributes.MOVEMENT_SPEED,0.30D)
                .add(Attributes.ATTACK_DAMAGE,50)
                .add(Attributes.FOLLOW_RANGE,20)
                .add(Attributes.KNOCKBACK_RESISTANCE,1);
    }

    @Override protected void defineSynchedData(){ super.defineSynchedData(); entityData.define(ACTION,0); }
    public int action(){ return entityData.get(ACTION); }
    public void setOwner(ChangeBoss boss){ owner=boss.getUUID(); }
    @Override public int getMeleeDefense(){ return 10; }
    @Override public int getRangedDefense(){ return 10; }
    @Override public int getMagicDefense(){ return 10; }
    @Override public boolean shouldIgnoreSpawnDistanceLimit(){ return true; }
    @Override public boolean shouldDisengageOnDistance(){ return false; }
    @Override public boolean isPushable(){ return false; }

    @Override public void tick(){
        super.tick();
        if(level().isClientSide || !isAlive()) return;
        if(cooldown>0) cooldown--;
        Player p=level().getNearestPlayer(this,10);
        if(p!=null && !p.isCreative() && !p.isSpectator()){
            if(distanceToSqr(p)>2.2D*2.2D){ getNavigation().moveTo(p,1.0D); entityData.set(ACTION,1); }
            else if(cooldown<=0){ cooldown=40; faceTargetForAttack(p); hurtWithoutKnockback(p,damageSources().mobAttack(this),50F); entityData.set(ACTION,2); }
            return;
        }
        ChangeBoss boss=owner==null?null:(ChangeBoss)((net.minecraft.server.level.ServerLevel)level()).getEntity(owner);
        if(boss!=null && boss.isAlive()){
            if(distanceToSqr(boss)<=1.6D*1.6D){ dropBrew(); discard(); }
            else { getNavigation().moveTo(boss,1.0D); entityData.set(ACTION,1); }
        }
    }

    private void dropBrew(){
        GuiHuaNiangEntity brew=ChangeContent.BREW.get().create(level());
        if(brew!=null){ brew.moveTo(getX(),getY(),getZ(),0,0); level().addFreshEntity(brew); }
    }

    @Override public void die(net.minecraft.world.damagesource.DamageSource source){ if(!level().isClientSide) dropBrew(); super.die(source); }
}
