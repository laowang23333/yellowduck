package com.yourname.yellowduck.particle;

import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = "yellowduck",
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class ModParticleClient {
    private ModParticleClient() {}

    @SubscribeEvent
    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(
                ModParticles.SAKURA_FLAME.get(),
                SakuraParticle.Provider::new
        );

        event.registerSpriteSet(
                ModParticles.SAKURA_PETAL.get(),
                SakuraParticle.Provider::new
        );

        event.registerSpriteSet(
                ModParticles.SAKURA_MAGIC.get(),
                SakuraParticle.Provider::new
        );

        event.registerSpriteSet(
                ModParticles.SAKURA_WARNING.get(),
                SakuraParticle.Provider::new
        );

        event.registerSpriteSet(
                ModParticles.SAKURA_EXPLOSION.get(),
                SakuraParticle.Provider::new
        );

        event.registerSpriteSet(
                ModParticles.SAKURA_ERUPTION.get(),
                SakuraParticle.Provider::new
        );
    }
}
