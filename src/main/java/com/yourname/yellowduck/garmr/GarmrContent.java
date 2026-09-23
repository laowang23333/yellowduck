package com.yourname.yellowduck.garmr;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GarmrContent {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, YellowDuckMod.MOD_ID);

    public static final RegistryObject<EntityType<GarmrBoss>> BOSS = ENTITY_TYPES.register("garmr", () ->
            EntityType.Builder.<GarmrBoss>of(GarmrBoss::new, MobCategory.MONSTER)
                    .sized(4.2F, 3.8F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .build("garmr"));

    public static final RegistryObject<EntityType<GarmrProjectile>> PROJECTILE = ENTITY_TYPES.register("garmr_projectile", () ->
            EntityType.Builder.<GarmrProjectile>of(GarmrProjectile::new, MobCategory.MISC)
                    .sized(0.45F, 0.45F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build("garmr_projectile"));

    private GarmrContent() {}

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(BOSS.get(), GarmrBoss.createAttributes().build());
    }
}
