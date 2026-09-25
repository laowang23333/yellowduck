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
import java.util.UUID;

/** 恐惧之地 Native GLTF 渲染。 */
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

        private YellowGltfModel body;
        private YellowGltfModel wing;
        private boolean failed;

        private final Map<UUID, ActionClock> actionClocks = new HashMap<>();

        GarmrRenderer(EntityRendererProvider.Context context) {
            super(context);
            this.shadowRadius = 1.8F;
        }

        @Override
        public ResourceLocation getTextureLocation(GarmrBoss entity) {
            return body != null && body.texture != null
                    ? body.texture
                    : MissingTextureAtlasSprite.getLocation();
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
                    LOGGER.error("[YellowDuck/Garmr] missing animation clip {} in body/wing GLB",
                            animation.clip);
                    animation = new AnimationState(
                            entity.isVisualAirborne() ? "air_idle" : "idle",
                            (entity.tickCount + partialTick) / 20.0F,
                            true
                    );
                }

                pose.pushPose();
                try {
                    pose.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
                    pose.scale(SCALE, SCALE, SCALE);
                    YellowGltfRenderUtil.renderModel(
                            body, pose, buffers, packedLight,
                            animation.seconds, animation.clip, animation.loop);
                    YellowGltfRenderUtil.renderModel(
                            wing, pose, buffers, packedLight,
                            animation.seconds, animation.clip, animation.loop);
                } finally {
                    pose.popPose();
                }
            }

            super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
        }

        private AnimationState selectAnimation(GarmrBoss entity, float partialTick) {
            int action = entity.visualAction();

            int serial = entity.getEntityData().get(GarmrBoss.ACTION_SERIAL);
            float nowTicks = entity.tickCount + partialTick;
            UUID id = entity.getUUID();
            ActionClock clock = actionClocks.get(id);
            if (clock == null || clock.serial != serial) {
                clock = new ActionClock(serial, nowTicks);
                actionClocks.put(id, clock);
            }

            float actionSeconds =
                    Math.max(0.0F, (nowTicks - clock.localStartTick) / 20.0F);

            return switch (action) {
                case GarmrBoss.ACT_BASIC ->
                        new AnimationState("basic_attack", actionSeconds, false);
                case GarmrBoss.ACT_FIRE_BREATH ->
                        new AnimationState(
                                "fire_breath",
                                scaleToLogicalDuration(
                                        "fire_breath",
                                        actionSeconds,
                                        GarmrConfig.BREATH_DURATION_TICKS),
                                false);
                case GarmrBoss.ACT_ICE_BREATH ->
                        new AnimationState(
                                "ice_breath",
                                scaleToLogicalDuration(
                                        "ice_breath",
                                        actionSeconds,
                                        GarmrConfig.BREATH_DURATION_TICKS),
                                false);
                case GarmrBoss.ACT_RANGED ->
                        new AnimationState(
                                entity.isVisualAirborne() ? "air_ranged" : "ranged_attack",
                                actionSeconds,
                                false);
                case GarmrBoss.ACT_TAKEOFF ->
                        new AnimationState(
                                "takeoff",
                                scaleToLogicalDuration(
                                        "takeoff",
                                        actionSeconds,
                                        GarmrConfig.TAKEOFF_TICKS),
                                false);
                case GarmrBoss.ACT_LANDING ->
                        new AnimationState(
                                "landing",
                                scaleToLogicalDuration(
                                        "landing",
                                        actionSeconds,
                                        GarmrConfig.LANDING_TICKS),
                                false);
                case GarmrBoss.ACT_DEATH ->
                        new AnimationState("death", actionSeconds, false);
                default ->
                        new AnimationState(
                                entity.isVisualAirborne() ? "air_idle" : "idle",
                                (entity.tickCount + partialTick) / 20.0F,
                                true);
            };
        }

        private float scaleToLogicalDuration(
                String clip, float actionSeconds, int logicalTicks) {
            float clipDuration =
                    com.yourname.yellowduck.client.gltf.YellowGltfAnimationPlayer
                            .getAnimationDuration(body, clip);
            float logicalSeconds = Math.max(0.05F, logicalTicks / 20.0F);
            if (clipDuration <= 0.0F) return actionSeconds;
            return actionSeconds * clipDuration / logicalSeconds;
        }

        private record AnimationState(String clip, float seconds, boolean loop) {}
        private record ActionClock(int serial, float localStartTick) {}
    }

    /**
     * Garmr 辅助实体渲染。
     *
     * 骷髅射手和亡灵战士使用 NetCraft 1.4.23 原 GLB，
     * 因此直接调用其独立 stand/run/attack 动画；
     * 其余旧模型继续保持 YellowDuck 现有 Anim-1 时间轴。
     */
    private static final class HelperRenderer extends EntityRenderer<GarmrHelperEntity> {
        private static final Logger LOGGER = LogUtils.getLogger();
        private static final float FPS = 30.0F;
        private static final String ANIM = "Anim-1";

        private static final ResourceLocation ANUBIS = model("a_nubies.glb");
        private static final ResourceLocation DEVIL = model("little_devil.glb");
        private static final ResourceLocation ARCHER =
                model("entity_minion_t1_skeleton_embedded.glb");
        private static final ResourceLocation GUARD =
                model("entity_elite_t1_skeleton_embedded.glb");
        private static final ResourceLocation LAVA_GUARD =
                model("entity_minion_t4_fireelement_embedded.glb");
        private static final ResourceLocation LADY_ICE = model("countess_ice.glb");
        private static final ResourceLocation LADY_FIRE = model("countess_fire.glb");

        private final Map<ResourceLocation, YellowGltfModel> models = new HashMap<>();
        private final Map<ResourceLocation, Boolean> warned = new HashMap<>();

        HelperRenderer(EntityRendererProvider.Context context) {
            super(context);
            this.shadowRadius = 0.55F;
        }

        private static ResourceLocation model(String file) {
            return new ResourceLocation(
                    YellowDuckMod.MOD_ID,
                    "models/gltf/garmr/" + file
            );
        }

        @Override
        public ResourceLocation getTextureLocation(GarmrHelperEntity entity) {
            ModelSpec spec = spec(entity.getVariant());
            YellowGltfModel model =
                    spec == null ? null : load(spec.location);
            return model != null && model.texture != null
                    ? model.texture
                    : MissingTextureAtlasSprite.getLocation();
        }

        @Override
        public void render(
                GarmrHelperEntity entity,
                float entityYaw,
                float partialTick,
                PoseStack pose,
                MultiBufferSource buffers,
                int packedLight
        ) {
            ModelSpec spec = spec(entity.getVariant());
            if (spec != null) {
                YellowGltfModel model = load(spec.location);

                if (model != null) {
                    AnimationSample sample =
                            sampleAnimation(entity, partialTick, spec);

                    if (sample != null
                            && model.animationByName.containsKey(sample.animation)) {
                        pose.pushPose();
                        try {
                            pose.mulPose(
                                    Axis.YP.rotationDegrees(180.0F - entityYaw));
                            pose.scale(spec.scale, spec.scale, spec.scale);

                            YellowGltfRenderUtil.renderModel(
                                    model,
                                    pose,
                                    buffers,
                                    packedLight,
                                    sample.seconds,
                                    sample.animation,
                                    sample.loop,
                                    spec.tintArgb
                            );
                        } finally {
                            pose.popPose();
                        }
                    }
                }
            }

            super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
        }

        private AnimationSample sampleAnimation(
                GarmrHelperEntity entity,
                float partialTick,
                ModelSpec spec
        ) {
            float seconds = (entity.tickCount + partialTick) / 20.0F;
            int variant = entity.getVariant();

            if (variant == GarmrHelperEntity.P1_ARCHER) {
                float attackSeconds = entity.archerAttackSeconds(partialTick);

                if (attackSeconds >= 0.0F && attackSeconds < 1.57F) {
                    return new AnimationSample(
                            "Anim-1_attack_01",
                            attackSeconds,
                            false
                    );
                }

                return new AnimationSample(
                        entity.isArcherMoving()
                                ? "Anim-1_run"
                                : "Anim-1_stand",
                        seconds,
                        true
                );
            }

            if (variant == GarmrHelperEntity.DEATH_GUARD) {
                return new AnimationSample(
                        entity.getDeltaMovement().horizontalDistanceSqr() > 0.0025D
                                ? "Anim-1_run"
                                : "Anim-1_stand",
                        seconds,
                        true
                );
            }

            if (variant == GarmrHelperEntity.LAVA_GUARD) {
                return new AnimationSample(
                        entity.getDeltaMovement().horizontalDistanceSqr() > 0.0025D
                                ? "Anim-1_run"
                                : "Anim-1_stand",
                        seconds,
                        true
                );
            }

            String animation = spec.animation;
            if (!ANIM.equals(animation)) {
                return new AnimationSample(animation, seconds, true);
            }

            int first = 0;
            int last = 20;

            if ((variant == GarmrHelperEntity.LADY_ICE
                    || variant == GarmrHelperEntity.LADY_FIRE)
                    && entity.getDeltaMovement().horizontalDistanceSqr() > 0.0025D) {
                first = 23;
                last = 43;
            }

            return new AnimationSample(
                    ANIM,
                    sampleLoop(first, last, seconds),
                    true
            );
        }

        private YellowGltfModel load(ResourceLocation location) {
            YellowGltfModel cached = models.get(location);
            if (cached != null) return cached;

            YellowGltfModel loaded =
                    YellowGltfModelCache.getOrLoad(location);

            if (loaded != null) {
                models.put(location, loaded);
                return loaded;
            }

            if (!warned.getOrDefault(location, false)) {
                warned.put(location, true);
                LOGGER.error(
                        "[YellowDuck/Garmr] helper GLB failed to load: {}",
                        location
                );
            }

            return null;
        }

        private static float sampleLoop(
                int firstFrame,
                int lastFrame,
                float seconds
        ) {
            float length =
                    Math.max(1, lastFrame - firstFrame) / FPS;
            float local = seconds % length;
            if (local < 0.0F) local += length;
            return firstFrame / FPS + local;
        }

        private static ModelSpec spec(int variant) {
            return switch (variant) {
                case GarmrHelperEntity.ANUBIS ->
                        new ModelSpec(
                                ANUBIS,
                                0.065F,
                                0xFFFFFFFF,
                                ANIM
                        );
                case GarmrHelperEntity.DEVIL ->
                        new ModelSpec(
                                DEVIL,
                                0.11F,
                                0xFF9C62B8,
                                ANIM
                        );
                case GarmrHelperEntity.P1_ARCHER ->
                        new ModelSpec(
                                ARCHER,
                                0.10F,
                                0xFFFFFFFF,
                                "Anim-1_stand"
                        );
                case GarmrHelperEntity.DEATH_GUARD ->
                        new ModelSpec(
                                GUARD,
                                0.10F,
                                0xFFFFFFFF,
                                "Anim-1_stand"
                        );
                case GarmrHelperEntity.LADY_ICE ->
                        new ModelSpec(
                                LADY_ICE,
                                0.070F,
                                0xFFFFFFFF,
                                ANIM
                        );
                case GarmrHelperEntity.LADY_FIRE ->
                        new ModelSpec(
                                LADY_FIRE,
                                0.070F,
                                0xFFFFFFFF,
                                ANIM
                        );
                case GarmrHelperEntity.LAVA_GUARD ->
                        new ModelSpec(
                                LAVA_GUARD,
                                0.10F,
                                0xFFFFFFFF,
                                "Anim-1_stand"
                        );
                default -> null;
            };
        }

        private record ModelSpec(
                ResourceLocation location,
                float scale,
                int tintArgb,
                String animation
        ) {}

        private record AnimationSample(
                String animation,
                float seconds,
                boolean loop
        ) {}
    }

    /** 投射物本体不画几何体，视觉由服务端同步粒子承担。 */
    private static final class EmptyProjectileRenderer
            extends EntityRenderer<GarmrProjectile> {

        EmptyProjectileRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public ResourceLocation getTextureLocation(GarmrProjectile entity) {
            return MissingTextureAtlasSprite.getLocation();
        }
    }
}
