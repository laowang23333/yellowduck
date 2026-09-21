package com.yourname.yellowduck.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** 斯尔克原版贴图粒子的轻量Forge实现。位置/方向由服务端技能逻辑决定。 */
public class SilkParticle extends TextureSheetParticle {
    public enum Style {
        DARK_FIRE, SOUL, SMOKE, DARK_SMOKE, FEATHER, FIRE, STONE_SMOKE
    }

    private final SpriteSet sprites;
    private final float startAlpha;

    protected SilkParticle(ClientLevel level, double x, double y, double z,
                           double xd, double yd, double zd,
                           SpriteSet sprites, Style style) {
        super(level, x, y, z, xd, yd, zd);
        this.sprites = sprites;
        this.startAlpha = 0.92F;
        this.alpha = startAlpha;
        this.friction = 0.92F;
        this.gravity = 0.0F;

        switch (style) {
            case DARK_FIRE -> {
                this.quadSize = 0.35F + random.nextFloat() * 0.35F;
                this.lifetime = 10 + random.nextInt(12);
            }
            case SOUL -> {
                this.quadSize = 0.30F + random.nextFloat() * 0.35F;
                this.lifetime = 10 + random.nextInt(10);
                this.yd += 0.008D;
            }
            case SMOKE -> {
                this.quadSize = 0.65F + random.nextFloat() * 0.65F;
                this.lifetime = 12 + random.nextInt(14);
                this.friction = 0.95F;
            }
            case DARK_SMOKE -> {
                this.quadSize = 0.75F + random.nextFloat() * 0.95F;
                this.lifetime = 18 + random.nextInt(16);
                this.friction = 0.96F;
                this.yd += 0.01D;
            }
            case FEATHER -> {
                this.quadSize = 0.24F + random.nextFloat() * 0.18F;
                this.lifetime = 16 + random.nextInt(10);
                this.roll = random.nextFloat() * ((float) Math.PI * 2.0F);
                this.oRoll = this.roll;
            }
            case FIRE -> {
                this.quadSize = 0.30F + random.nextFloat() * 0.30F;
                this.lifetime = 14 + random.nextInt(16);
                this.gravity = -0.01F;
            }
            case STONE_SMOKE -> {
                this.quadSize = 0.90F + random.nextFloat() * 0.90F;
                this.lifetime = 16 + random.nextInt(16);
                this.friction = 0.94F;
            }
        }
        this.setSpriteFromAge(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        if (age < lifetime) setSpriteFromAge(sprites);
        if (age >= lifetime - 5) {
            alpha = startAlpha * Math.max(0.0F, (lifetime - age) / 5.0F);
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final Style style;

        public Provider(SpriteSet sprites, Style style) {
            this.sprites = sprites;
            this.style = style;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new SilkParticle(level, x, y, z, xd, yd, zd, sprites, style);
        }
    }
}
