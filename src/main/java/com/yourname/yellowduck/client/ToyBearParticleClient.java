package com.yourname.yellowduck.client;

import com.yourname.yellowduck.particle.ModParticles;
import com.yourname.yellowduck.particle.SakuraParticle;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "yellowduck", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ToyBearParticleClient {
    private ToyBearParticleClient() {}

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        register(event, ModParticles.SAKURA_FLAME.get());
        register(event, ModParticles.SAKURA_PETAL.get());
        register(event, ModParticles.SAKURA_MAGIC.get());
        register(event, ModParticles.SAKURA_WARNING.get());
        register(event, ModParticles.SAKURA_EXPLOSION.get());
        register(event, ModParticles.SAKURA_ERUPTION.get());

        register(event, ModParticles.SAKURA_BEAR_RAGE_RING.get());
        register(event, ModParticles.SAKURA_BEAR_RAGE_MIST.get());
        register(event, ModParticles.SAKURA_BEAR_RAGE_SHARD.get());
        register(event, ModParticles.SAKURA_BEAR_RAGE_GROUND_RING.get());
        register(event, ModParticles.SAKURA_BEAR_RAGE_SLASH.get());
        register(event, ModParticles.SAKURA_BEAR_RAGE_SPARK.get());
        register(event, ModParticles.SAKURA_BEAR_RAGE_BURST.get());
    }

    private static void register(RegisterParticleProvidersEvent event,
                                  net.minecraft.core.particles.SimpleParticleType type) {
        event.registerSpriteSet(type, SakuraParticle.Provider::new);
    }
}
