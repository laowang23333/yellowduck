package com.yourname.yellowduck.registry;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(
                    ForgeRegistries.SOUND_EVENTS,
                    YellowDuckMod.MOD_ID
            );

    // 鸡哥音效
    public static final RegistryObject<SoundEvent> JIJIJI =
            register("jijiji");

    public static final RegistryObject<SoundEvent> NIGANMA =
            register("niganma");

    public static final RegistryObject<SoundEvent> ROUDAN =
            register("roudan");

    // 小樱音效
    public static final RegistryObject<SoundEvent> SAKURA_ATT =
            register("sakura_att");

    public static final RegistryObject<SoundEvent> SAKURA_END =
            register("sakura_end");

    public static final RegistryObject<SoundEvent> SAKURA_XIONG =
            register("sakura_xiong");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUND_EVENTS.register(
                name,
                () -> SoundEvent.createVariableRangeEvent(
                        new ResourceLocation(YellowDuckMod.MOD_ID, name)
                )
        );
    }
}
