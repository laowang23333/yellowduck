package com.yourname.yellowduck;

import com.yourname.yellowduck.config.EntityTuningConfig;
import com.yourname.yellowduck.cleopatra.*;
import com.yourname.yellowduck.entity.MountEntity;
import com.yourname.yellowduck.entity.AlpacaMountEntity;
import com.yourname.yellowduck.entity.SakurawitchEntity;
import com.yourname.yellowduck.entity.ToyBearEntity;
import com.yourname.yellowduck.entity.TwoPhaseBossEntity;
import com.yourname.yellowduck.network.MountNetwork;
import com.yourname.yellowduck.particle.ModParticles;
import com.yourname.yellowduck.registry.ModEffects;
import com.yourname.yellowduck.registry.ModEntities;
import com.yourname.yellowduck.registry.ModSounds;
import com.yourname.yellowduck.silk.SilkContent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
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
        CleopatraEntities.TYPES.register(bus);
        SilkContent.ENTITY_TYPES.register(bus);
        com.yourname.yellowduck.registry.ModItems.ITEMS.register(bus);
        ModParticles.PARTICLES.register(bus);

        // 注册音效
        ModSounds.SOUND_EVENTS.register(bus);

        // 注册自定义 MobEffect
        ModEffects.EFFECTS.register(bus);
        CleopatraEffects.EFFECTS.register(bus);
        MountNetwork.init();

        // 首次启动后自动生成 config/yellowduck-entities.toml。
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, EntityTuningConfig.SPEC, "yellowduck-entities.toml");

        bus.addListener(this::registerAttributes);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.TWO_PHASE_BOSS.get(), TwoPhaseBossEntity.createAttributes().build());
        event.put(ModEntities.MOUNT.get(), MountEntity.createAttributes().build());
        event.put(ModEntities.ALPACA_MOUNT.get(), AlpacaMountEntity.createAttributes().build());
        event.put(ModEntities.RABBIT_MOUNT.get(), MountEntity.createAttributes().build());
        event.put(ModEntities.BAMBOO_HORSE_MOUNT.get(), MountEntity.createAttributes().build());
        event.put(ModEntities.SAKURA_WITCH.get(), SakurawitchEntity.createAttributes().build());
        event.put(ModEntities.TOY_BEAR.get(), ToyBearEntity.createAttributes().build());
        event.put(CleopatraEntities.BOSS.get(), CleopatraBoss.createAttributes().build());
        event.put(CleopatraEntities.SANDWORM.get(), CleopatraSandworm.createAttributes().build());
        event.put(CleopatraEntities.SCORPION.get(), CleopatraScorpion.createAttributes().build());
        event.put(CleopatraEntities.SNAKE_POISON.get(), CleopatraVenomSnake.createAttributes().build());
        event.put(CleopatraEntities.SNAKE_FIRE.get(), CleopatraVenomSnake.createAttributes().build());
        event.put(CleopatraEntities.SNAKE_ICE.get(), CleopatraVenomSnake.createAttributes().build());
    }
}
