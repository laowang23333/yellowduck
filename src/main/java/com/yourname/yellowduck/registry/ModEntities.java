package com.yourname.yellowduck.registry;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.entity.MountEntity;
import com.yourname.yellowduck.entity.RabbitMountEntity;
import com.yourname.yellowduck.entity.AlpacaMountEntity;
import com.yourname.yellowduck.entity.BambooHorseEntity;
import com.yourname.yellowduck.entity.SakurawitchEntity;
import com.yourname.yellowduck.entity.ToyBearEntity;
import com.yourname.yellowduck.entity.TwoPhaseBossEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, YellowDuckMod.MOD_ID);

    public static final RegistryObject<EntityType<TwoPhaseBossEntity>> TWO_PHASE_BOSS =
            ENTITIES.register("two_phase_boss", () ->
                    EntityType.Builder.<TwoPhaseBossEntity>of(TwoPhaseBossEntity::new, MobCategory.MONSTER)
                            .sized(1.0F, 1.0F)
                            .build("two_phase_boss"));

    public static final RegistryObject<EntityType<MountEntity>> MOUNT =
            ENTITIES.register("mount", () ->
                    EntityType.Builder.<MountEntity>of(MountEntity::new, MobCategory.CREATURE)
                            .sized(2.4F, 2.2F)
                            .build("mount"));

    public static final RegistryObject<EntityType<AlpacaMountEntity>> ALPACA_MOUNT =
            ENTITIES.register("alpaca_mount", () ->
                    EntityType.Builder.<AlpacaMountEntity>of(AlpacaMountEntity::new, MobCategory.CREATURE)
                            .sized(1.4F, 2.2F)
                            .clientTrackingRange(10)
                            .build("alpaca_mount"));

    public static final RegistryObject<EntityType<RabbitMountEntity>> RABBIT_MOUNT =
            ENTITIES.register("rabbit_mount", () ->
                    EntityType.Builder.<RabbitMountEntity>of(RabbitMountEntity::new, MobCategory.CREATURE)
                            .sized(1.4F, 1.25F).clientTrackingRange(10).updateInterval(1)
                            .build("rabbit_mount"));

    public static final RegistryObject<EntityType<BambooHorseEntity>> BAMBOO_HORSE_MOUNT =
            ENTITIES.register("bamboo_horse_mount", () ->
                    EntityType.Builder.<BambooHorseEntity>of(BambooHorseEntity::new, MobCategory.CREATURE)
                            .sized(1.5F, 1.45F).clientTrackingRange(10).updateInterval(1)
                            .build("bamboo_horse_mount"));

    // ==========================================
    // 小樱 Boss
    // ==========================================
    public static final RegistryObject<EntityType<SakurawitchEntity>> SAKURA_WITCH =
            ENTITIES.register("sakurawitch", () ->
                    EntityType.Builder.<SakurawitchEntity>of(SakurawitchEntity::new, MobCategory.MONSTER)
                            .sized(1.3F, 2.8F)
                            .clientTrackingRange(10)
                            .build("sakurawitch"));

    // ==========================================
    // 小樱布偶熊
    // ==========================================
    public static final RegistryObject<EntityType<ToyBearEntity>> TOY_BEAR =
            ENTITIES.register("toy_bear", () ->
                    EntityType.Builder.<ToyBearEntity>of(ToyBearEntity::new, MobCategory.MONSTER)
                            .sized(1.15F, 1.75F)
                            .clientTrackingRange(10)
                            .build("toy_bear"));
}
