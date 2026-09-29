package com.yourname.yellowduck.daji;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DajiContent {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, YellowDuckMod.MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, YellowDuckMod.MOD_ID);

    public static final RegistryObject<EntityType<DajiBoss>> BOSS = ENTITY_TYPES.register("daji_boss", () ->
            EntityType.Builder.<DajiBoss>of(DajiBoss::new, MobCategory.MONSTER)
                    .sized(1.2F, 2.6F).clientTrackingRange(64).updateInterval(1).build("daji_boss"));

    public static final RegistryObject<EntityType<DajiFoxMinion>> FOX = ENTITY_TYPES.register("daji_fox", () ->
            EntityType.Builder.<DajiFoxMinion>of(DajiFoxMinion::new, MobCategory.MONSTER)
                    .sized(1.6F, 2.0F).clientTrackingRange(48).updateInterval(1).build("daji_fox"));

    public static final RegistryObject<EntityType<DajiEffectEntity>> EFFECT = ENTITY_TYPES.register("daji_effect", () ->
            EntityType.Builder.<DajiEffectEntity>of(DajiEffectEntity::new, MobCategory.MISC)
                    .sized(0.2F, 0.2F).clientTrackingRange(64).updateInterval(1).build("daji_effect"));

    public static final RegistryObject<MobEffect> VULNERABILITY = EFFECTS.register("daji_white_fox_vulnerability", () ->
            new MobEffect(MobEffectCategory.HARMFUL, 0xEEE7FF) {});

    private DajiContent() {}

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(BOSS.get(), DajiBoss.createAttributes().build());
        event.put(FOX.get(), DajiFoxMinion.createAttributes().build());
    }
}
