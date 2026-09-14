package com.yourname.yellowduck.registry;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.entity.MountEntity;
import com.yourname.yellowduck.entity.SakurawitchEntity;
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
                            .sized(1.5F, 1.5F)
                            .build("mount"));

    // ==========================================
    // 小樱 Boss
    // ==========================================
    public static final RegistryObject<EntityType<SakurawitchEntity>> SAKURA_WITCH =
            ENTITIES.register("sakurawitch", () ->
                    EntityType.Builder.<SakurawitchEntity>of(SakurawitchEntity::new, MobCategory.MONSTER)
                            .sized(1.3F, 2.8F)
                            .clientTrackingRange(10)
                            .build("sakurawitch"));
}
