package com.yourname.yellowduck.tengu;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.entity.MountEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TenguContent {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, YellowDuckMod.MOD_ID);

    public static final RegistryObject<EntityType<TenguBoss>> BOSS =
            ENTITY_TYPES.register("tengu_boss", () ->
                    EntityType.Builder.<TenguBoss>of(TenguBoss::new, MobCategory.MONSTER)
                            .sized(3.2F, 3.2F)
                            .clientTrackingRange(16)
                            .updateInterval(1)
                            .build("tengu_boss"));

    /** Boss 击杀后 10% 生成的“未驯服候选坐骑”。没有蛋。 */
    public static final RegistryObject<EntityType<TenguWildMountEntity>> WILD_MOUNT =
            ENTITY_TYPES.register("tengu_wild_mount", () ->
                    EntityType.Builder.<TenguWildMountEntity>of(TenguWildMountEntity::new, MobCategory.CREATURE)
                            .sized(2.4F, 2.4F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build("tengu_wild_mount"));

    /** 驯服完成后转入现有坐骑系统使用的实体。当前不提供任何坐骑蛋。 */
    public static final RegistryObject<EntityType<TenguMountEntity>> MOUNT =
            ENTITY_TYPES.register("tengu_mount", () ->
                    EntityType.Builder.<TenguMountEntity>of(TenguMountEntity::new, MobCategory.CREATURE)
                            .sized(2.4F, 2.4F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build("tengu_mount"));

    private TenguContent() {}

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(BOSS.get(), TenguBoss.createAttributes().build());
        event.put(WILD_MOUNT.get(), TenguWildMountEntity.createAttributes().build());
        event.put(MOUNT.get(), MountEntity.createAttributes().build());
    }
}
