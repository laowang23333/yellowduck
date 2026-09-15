package com.yourname.yellowduck.particle;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    private ModParticles() {}

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.<ParticleType<?>>create(
                    ForgeRegistries.PARTICLE_TYPES,
                    YellowDuckMod.MOD_ID
            );

    public static final RegistryObject<SimpleParticleType> SAKURA_FLAME =
            PARTICLES.register("sakura_flame",
                    () -> new SimpleParticleType(true));

    public static final RegistryObject<SimpleParticleType> SAKURA_PETAL =
            PARTICLES.register("sakura_petal",
                    () -> new SimpleParticleType(true));

    public static final RegistryObject<SimpleParticleType> SAKURA_MAGIC =
            PARTICLES.register("sakura_magic",
                    () -> new SimpleParticleType(true));

    public static final RegistryObject<SimpleParticleType> SAKURA_WARNING =
            PARTICLES.register("sakura_warning",
                    () -> new SimpleParticleType(true));

    public static final RegistryObject<SimpleParticleType> SAKURA_EXPLOSION =
            PARTICLES.register("sakura_explosion",
                    () -> new SimpleParticleType(true));

    public static final RegistryObject<SimpleParticleType> SAKURA_ERUPTION =
            PARTICLES.register("sakura_eruption",
                    () -> new SimpleParticleType(true));
}
