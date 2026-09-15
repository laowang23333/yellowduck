package com.yourname.yellowduck;

import com.yourname.yellowduck.entity.MountEntity;
import com.yourname.yellowduck.entity.SakurawitchEntity;
import com.yourname.yellowduck.entity.TwoPhaseBossEntity;
import com.yourname.yellowduck.particle.ModParticles;
import com.yourname.yellowduck.registry.ModEffects;
import com.yourname.yellowduck.registry.ModEntities;
import com.yourname.yellowduck.registry.ModSounds;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(YellowDuckMod.MOD_ID)
public class YellowDuckMod {

    public static final String MOD_ID = "yellowduck";

    public YellowDuckMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        // 方块
        com.yourname.yellowduck.registry.ModBlocks.BLOCKS.register(bus);

        // 方块物品
        com.yourname.yellowduck.registry.ModBlocks.ITEMS.register(bus);

        // 方块实体
        com.yourname.yellowduck.registry.ModBlockEntities.BLOCK_ENTITIES.register(bus);

        // 菜单
        com.yourname.yellowduck.registry.ModMenuTypes.MENUS.register(bus);

        // 创造模式物品栏
        com.yourname.yellowduck.registry.ModCreativeTabs.CREATIVE_TABS.register(bus);

        // 实体
        ModEntities.ENTITIES.register(bus);

        // 音效
        ModSounds.SOUND_EVENTS.register(bus);

        // 自定义 MobEffect
        ModEffects.EFFECTS.register(bus);

        // 自定义粒子
        ModParticles.PARTICLES.register(bus);

        // 实体属性
        bus.addListener(this::registerAttributes);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(
                ModEntities.TWO_PHASE_BOSS.get(),
                TwoPhaseBossEntity.createAttributes().build()
        );

        event.put(
                ModEntities.MOUNT.get(),
                MountEntity.createAttributes().build()
        );

        event.put(
                ModEntities.SAKURA_WITCH.get(),
                SakurawitchEntity.createAttributes().build()
        );
    }
}
