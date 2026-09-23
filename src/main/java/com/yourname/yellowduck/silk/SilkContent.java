package com.yourname.yellowduck.silk;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 斯尔克战斗相关实体注册。 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SilkContent {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, YellowDuckMod.MOD_ID);

    public static final RegistryObject<EntityType<SilkBoss>> BOSS =
            ENTITY_TYPES.register("silk_boss", () ->
                    EntityType.Builder.<SilkBoss>of(LockedSilkBoss::new, MobCategory.MONSTER)
                            .sized(1.8F, 4.4F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .fireImmune()
                            .build("silk_boss"));

    public static final RegistryObject<EntityType<SilkBat>> BAT =
            ENTITY_TYPES.register("silk_shadow_bat", () ->
                    EntityType.Builder.<SilkBat>of(SilkBat::new, MobCategory.MONSTER)
                            .sized(0.7F, 0.7F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build("silk_shadow_bat"));

    public static final RegistryObject<EntityType<SilkMeteor>> METEOR =
            ENTITY_TYPES.register("silk_meteor", () ->
                    EntityType.Builder.<SilkMeteor>of(SilkMeteor::new, MobCategory.MISC)
                            .sized(1.3F, 1.3F)
                            .clientTrackingRange(16)
                            .updateInterval(1)
                            .fireImmune()
                            .build("silk_meteor"));

    /** 旧存档兼容：保留旧“疫病转移熊”实体类型，但新战斗逻辑不再召唤它。 */
    public static final RegistryObject<EntityType<SilkPlagueBear>> PLAGUE_BEAR =
            ENTITY_TYPES.register("silk_plague_bear", () ->
                    EntityType.Builder.<SilkPlagueBear>of(SilkPlagueBear::new, MobCategory.MONSTER)
                            .sized(1.15F, 1.75F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build("silk_plague_bear"));

    public static final RegistryObject<EntityType<SilkDarkTeddy>> DARK_TEDDY =
            ENTITY_TYPES.register("silk_dark_teddy", () ->
                    EntityType.Builder.<SilkDarkTeddy>of(SilkDarkTeddy::new, MobCategory.MONSTER)
                            .sized(1.15F, 1.75F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build("silk_dark_teddy"));

    public static final RegistryObject<EntityType<SilkDarkSlime>> DARK_SLIME =
            ENTITY_TYPES.register("silk_dark_slime", () ->
                    EntityType.Builder.<SilkDarkSlime>of(SilkDarkSlime::new, MobCategory.MONSTER)
                            .sized(1.0F, 1.0F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build("silk_dark_slime"));

    public static final RegistryObject<EntityType<SilkBlackWater>> BLACK_WATER =
            ENTITY_TYPES.register("silk_black_water", () ->
                    EntityType.Builder.<SilkBlackWater>of(SilkBlackWater::new, MobCategory.MISC)
                            .sized(3.0F, 0.15F)
                            .clientTrackingRange(16)
                            .updateInterval(10)
                            .fireImmune()
                            .build("silk_black_water"));

    public static final RegistryObject<EntityType<SilkBlackBall>> BLACK_BALL =
            ENTITY_TYPES.register("silk_black_ball", () ->
                    EntityType.Builder.<SilkBlackBall>of(SilkBlackBall::new, MobCategory.MONSTER)
                            .sized(1.0F, 1.0F)
                            .clientTrackingRange(16)
                            .updateInterval(1)
                            .fireImmune()
                            .build("silk_black_ball"));

    public static final RegistryObject<EntityType<SilkVisualCircle>> VISUAL_CIRCLE =
            ENTITY_TYPES.register("silk_visual_circle", () ->
                    EntityType.Builder.<SilkVisualCircle>of(SilkVisualCircle::new, MobCategory.MISC)
                            .sized(10.0F, 0.1F)
                            .clientTrackingRange(16)
                            .updateInterval(10)
                            .fireImmune()
                            .build("silk_visual_circle"));

    private SilkContent() {
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(BOSS.get(), SilkBoss.createAttributes().build());
        event.put(BAT.get(), net.minecraft.world.entity.ambient.Bat.createAttributes().build());
        event.put(PLAGUE_BEAR.get(), SilkPlagueBear.createAttributes().build());
        event.put(DARK_TEDDY.get(), SilkDarkTeddy.createAttributes().build());
        event.put(DARK_SLIME.get(), SilkDarkSlime.createAttributes().build());
        event.put(BLACK_BALL.get(), SilkBlackBall.createAttributes().build());
    }
}
