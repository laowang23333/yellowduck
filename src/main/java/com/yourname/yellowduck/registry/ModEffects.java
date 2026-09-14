package com.yourname.yellowduck.registry;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, YellowDuckMod.MOD_ID);

    // 魔法易伤：每层提升 5% 受到的魔法伤害，最高 10 层
    public static final RegistryObject<MobEffect> MAGIC_VULNERABILITY =
            EFFECTS.register("magic_vulnerability",
                    () -> new MobEffect(MobEffectCategory.HARMFUL, 0x9B30FF) {});
}
