package com.yourname.yellowduck.garmr;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** 原 .pj 贴图的轻量客户端承载器；位置/扇形由服务端决定。 */
@OnlyIn(Dist.CLIENT)
public final class GarmrParticle extends TextureSheetParticle {
    private final float startAlpha;

    private GarmrParticle(ClientLevel level, double x, double y, double z,
                          double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd);
        this.friction = 0.92F;
        this.gravity = 0.0F;
        this.quadSize = 0.85F + random.nextFloat() * 0.55F;
        this.lifetime = 16 + random.nextInt(5); // 约 800~1000ms
        this.startAlpha = 0.92F;
        this.alpha = startAlpha;
        this.setSpriteFromAge(sprites);
    }

    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    @Override
    public void tick() {
        super.tick();
        if (age > lifetime - 4) alpha = startAlpha * Math.max(0.0F, (lifetime - age) / 4.0F);
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double xd, double yd, double zd) {
            return new GarmrParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
