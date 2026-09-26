package com.yourname.yellowduck.change;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.client.gltf.YellowGltfModel;
import com.yourname.yellowduck.client.gltf.YellowGltfModelCache;
import com.yourname.yellowduck.client.gltf.YellowGltfRenderUtil;
import com.yourname.yellowduck.change.client.ChangeEffectRenderer;
import com.yourname.yellowduck.change.client.ChangeRageWaveParticle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

@Mod.EventBusSubscriber(
        modid = YellowDuckMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class ChangeClient {
    private static ResourceLocation model(String name) {
        return new ResourceLocation(
                YellowDuckMod.MOD_ID,
                "models/gltf/change/" + name
        );
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                ChangeContent.BOSS.get(),
                context -> new BossRenderer(context, false)
        );
        event.registerEntityRenderer(
                ChangeContent.CLONE.get(),
                CloneRenderer::new
        );
        event.registerEntityRenderer(
                ChangeContent.RABBIT.get(),
                RabbitRenderer::new
        );
        event.registerEntityRenderer(
                ChangeContent.BREW.get(),
                BrewRenderer::new
        );
        event.registerEntityRenderer(ChangeContent.EFFECT_ATTACK.get(), ChangeEffectRenderer::new);
        event.registerEntityRenderer(ChangeContent.EFFECT_SUMMON.get(), ChangeEffectRenderer::new);
        event.registerEntityRenderer(ChangeContent.EFFECT_DRINK.get(), ChangeEffectRenderer::new);
        event.registerEntityRenderer(ChangeContent.MARK_DRINK.get(), ChangeEffectRenderer::new);
        event.registerEntityRenderer(ChangeContent.MARK_THIRST.get(), ChangeEffectRenderer::new);
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(
                ChangeContent.RAGE_WAVE.get(),
                ChangeRageWaveParticle.Provider::new
        );
    }

    private abstract static class Base<T extends net.minecraft.world.entity.Entity>
            extends EntityRenderer<T> {
        final YellowGltfModel model;
        final float scale;

        Base(
                EntityRendererProvider.Context context,
                String file,
                float scale,
                float shadow
        ) {
            super(context);
            this.scale = scale;
            this.shadowRadius = shadow;
            this.model = YellowGltfModelCache.getOrLoad(model(file));
        }

        @Override
        public ResourceLocation getTextureLocation(T entity) {
            return model != null && model.texture != null
                    ? model.texture
                    : MissingTextureAtlasSprite.getLocation();
        }

        void draw(
                T entity,
                float yaw,
                float partialTick,
                PoseStack pose,
                MultiBufferSource buffers,
                int light,
                float seconds
        ) {
            if (model == null) return;

            pose.pushPose();
            pose.mulPose(
                    new Quaternionf().rotationY(
                            (float) Math.toRadians(180.0F - yaw)
                    )
            );
            pose.scale(scale, scale, scale);

            YellowGltfRenderUtil.renderModel(
                    model,
                    pose,
                    buffers,
                    light,
                    seconds,
                    "Anim-1",
                    true,
                    0xFFFFFFFF
            );
            pose.popPose();
        }
    }

    private static final class BossRenderer extends Base<ChangeBoss> {
        BossRenderer(EntityRendererProvider.Context context, boolean ignored) {
            super(context, "boss_elite_chang_e.glb", 0.075F, 0.85F);
        }

        @Override
        public void render(
                ChangeBoss entity,
                float yaw,
                float partialTick,
                PoseStack pose,
                MultiBufferSource buffers,
                int light
        ) {
            float seconds = frameTime(
                    entity.action(),
                    entity.tickCount + partialTick
            );
            draw(
                    entity,
                    yaw + 180.0F,
                    partialTick,
                    pose,
                    buffers,
                    light,
                    seconds
            );
            super.render(
                    entity,
                    yaw,
                    partialTick,
                    pose,
                    buffers,
                    light
            );
        }
    }

    private static final class CloneRenderer extends Base<ChangeClone> {
        CloneRenderer(EntityRendererProvider.Context context) {
            super(context, "boss_elite_chang_e.glb", 0.075F, 0.85F);
        }

        @Override
        public void render(
                ChangeClone entity,
                float yaw,
                float partialTick,
                PoseStack pose,
                MultiBufferSource buffers,
                int light
        ) {
            float seconds =
                    (entity.attacking()
                            ? 57.0F
                            : entity.getDeltaMovement()
                                    .horizontalDistanceSqr() > 0.002D
                                    ? 21.0F
                                    : 0.0F)
                            / 30.0F
                            + (entity.tickCount % 20) / 30.0F;

            draw(
                    entity,
                    yaw + 180.0F,
                    partialTick,
                    pose,
                    buffers,
                    light,
                    seconds
            );
            super.render(
                    entity,
                    yaw,
                    partialTick,
                    pose,
                    buffers,
                    light
            );
        }
    }

    private static final class RabbitRenderer extends Base<ChangeRabbit> {
        RabbitRenderer(EntityRendererProvider.Context context) {
            super(context, "change_rabbit.glb", 0.065F, 0.45F);
        }

        @Override
        public void render(
                ChangeRabbit entity,
                float yaw,
                float partialTick,
                PoseStack pose,
                MultiBufferSource buffers,
                int light
        ) {
            int firstFrame = entity.action() == 2
                    ? 48
                    : entity.action() == 1
                    ? 20
                    : 0;

            float seconds =
                    firstFrame / 30.0F
                            + (entity.tickCount % 18) / 30.0F;

            draw(
                    entity,
                    yaw + 180.0F,
                    partialTick,
                    pose,
                    buffers,
                    light,
                    seconds
            );
            super.render(
                    entity,
                    yaw,
                    partialTick,
                    pose,
                    buffers,
                    light
            );
        }
    }

    private static final class BrewRenderer extends Base<GuiHuaNiangEntity> {
        BrewRenderer(EntityRendererProvider.Context context) {
            super(context, "elite_pool_purple.glb", 0.065F, 0.1F);
        }

        @Override
        public void render(
                GuiHuaNiangEntity entity,
                float yaw,
                float partialTick,
                PoseStack pose,
                MultiBufferSource buffers,
                int light
        ) {
            draw(
                    entity,
                    yaw,
                    partialTick,
                    pose,
                    buffers,
                    light,
                    (entity.tickCount + partialTick) / 20.0F
            );
            super.render(
                    entity,
                    yaw,
                    partialTick,
                    pose,
                    buffers,
                    light
            );
        }
    }

    private static float frameTime(int action, float ticks) {
        int start = switch (action) {
            case ChangeBoss.WALK -> 21;
            case ChangeBoss.ATTACK -> 57;
            case ChangeBoss.SKILL -> 79;
            case ChangeBoss.SUMMON -> 106;
            case ChangeBoss.RAGE -> 127;
            default -> 0;
        };

        int end = switch (action) {
            case ChangeBoss.WALK -> 56;
            case ChangeBoss.ATTACK -> 78;
            case ChangeBoss.SKILL -> 105;
            case ChangeBoss.SUMMON -> 126;
            case ChangeBoss.RAGE -> 150;
            default -> 20;
        };

        float length = Math.max(1, end - start);
        return (start + (ticks % length)) / 30.0F;
    }
}
