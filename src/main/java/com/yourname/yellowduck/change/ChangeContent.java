package com.yourname.yellowduck.change;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ChangeContent {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, YellowDuckMod.MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, YellowDuckMod.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, YellowDuckMod.MOD_ID);

    public static final RegistryObject<EntityType<ChangeBoss>> BOSS = ENTITY_TYPES.register("change_boss",
            () -> EntityType.Builder.of(ChangeBoss::new, MobCategory.MONSTER)
                    .sized(0.9F, 2.2F).clientTrackingRange(12).build("change_boss"));
    public static final RegistryObject<EntityType<ChangeClone>> CLONE = ENTITY_TYPES.register("change_clone",
            () -> EntityType.Builder.of(ChangeClone::new, MobCategory.MONSTER)
                    .sized(0.9F, 2.2F).clientTrackingRange(12).build("change_clone"));
    public static final RegistryObject<EntityType<ChangeRabbit>> RABBIT = ENTITY_TYPES.register("change_brewing_rabbit",
            () -> EntityType.Builder.of(ChangeRabbit::new, MobCategory.MONSTER)
                    .sized(0.8F, 1.15F).clientTrackingRange(10).build("change_brewing_rabbit"));
    public static final RegistryObject<EntityType<GuiHuaNiangEntity>> BREW = ENTITY_TYPES.register("gui_hua_niang",
            () -> EntityType.Builder.<GuiHuaNiangEntity>of(GuiHuaNiangEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.35F).clientTrackingRange(10).build("gui_hua_niang"));

    public static final RegistryObject<MobEffect> DRINK = EFFECTS.register("change_drink",
            () -> new ChangeStackEffect(false, 0xD9B45C));
    public static final RegistryObject<MobEffect> THIRST = EFFECTS.register("change_thirst",
            () -> new ChangeStackEffect(true, 0xA44EBC));
    public static final RegistryObject<MobEffect> DRUNKEN = EFFECTS.register("change_drunken",
            () -> new ChangeStackEffect(true, 0x7A4E9E));
    public static final RegistryObject<MobEffect> DRINK_BOOST = EFFECTS.register("change_drink_boost",
            () -> new ChangeStackEffect(false, 0xF2D46B));

    public static final RegistryObject<SoundEvent> SKILL = sound("change_skill");
    public static final RegistryObject<SoundEvent> DEATH = sound("change_death");

    private static RegistryObject<SoundEvent> sound(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(
                new ResourceLocation(YellowDuckMod.MOD_ID, id)));
    }

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
        EFFECTS.register(bus);
        SOUNDS.register(bus);
    }

    private ChangeContent() {}
}
