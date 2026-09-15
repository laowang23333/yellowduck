package com.yourname.yellowduck.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 小樱/布偶熊通用贴图粒子。
 * 实际视觉来自 assets/yellowduck/textures/particle 下的 PNG。
 */
public class SakuraParticle extends TextureSheetParticle {
    private final float initialAlpha;

    protected SakuraParticle(ClientLevel level, double x, double y, double z,
                             SpriteSet sprites) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.friction = 0.92F;
        this.gravity = -0.005F;
        this.initialAlpha = 0.95F;
        this.alpha = initialAlpha;
        this.quadSize = 0.35F + this.random.nextFloat() * 0.25F;
        this.lifetime = 12 + this.random.nextInt(12);
        this.xd = (this.random.nextDouble() - 0.5D) * 0.025D;
        this.yd = 0.015D + this.random.nextDouble() * 0.025D;
        this.zd = (this.random.nextDouble() - 0.5D) * 0.025D;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        if (age >= lifetime - 5) {
            alpha = initialAlpha * (1.0F - (age - (lifetime - 5)) / 5.0F);
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd) {
            SakuraParticle particle = new SakuraParticle(level, x, y, z, sprites);
            particle.xd += xd;
            particle.yd += yd;
            particle.zd += zd;
            return particle;
        }
    }
}
