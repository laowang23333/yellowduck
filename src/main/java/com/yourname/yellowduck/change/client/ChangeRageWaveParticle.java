package com.yourname.yellowduck.change.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public final class ChangeRageWaveParticle extends TextureSheetParticle {
    private static final double RISE_PER_TICK = 0.2D;
    private static final int LIFETIME = 20;
    private static final int FADE_TICKS = 5;

    private ChangeRageWaveParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            SpriteSet sprites
    ) {
        super(level, x, y, z);
        this.quadSize = 0.13F;
        this.lifetime = LIFETIME;
        this.hasPhysics = false;
        this.setSprite(sprites.get(0, 1));
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        this.y += RISE_PER_TICK;
        int fadeStart = this.lifetime - FADE_TICKS;
        if (this.age > fadeStart) {
            this.alpha = Math.max(
                    0.0F,
                    1.0F - (this.age - fadeStart) / (float) FADE_TICKS
            );
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed
        ) {
            return new ChangeRageWaveParticle(level, x, y, z, sprites);
        }
    }
}
