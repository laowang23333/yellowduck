package com.yourname.yellowduck.cleopatra;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class CleopatraEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, YellowDuckMod.MOD_ID);
    private static MobEffect plain(int color) { return new MobEffect(MobEffectCategory.HARMFUL, color) {}; }
    public static final RegistryObject<MobEffect> VENOM_POISON = EFFECTS.register("venom_poison", () -> plain(0x66CCFF));
    public static final RegistryObject<MobEffect> VENOM_BURNING = EFFECTS.register("venom_burning", () -> plain(0xFF6644));
    public static final RegistryObject<MobEffect> VENOM_FROZEN = EFFECTS.register("venom_frozen", () -> plain(0x33AAFF));
    public static final RegistryObject<MobEffect> VENOM_BOMB_POISON = EFFECTS.register("venom_bomb_poison", () -> plain(0x66AAFF));
    public static final RegistryObject<MobEffect> VENOM_BOMB_BURNING = EFFECTS.register("venom_bomb_burning", () -> plain(0xFF5555));
    public static final RegistryObject<MobEffect> VENOM_BOMB_FROZEN = EFFECTS.register("venom_bomb_frozen", () -> plain(0x3388FF));
    public static final RegistryObject<MobEffect> VENOM_SICKNESS = EFFECTS.register("venom_sickness", VenomSickness::new);
    private CleopatraEffects() {}

    private static final class VenomSickness extends MobEffect {
        private VenomSickness() { super(MobEffectCategory.HARMFUL, 0x88CC44); }
        @Override public void applyEffectTick(LivingEntity e, int amplifier) {
            if (!e.level().isClientSide) e.hurt(e.damageSources().indirectMagic(e, e), 2.0F);
        }
        @Override public boolean isDurationEffectTick(int duration, int amplifier) { return duration > 0 && duration % 20 == 0; }
    }
}
