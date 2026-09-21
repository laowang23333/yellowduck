package com.yourname.yellowduck.particle;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "yellowduck", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModParticleClient {
    private ModParticleClient() {}

    @SubscribeEvent
    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SAKURA_FLAME.get(), SakuraParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SAKURA_PETAL.get(), SakuraParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SAKURA_MAGIC.get(), SakuraParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SAKURA_WARNING.get(), SakuraParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SAKURA_EXPLOSION.get(), SakuraParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SAKURA_ERUPTION.get(), SakuraParticle.Provider::new);

        event.registerSpriteSet(ModParticles.SILK_DARK_FIRE.get(), sprites -> new SilkParticle.Provider(sprites, SilkParticle.Style.DARK_FIRE));
        event.registerSpriteSet(ModParticles.SILK_SOUL.get(), sprites -> new SilkParticle.Provider(sprites, SilkParticle.Style.SOUL));
        event.registerSpriteSet(ModParticles.SILK_SMOKE.get(), sprites -> new SilkParticle.Provider(sprites, SilkParticle.Style.SMOKE));
        event.registerSpriteSet(ModParticles.SILK_DARK_SMOKE.get(), sprites -> new SilkParticle.Provider(sprites, SilkParticle.Style.DARK_SMOKE));
        event.registerSpriteSet(ModParticles.SILK_FEATHER.get(), sprites -> new SilkParticle.Provider(sprites, SilkParticle.Style.FEATHER));
        event.registerSpriteSet(ModParticles.SILK_FIRE.get(), sprites -> new SilkParticle.Provider(sprites, SilkParticle.Style.FIRE));
        event.registerSpriteSet(ModParticles.SILK_STONE_SMOKE.get(), sprites -> new SilkParticle.Provider(sprites, SilkParticle.Style.STONE_SMOKE));
        event.registerSpriteSet(ModParticles.SILK_GUSH.get(), sprites -> new SilkParticle.Provider(sprites, SilkParticle.Style.DARK_SMOKE));
    }
}
