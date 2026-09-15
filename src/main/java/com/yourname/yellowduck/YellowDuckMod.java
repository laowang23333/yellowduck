package com.yourname.yellowduck;

import com.yourname.yellowduck.entity.MountEntity;
import com.yourname.yellowduck.entity.SakurawitchEntity;
import com.yourname.yellowduck.entity.TwoPhaseBossEntity;
import com.yourname.yellowduck.registry.ModEffects;
import com.yourname.yellowduck.registry.ModEntities;
import com.yourname.yellowduck.registry.ModSounds;
import com.yourname.yellowduck.particle.ModParticles;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(YellowDuckMod.MOD_ID)
public class YellowDuckMod {
    public static final String MOD_ID = "yellowduck";

    public YellowDuckMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        com.yourname.yellowduck.registry.ModBlocks.BLOCKS.register(bus);
        com.yourname.yellowduck.registry.ModBlocks.ITEMS.register(bus);
        com.yourname.yellowduck.registry.ModBlockEntities.BLOCK_ENTITIES.register(bus);
        com.yourname.yellowduck.registry.ModMenuTypes.MENUS.register(bus);
        com.yourname.yellowduck.registry.ModCreativeTabs.CREATIVE_TABS.register(bus);
        ModEntities.ENTITIES.register(bus);
        ModParticles.PARTICLES.register(bus);

        // 注册音效
        ModSounds.SOUND_EVENTS.register(bus);

        // 注册自定义 MobEffect
        ModEffects.EFFECTS.register(bus);

        bus.addListener(this::registerAttributes);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.TWO_PHASE_BOSS.get(), TwoPhaseBossEntity.createAttributes().build());
        event.put(ModEntities.MOUNT.get(), MountEntity.createAttributes().build());
        event.put(ModEntities.SAKURA_WITCH.get(), SakurawitchEntity.createAttributes().build());
    }
}
