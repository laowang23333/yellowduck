package com.yourname.yellowduck.change;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class GuiHuaNiangEntity extends Entity {
    public GuiHuaNiangEntity(EntityType<? extends GuiHuaNiangEntity> type, Level level){ super(type,level); }
    @Override protected void defineSynchedData(){}

    @Override public void tick(){
        super.tick();
        if(level().isClientSide) return;
        if(tickCount>600){ discard(); return; }

        ChangeBoss boss=level().getEntitiesOfClass(ChangeBoss.class,getBoundingBox().inflate(2.0D),b->b.isAlive())
                .stream().findFirst().orElse(null);
        if(boss!=null){ boss.onBrewDrunk(); discard(); return; }

        Player p=level().getEntitiesOfClass(Player.class,getBoundingBox().inflate(0.8D),
                x->x.isAlive()&&!x.isCreative()&&!x.isSpectator()).stream().findFirst().orElse(null);
        if(p!=null){
            ChangeStatus.removeOneThirst(p);
            ChangeStatus.addDrink(p,1);
            discard();
        }
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag){}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
}
