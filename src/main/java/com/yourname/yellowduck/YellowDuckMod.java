package com.yourname.yellowduck;

import com.yourname.yellowduck.entity.MountEntity;
import com.yourname.yellowduck.entity.TwoPhaseBossEntity;
import com.yourname.yellowduck.registry.ModEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(YellowDuckMod.MOD_ID)
public class YellowDuckMod {
    public static final String MOD_ID = "yellowduck";

    public YellowDuckMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITIES.register(bus);
        bus.addListener(this::registerAttributes);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.TWO_PHASE_BOSS.get(), TwoPhaseBossEntity.createAttributes().build());
        event.put(ModEntities.MOUNT.get(), MountEntity.createAttributes().build());
    }
}
