package com.yourname.yellowduck.silk;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 斯尔克相关实体注册。
 *
 * 使用 DeferredRegister 延迟创建 EntityType，避免在 Forge 注册表冻结后
 * 因类静态初始化直接构造 EntityType 而触发 "Registry is already frozen"。
 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SilkContent {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, YellowDuckMod.MOD_ID);

    public static final RegistryObject<EntityType<SilkBoss>> BOSS =
            ENTITY_TYPES.register("silk_boss", () ->
                    EntityType.Builder.<SilkBoss>of(SilkBoss::new, MobCategory.MONSTER)
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
                    EntityType.Builder.<SilkMeteor>of(SilkMeteor::new, MobCategory.MONSTER)
                            .sized(1.3F, 1.3F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .fireImmune()
                            .build("silk_meteor"));

    /** 疫病转移之熊：外观复用小樱布偶熊模型，行为由 SilkPlagueBear 独立控制。 */
    public static final RegistryObject<EntityType<SilkPlagueBear>> PLAGUE_BEAR =
            ENTITY_TYPES.register("silk_plague_bear", () ->
                    EntityType.Builder.<SilkPlagueBear>of(SilkPlagueBear::new, MobCategory.MONSTER)
                            .sized(1.15F, 1.75F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build("silk_plague_bear"));

    private SilkContent() {
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(BOSS.get(), SilkBoss.createAttributes().build());
        event.put(BAT.get(), net.minecraft.world.entity.ambient.Bat.createAttributes().build());
        event.put(METEOR.get(), net.minecraft.world.entity.PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40)
                .add(Attributes.MOVEMENT_SPEED, 0.15)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1)
                .build());
        event.put(PLAGUE_BEAR.get(), SilkPlagueBear.createAttributes().build());
    }
}