package com.yourname.yellowduck.garmr;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.client.gltf.YellowGltfAnimationPlayer;
import com.yourname.yellowduck.client.gltf.YellowGltfModel;
import com.yourname.yellowduck.client.gltf.YellowGltfModelCache;
import com.yourname.yellowduck.client.gltf.YellowGltfRenderUtil;
import com.yourname.yellowduck.particle.ModParticles;
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
import org.slf4j.Logger;

/** 加姆专用 Native GLTF 双层渲染：主体/翅膀使用同名动画 clip 与同一个实体时钟。 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GarmrClient {
    private GarmrClient() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GarmrContent.BOSS.get(), GarmrRenderer::new);
        event.registerEntityRenderer(GarmrContent.PROJECTILE.get(), EmptyProjectileRenderer::new);
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.GARMR_FIRE.get(), GarmrParticle.Provider::new);
        event.registerSpriteSet(ModParticles.GARMR_ICE.get(), GarmrParticle.Provider::new);
        event.registerSpriteSet(ModParticles.GARMR_DEVIL_SMOKE.get(), GarmrParticle.Provider::new);
    }

    private static final class GarmrRenderer extends EntityRenderer<GarmrBoss> {
        private static final Logger LOGGER = LogUtils.getLogger();
        private static final ResourceLocation BODY =
                new ResourceLocation(YellowDuckMod.MOD_ID, "models/gltf/garmr_body_embedded.glb");
        private static final ResourceLocation WING =
                new ResourceLocation(YellowDuckMod.MOD_ID, "models/gltf/garmr_wing_embedded.glb");
        private static final float SCALE = 0.10F;

        private YellowGltfModel body;
        private YellowGltfModel wing;
        private boolean failed;

        GarmrRenderer(EntityRendererProvider.Context context) {
            super(context);
            this.shadowRadius = 1.8F;
        }

        @Override
        public ResourceLocation getTextureLocation(GarmrBoss entity) {
            return body != null && body.texture != null ? body.texture : MissingTextureAtlasSprite.getLocation();
        }

        @Override
        public void render(GarmrBoss entity, float entityYaw, float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int packedLight) {
            if (!failed && (body == null || wing == null)) {
                body = YellowGltfModelCache.getOrLoad(BODY);
                wing = YellowGltfModelCache.getOrLoad(WING);
                if (body == null || wing == null) {
                    failed = true;
                    LOGGER.error("[YellowDuck/Garmr] Native GLTF body/wing failed to load");
                }
            }

            if (!failed && body != null && wing != null) {
                AnimationState animation = selectAnimation(entity, partialTick);
                if (!body.animationByName.containsKey(animation.clip)
                        || !wing.animationByName.containsKey(animation.clip)) {
                    LOGGER.error("[YellowDuck/Garmr] missing animation clip {} in body/wing GLB", animation.clip);
                    animation = new AnimationState(entity.isVisualAirborne() ? "air_idle" : "idle",
                            (entity.tickCount + partialTick) / 20.0F, true);
                }

                pose.pushPose();
                try {
                    pose.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
                    pose.scale(SCALE, SCALE, SCALE);
                    YellowGltfRenderUtil.renderModel(body, pose, buffers, packedLight,
                            animation.seconds, animation.clip, animation.loop);
                    YellowGltfRenderUtil.renderModel(wing, pose, buffers, packedLight,
                            animation.seconds, animation.clip, animation.loop);
                } finally {
                    pose.popPose();
                }
            }
            super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
        }

        private AnimationState selectAnimation(GarmrBoss entity, float partialTick) {
            int action = entity.visualAction();
            float actionSeconds = Math.max(0.0F,
                    (entity.tickCount + partialTick - entity.visualActionStartTick()) / 20.0F);

            return switch (action) {
                case GarmrBoss.ACT_BASIC -> new AnimationState("basic_attack", actionSeconds, false);
                case GarmrBoss.ACT_FIRE_BREATH -> new AnimationState("fire_breath",
                        scaleToLogicalDuration("fire_breath", actionSeconds, GarmrConfig.BREATH_DURATION_TICKS), false);
                case GarmrBoss.ACT_ICE_BREATH -> new AnimationState("ice_breath",
                        scaleToLogicalDuration("ice_breath", actionSeconds, GarmrConfig.BREATH_DURATION_TICKS), false);
                case GarmrBoss.ACT_RANGED -> new AnimationState(
                        entity.isVisualAirborne() ? "air_ranged" : "ranged_attack", actionSeconds, false);
                case GarmrBoss.ACT_LANDING -> new AnimationState("landing",
                        scaleToLogicalDuration("landing", actionSeconds, GarmrConfig.LANDING_TICKS), false);
                case GarmrBoss.ACT_DEATH -> new AnimationState("death", actionSeconds, false);
                default -> new AnimationState(entity.isVisualAirborne() ? "air_idle" : "idle",
                        (entity.tickCount + partialTick) / 20.0F, true);
            };
        }

        private float scaleToLogicalDuration(String clip, float actionSeconds, int logicalTicks) {
            float clipDuration = YellowGltfAnimationPlayer.getAnimationDuration(body, clip);
            float logicalSeconds = Math.max(0.05F, logicalTicks / 20.0F);
            if (clipDuration <= 0.0F) return actionSeconds;
            return actionSeconds * clipDuration / logicalSeconds;
        }

        private record AnimationState(String clip, float seconds, boolean loop) {}
    }

    /** 投射物本体不画几何体，视觉由服务端同步粒子承担。 */
    private static final class EmptyProjectileRenderer extends EntityRenderer<GarmrProjectile> {
        EmptyProjectileRenderer(EntityRendererProvider.Context context) { super(context); }
        @Override public ResourceLocation getTextureLocation(GarmrProjectile entity) {
            return MissingTextureAtlasSprite.getLocation();
        }
    }
}
