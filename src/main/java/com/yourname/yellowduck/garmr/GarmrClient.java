package com.yourname.yellowduck.garmr;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.yourname.yellowduck.YellowDuckMod;
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

import java.util.HashMap;
import java.util.Map;

/** 恐惧之地 Native GLTF 渲染：加姆主体/翅膀 + V5 已解析的辅助实体原模型。 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GarmrClient {
    private GarmrClient() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GarmrContent.BOSS.get(), GarmrRenderer::new);
        event.registerEntityRenderer(GarmrContent.PROJECTILE.get(), EmptyProjectileRenderer::new);
        event.registerEntityRenderer(GarmrContent.HELPER.get(), HelperRenderer::new);
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.GARMR_FIRE.get(),
                sprites -> new GarmrParticle.Provider(sprites, GarmrParticle.Mode.BREATH));
        event.registerSpriteSet(ModParticles.GARMR_ICE.get(),
                sprites -> new GarmrParticle.Provider(sprites, GarmrParticle.Mode.BREATH));
    }

    private static final class GarmrRenderer extends EntityRenderer<GarmrBoss> {
        private static final Logger LOGGER = LogUtils.getLogger();
        private static final ResourceLocation BODY =
                new ResourceLocation(YellowDuckMod.MOD_ID, "models/gltf/garmr_body_embedded.glb");
        private static final ResourceLocation WING =
                new ResourceLocation(YellowDuckMod.MOD_ID, "models/gltf/garmr_wing_embedded.glb");
        private static final float SCALE = 0.10F;
        private static final float FPS = 30.0F;
        private static final String SOURCE_ANIMATION = "Anim-1";

        private static final FrameRange IDLE = new FrameRange(0, 20, false);
        private static final FrameRange BASIC = new FrameRange(44, 66, false);
        private static final FrameRange ICE_BREATH = new FrameRange(93, 117, false);
        private static final FrameRange FIRE_BREATH = new FrameRange(121, 142, false);
        private static final FrameRange RANGED = new FrameRange(143, 172, false);
        private static final FrameRange DEATH = new FrameRange(176, 200, false);
        private static final FrameRange TAKEOFF = new FrameRange(274, 303, false);
        private static final FrameRange AIR_IDLE = new FrameRange(304, 330, false);
        private static final FrameRange AIR_RANGED = new FrameRange(331, 354, false);
        private static final FrameRange LANDING = new FrameRange(274, 303, true);

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
                if (!body.animationByName.containsKey(SOURCE_ANIMATION)
                        || !wing.animationByName.containsKey(SOURCE_ANIMATION)) {
                    failed = true;
                    LOGGER.error("[YellowDuck/Garmr] body/wing GLB missing original Anim-1");
                } else {
                    AnimationState animation = selectAnimation(entity, partialTick);
                    pose.pushPose();
                    try {
                        pose.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
                        pose.scale(SCALE, SCALE, SCALE);
                        YellowGltfRenderUtil.renderModel(body, pose, buffers, packedLight,
                                animation.sampleSeconds, SOURCE_ANIMATION, false);
                        YellowGltfRenderUtil.renderModel(wing, pose, buffers, packedLight,
                                animation.sampleSeconds, SOURCE_ANIMATION, false);
                    } finally {
                        pose.popPose();
                    }
                }
            }
            super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
        }

        private AnimationState selectAnimation(GarmrBoss entity, float partialTick) {
            int action = entity.visualAction();
            float actionSeconds = Math.max(0.0F,
                    (entity.tickCount + partialTick - entity.visualActionStartTick()) / 20.0F);

            return switch (action) {
                case GarmrBoss.ACT_BASIC -> state(BASIC, actionSeconds, GarmrConfig.BASIC_ACTION_TICKS, false);
                case GarmrBoss.ACT_FIRE_BREATH -> state(FIRE_BREATH, actionSeconds,
                        GarmrConfig.BREATH_DURATION_TICKS, false);
                case GarmrBoss.ACT_ICE_BREATH -> state(ICE_BREATH, actionSeconds,
                        GarmrConfig.BREATH_DURATION_TICKS, false);
                case GarmrBoss.ACT_RANGED -> state(entity.isVisualAirborne() ? AIR_RANGED : RANGED,
                        actionSeconds, GarmrConfig.RANGED_ACTION_TICKS, false);
                case GarmrBoss.ACT_TAKEOFF -> state(TAKEOFF, actionSeconds,
                        GarmrConfig.TAKEOFF_TICKS, false);
                case GarmrBoss.ACT_LANDING -> state(LANDING, actionSeconds,
                        GarmrConfig.LANDING_TICKS, false);
                case GarmrBoss.ACT_DEATH -> naturalState(DEATH, actionSeconds, false);
                default -> loopState(entity.isVisualAirborne() ? AIR_IDLE : IDLE,
                        (entity.tickCount + partialTick) / 20.0F);
            };
        }

        private AnimationState state(FrameRange range, float actionSeconds, int logicalTicks, boolean loop) {
            float rangeSeconds = range.lengthSeconds();
            float logicalSeconds = Math.max(0.05F, logicalTicks / 20.0F);
            float progressSeconds = actionSeconds * rangeSeconds / logicalSeconds;
            if (loop) progressSeconds = wrap(progressSeconds, rangeSeconds);
            else progressSeconds = Math.max(0.0F, Math.min(rangeSeconds, progressSeconds));
            return new AnimationState(range.sample(progressSeconds));
        }

        private AnimationState naturalState(FrameRange range, float seconds, boolean loop) {
            float local = loop ? wrap(seconds, range.lengthSeconds())
                    : Math.max(0.0F, Math.min(range.lengthSeconds(), seconds));
            return new AnimationState(range.sample(local));
        }

        private AnimationState loopState(FrameRange range, float seconds) {
            return new AnimationState(range.sample(wrap(seconds, range.lengthSeconds())));
        }

        private float wrap(float value, float length) {
            if (length <= 1.0E-6F) return 0.0F;
            float result = value % length;
            return result < 0.0F ? result + length : result;
        }

        private record AnimationState(float sampleSeconds) {}

        private record FrameRange(int firstFrame, int lastFrame, boolean reverse) {
            float lengthSeconds() { return Math.max(1, lastFrame - firstFrame) / FPS; }
            float sample(float localSeconds) {
                float first = firstFrame / FPS;
                float last = lastFrame / FPS;
                float clamped = Math.max(0.0F, Math.min(lengthSeconds(), localSeconds));
                return reverse ? last - clamped : first + clamped;
            }
        }
    }

    /** V5 原资源辅助实体渲染。每个 GLB 都只保留原始 Anim-1，避免重复动画导致文件膨胀。 */
    private static final class HelperRenderer extends EntityRenderer<GarmrHelperEntity> {
        private static final Logger LOGGER = LogUtils.getLogger();
        private static final float FPS = 30.0F;
        private static final String ANIM = "Anim-1";
        private static final ResourceLocation ANUBIS = model("a_nubies.glb");
        private static final ResourceLocation DEVIL = model("little_devil.glb");
        private static final ResourceLocation ARCHER = model("skeleton_archer.glb");
        private static final ResourceLocation GUARD = model("skeleton_guards.glb");
        private static final ResourceLocation LADY_ICE = model("countess_ice.glb");
        private static final ResourceLocation LADY_FIRE = model("countess_fire.glb");

        private final Map<ResourceLocation, YellowGltfModel> models = new HashMap<>();
        private final Map<ResourceLocation, Boolean> warned = new HashMap<>();

        HelperRenderer(EntityRendererProvider.Context context) {
            super(context);
            this.shadowRadius = 0.55F;
        }

        private static ResourceLocation model(String file) {
            return new ResourceLocation(YellowDuckMod.MOD_ID, "models/gltf/garmr/" + file);
        }

        @Override
        public ResourceLocation getTextureLocation(GarmrHelperEntity entity) {
            ModelSpec spec = spec(entity.getVariant());
            YellowGltfModel model = spec == null ? null : load(spec.location);
            return model != null && model.texture != null ? model.texture : MissingTextureAtlasSprite.getLocation();
        }

        @Override
        public void render(GarmrHelperEntity entity, float entityYaw, float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int packedLight) {
            ModelSpec spec = spec(entity.getVariant());
            if (spec != null) {
                YellowGltfModel model = load(spec.location);
                if (model != null && model.animationByName.containsKey(ANIM)) {
                    float seconds = (entity.tickCount + partialTick) / 20.0F;
                    int first = 0;
                    int last = 20;
                    // 亡灵夫人动画表已从原 config/represent/ani.txt 确认：stand 0-20 / walk 23-43。
                    if ((entity.getVariant() == GarmrHelperEntity.LADY_ICE
                            || entity.getVariant() == GarmrHelperEntity.LADY_FIRE
                            || entity.getVariant() == GarmrHelperEntity.DEATH_GUARD)
                            && entity.getDeltaMovement().horizontalDistanceSqr() > 0.0025D) {
                        first = 23;
                        last = 43;
                    }
                    float sampleSeconds = sampleLoop(first, last, seconds);
                    pose.pushPose();
                    try {
                        pose.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
                        pose.scale(spec.scale, spec.scale, spec.scale);
                        YellowGltfRenderUtil.renderModel(model, pose, buffers, packedLight,
                                sampleSeconds, ANIM, false, spec.tintArgb);
                    } finally {
                        pose.popPose();
                    }
                }
            }
            super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
        }

        private YellowGltfModel load(ResourceLocation location) {
            YellowGltfModel cached = models.get(location);
            if (cached != null) return cached;
            YellowGltfModel loaded = YellowGltfModelCache.getOrLoad(location);
            if (loaded != null) {
                models.put(location, loaded);
                return loaded;
            }
            if (!warned.getOrDefault(location, false)) {
                warned.put(location, true);
                LOGGER.error("[YellowDuck/Garmr] helper GLB failed to load: {}", location);
            }
            return null;
        }

        private static float sampleLoop(int firstFrame, int lastFrame, float seconds) {
            float length = Math.max(1, lastFrame - firstFrame) / FPS;
            float local = seconds % length;
            if (local < 0.0F) local += length;
            return firstFrame / FPS + local;
        }

        private static ModelSpec spec(int variant) {
            return switch (variant) {
                case GarmrHelperEntity.ANUBIS -> new ModelSpec(ANUBIS, 0.065F, 0xFFFFFFFF);
                // 小恶魔/骷髅两个上传包只有 .x，没有原贴图字节；V5 先用中性内嵌底图 + 顶点色，
                // 保证真实几何/骨骼/动画可用，后续拿到原贴图只替换 GLB 内图即可。
                case GarmrHelperEntity.DEVIL -> new ModelSpec(DEVIL, 0.11F, 0xFF9C62B8);
                case GarmrHelperEntity.P1_ARCHER -> new ModelSpec(ARCHER, 0.10F, 0xFFD8D8D8);
                case GarmrHelperEntity.DEATH_GUARD -> new ModelSpec(GUARD, 0.10F, 0xFFC8C8C8);
                case GarmrHelperEntity.LADY_ICE -> new ModelSpec(LADY_ICE, 0.070F, 0xFFFFFFFF);
                case GarmrHelperEntity.LADY_FIRE -> new ModelSpec(LADY_FIRE, 0.070F, 0xFFFFFFFF);
                default -> null;
            };
        }

        private record ModelSpec(ResourceLocation location, float scale, int tintArgb) {}
    }

    /** 投射物本体不画几何体，视觉由服务端同步粒子承担。 */
    private static final class EmptyProjectileRenderer extends EntityRenderer<GarmrProjectile> {
        EmptyProjectileRenderer(EntityRendererProvider.Context context) { super(context); }
        @Override public ResourceLocation getTextureLocation(GarmrProjectile entity) {
            return MissingTextureAtlasSprite.getLocation();
        }
    }
}
