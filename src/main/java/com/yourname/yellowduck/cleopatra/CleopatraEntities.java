package com.yourname.yellowduck.cleopatra;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 艳后战斗系统的全部实体注册。尺寸/追踪频率按 Stargazer 原版。 */
public final class CleopatraEntities {
    public static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, YellowDuckMod.MOD_ID);

    public static final RegistryObject<EntityType<CleopatraBoss>> BOSS = TYPES.register("cleopatra", () ->
            EntityType.Builder.<CleopatraBoss>of(CleopatraBoss::new, MobCategory.MONSTER)
                    .sized(0.8F, 2.0F).clientTrackingRange(64).updateInterval(1).build("cleopatra"));

    public static final RegistryObject<EntityType<CleopatraSandworm>> SANDWORM = TYPES.register("cleopatra_sandworm", () ->
            EntityType.Builder.<CleopatraSandworm>of(CleopatraSandworm::new, MobCategory.MONSTER)
                    .sized(2.2F, 1.0F).clientTrackingRange(64).updateInterval(3).build("cleopatra_sandworm"));

    public static final RegistryObject<EntityType<CleopatraScorpion>> SCORPION = TYPES.register("cleopatra_scorpion", () ->
            EntityType.Builder.<CleopatraScorpion>of(CleopatraScorpion::new, MobCategory.MONSTER)
                    .sized(0.9F, 0.7F).clientTrackingRange(64).updateInterval(3).build("cleopatra_scorpion"));

    public static final RegistryObject<EntityType<CleopatraVenomBullet>> VENOM_BULLET = TYPES.register("cleopatra_venom_bullet", () ->
            EntityType.Builder.<CleopatraVenomBullet>of(CleopatraVenomBullet::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(64).updateInterval(3).build("cleopatra_venom_bullet"));

    public static final RegistryObject<EntityType<CleopatraVenomPool>> VENOM_POOL = TYPES.register("cleopatra_venom_pool", () ->
            EntityType.Builder.<CleopatraVenomPool>of(CleopatraVenomPool::new, MobCategory.MISC)
                    .sized(1.0F, 0.2F).clientTrackingRange(64).updateInterval(5).build("cleopatra_venom_pool"));

    public static final RegistryObject<EntityType<CleopatraVenomRing>> VENOM_RING_FIRE = ring("cleopatra_venom_ring_fire");
    public static final RegistryObject<EntityType<CleopatraVenomRing>> VENOM_RING_ICE = ring("cleopatra_venom_ring_ice");
    public static final RegistryObject<EntityType<CleopatraVenomRing>> VENOM_RING_PLAIN = ring("cleopatra_venom_ring_plain");

    public static final RegistryObject<EntityType<CleopatraBombMark>> BOMB_MARK_POISON = bomb("cleopatra_bomb_mark_poison");
    public static final RegistryObject<EntityType<CleopatraBombMark>> BOMB_MARK_BURNING = bomb("cleopatra_bomb_mark_burning");
    public static final RegistryObject<EntityType<CleopatraBombMark>> BOMB_MARK_FROZEN = bomb("cleopatra_bomb_mark_frozen");

    public static final RegistryObject<EntityType<CleopatraSnakeSummoner>> SNAKE_SUMMONER = TYPES.register("cleopatra_snake_summoner", () ->
            EntityType.Builder.<CleopatraSnakeSummoner>of(CleopatraSnakeSummoner::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F).clientTrackingRange(64).updateInterval(20).build("cleopatra_snake_summoner"));

    public static final RegistryObject<EntityType<CleopatraVenomSnake>> SNAKE_POISON = snake("cleopatra_snake_poison");
    public static final RegistryObject<EntityType<CleopatraVenomSnake>> SNAKE_FIRE = snake("cleopatra_snake_fire");
    public static final RegistryObject<EntityType<CleopatraVenomSnake>> SNAKE_ICE = snake("cleopatra_snake_ice");

    private static RegistryObject<EntityType<CleopatraVenomRing>> ring(String id) {
        return TYPES.register(id, () -> EntityType.Builder.<CleopatraVenomRing>of(CleopatraVenomRing::new, MobCategory.MISC)
                .sized(1.0F, 0.2F).clientTrackingRange(64).updateInterval(5).build(id));
    }

    private static RegistryObject<EntityType<CleopatraBombMark>> bomb(String id) {
        return TYPES.register(id, () -> EntityType.Builder.<CleopatraBombMark>of(CleopatraBombMark::new, MobCategory.MISC)
                .sized(0.6F, 0.6F).clientTrackingRange(64).updateInterval(3).build(id));
    }

    private static RegistryObject<EntityType<CleopatraVenomSnake>> snake(String id) {
        return TYPES.register(id, () -> EntityType.Builder.<CleopatraVenomSnake>of(CleopatraVenomSnake::new, MobCategory.MONSTER)
                .sized(1.0F, 10.0F).clientTrackingRange(64).updateInterval(1).build(id));
    }

    private CleopatraEntities() {}
}
