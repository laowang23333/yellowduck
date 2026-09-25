package com.yourname.yellowduck.registry;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.effect.RootEntangleEffect;
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
}
