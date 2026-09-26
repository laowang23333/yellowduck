package com.yourname.yellowduck.registry;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.effect.MooncakeIconEffect;
import com.yourname.yellowduck.effect.RootEntangleEffect;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, YellowDuckMod.MOD_ID);

    // 双头犬索命：图标显示层数；实际叠层/10层必死由 GarmrBoss 维护。
    public static final RegistryObject<MobEffect> GARMR_DEATH_CURSE = EFFECTS.register("garmr_death_curse",
                    () -> new MobEffect(MobEffectCategory.HARMFUL, 0x5B176E) {});

    public static final RegistryObject<MobEffect> MAGIC_VULNERABILITY =
            EFFECTS.register("magic_vulnerability",
                    () -> new MobEffect(MobEffectCategory.HARMFUL, 0x9B30FF) {});

    public static final RegistryObject<MobEffect> SAKURA_MAGIC_WEAKNESS =
            EFFECTS.register("sakura_magic_weakness",
                    () -> new MobEffect(MobEffectCategory.HARMFUL, 0x7A3A8C) {});

    // 布偶熊 P3：根须缠绕。
    public static final RegistryObject<MobEffect> ROOT_ENTANGLE =
            EFFECTS.register("root_entangle", RootEntangleEffect::new);

    /** 红色樱花冰皮月饼：实际 20% 增伤在 MooncakeItem 的最终伤害事件中处理。 */
    public static final RegistryObject<MobEffect> SAKURA_MOONCAKE_DAMAGE =
            EFFECTS.register("sakura_mooncake_damage",
                    () -> new MooncakeIconEffect(
                            0xF05B6F,
                            new ResourceLocation(YellowDuckMod.MOD_ID,
                                    "textures/mob_effect/sakura_mooncake_damage.png"),
                            false
                    ));

    /** 黄色樱花冰皮月饼：生命恢复 I，并使用黄色月饼图标。 */
    public static final RegistryObject<MobEffect> SAKURA_MOONCAKE_REGENERATION =
            EFFECTS.register("sakura_mooncake_regeneration",
                    () -> new MooncakeIconEffect(
                            0xFFD45E,
                            new ResourceLocation(YellowDuckMod.MOD_ID,
                                    "textures/mob_effect/sakura_mooncake_regeneration.png"),
                            true
                    ));
}
