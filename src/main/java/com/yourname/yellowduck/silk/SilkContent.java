package com.yourname.yellowduck.silk;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/** 独立注册，避免覆盖项目已有的ModEntities/ModItems/客户端注册。 */
@Mod.EventBusSubscriber(modid = "yellowduck", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SilkContent {
    public static final EntityType<SilkBoss> BOSS = EntityType.Builder.<SilkBoss>of(SilkBoss::new, MobCategory.MONSTER)
            .sized(1.8F, 4.4F).clientTrackingRange(12).updateInterval(1).fireImmune().build("silk_boss");
    public static final EntityType<SilkBat> BAT = EntityType.Builder.<SilkBat>of(SilkBat::new, MobCategory.MONSTER)
            .sized(0.7F, 0.7F).clientTrackingRange(12).updateInterval(1).build("silk_shadow_bat");
    public static final EntityType<SilkMeteor> METEOR = EntityType.Builder.<SilkMeteor>of(SilkMeteor::new, MobCategory.MONSTER)
            .sized(1.3F, 1.3F).clientTrackingRange(12).updateInterval(1).fireImmune().build("silk_meteor");
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            helper.register(new ResourceLocation("yellowduck", "silk_boss"), BOSS);
            helper.register(new ResourceLocation("yellowduck", "silk_shadow_bat"), BAT);
            helper.register(new ResourceLocation("yellowduck", "silk_meteor"), METEOR);
        });
    }
    @SubscribeEvent public static void attributes(EntityAttributeCreationEvent event) {
        event.put(BOSS, SilkBoss.createAttributes().build());
        event.put(BAT, net.minecraft.world.entity.ambient.Bat.createAttributes().build());
        event.put(METEOR, net.minecraft.world.entity.PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40).add(Attributes.MOVEMENT_SPEED, 0.15)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1).build());
    }
}
