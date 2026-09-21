package com.yourname.yellowduck.registry;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, YellowDuckMod.MOD_ID);

    // 鸡哥音效
    public static final RegistryObject<SoundEvent> JIJIJI = register("jijiji");
    public static final RegistryObject<SoundEvent> NIGANMA = register("niganma");
    public static final RegistryObject<SoundEvent> ROUDAN = register("roudan");

    // 小樱音效
    public static final RegistryObject<SoundEvent> SAKURA_ATT = register("sakura_att");
    public static final RegistryObject<SoundEvent> SAKURA_END = register("sakura_end");
    public static final RegistryObject<SoundEvent> SAKURA_XIONG = register("sakura_xiong");
    public static final RegistryObject<SoundEvent> XIAOQI_DISC = register("xiaoqi_disc");
    public static final RegistryObject<SoundEvent> YIDIYIDI_DISC = register("yidiyidi_disc");

    // 印神像放置音效
    public static final RegistryObject<SoundEvent> XJY = register("xjy");

    // 五张唱片
    public static final RegistryObject<SoundEvent> MUSIC_DISC_1 = register("music_disc_1");
    public static final RegistryObject<SoundEvent> MUSIC_DISC_2 = register("music_disc_2");
    public static final RegistryObject<SoundEvent> MUSIC_DISC_3 = register("music_disc_3");
    public static final RegistryObject<SoundEvent> MUSIC_DISC_4 = register("music_disc_4");
    public static final RegistryObject<SoundEvent> MUSIC_DISC_5 = register("music_disc_5");

    // 疯狂教授斯尔克
    public static final RegistryObject<SoundEvent> SILK_ATT1 = register("silk_att1");
    public static final RegistryObject<SoundEvent> SILK_ATT4 = register("silk_att4");
    public static final RegistryObject<SoundEvent> SILK_DEATH = register("silk_death");
    public static final RegistryObject<SoundEvent> SILK_BEAR_ROAR = register("silk_bear_roar");
    public static final RegistryObject<SoundEvent> SILK_BEAR_HURT = register("silk_bear_hurt");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUND_EVENTS.register(
                name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(YellowDuckMod.MOD_ID, name))
        );
    }
}
