package com.yourname.yellowduck.client.gltf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.logging.LogUtils;
import com.yourname.yellowduck.client.ClientEntityMotionState;
import com.yourname.yellowduck.silk.SilkBoss;
import com.yourname.yellowduck.silk.SilkContent;
import dev.phe.polymesh.animation.AnimationController;
import dev.phe.polymesh.api.GltfRenderOptions;
import dev.phe.polymesh.client.GltfEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import org.slf4j.Logger;

/**
 * Professor Silk renderer test bridge.
 *
 * <p>When NetCraft is installed, this renderer reuses NetCraft's proven GLTF loader,
 * model cache, animation sampler and skinned-mesh renderer through reflection. There
 * is no compile-time NetCraft dependency and the NetCraft jar does not need to be
 * committed into YellowDuck. If NetCraft is absent, this class does not replace the
 * existing PolyMesh renderer. If a runtime NetCraft render call fails, the renderer
 * falls back to the old PolyMesh path so the boss never becomes invisible.</p>
 */
@Mod.EventBusSubscriber(modid = "yellowduck", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SilkNetcraftRendererBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation MODEL =
            new ResourceLocation("yellowduck", "models/gltf/silk_boss_embedded.glb");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("yellowduck", "textures/entity/silk/silk_boss_netcraft.png");

    private SilkNetcraftRendererBridge() {
    }

    /** LOWEST makes this registration run after the existing SilkClient registration. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        if (NetcraftBridge.AVAILABLE) {
            // EntityRenderers stores providers by EntityType. Running at LOWEST priority
            // intentionally replaces SilkClient's earlier PolyMesh provider for this one boss.
            event.registerEntityRenderer(SilkContent.BOSS.get(), NetcraftBossRenderer::new);
            LOGGER.info("[YellowDuck] Professor Silk NetCraft GLTF renderer bridge enabled");
        } else {
            LOGGER.info("[YellowDuck] NetCraft GLTF core not found; Professor Silk keeps PolyMesh renderer");
        }
    }

    public static final class NetcraftBossRenderer extends EntityRenderer<SilkBoss> {
        private static final float SCALE = 0.15F;
        private static final int MOVE_GRACE_TICKS = 4;

        private final Map<SilkBoss, AnimationState> states = new WeakHashMap<>();
        private final FallbackRenderer fallback;

        public NetcraftBossRenderer(EntityRendererProvider.Context context) {
            super(context);
            this.shadowRadius = 0.8F;
            this.fallback = new FallbackRenderer(context);
        }

        @Override
        public ResourceLocation getTextureLocation(SilkBoss boss) {
            return TEXTURE;
        }

        @Override
        public void render(SilkBoss boss, float yaw, float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int packedLight) {
            AnimationState state = states.computeIfAbsent(boss, ignored -> new AnimationState());
            int attack = boss.getEntityData().get(SilkBoss.ANIMATION);
            int serial = boss.getEntityData().get(SilkBoss.CAST_SERIAL);

            boolean moving = ClientEntityMotionState.isMoving(
                    boss, boss.getEntityData().get(SilkBoss.WALKING), MOVE_GRACE_TICKS);

            String animation = !boss.isAlive() || attack < 0 ? "Anim-1_death"
                    : attack > 0 ? "Anim-1_attack_0" + attack
                    : moving ? "Anim-1_walk"
                    : boss.getEntityData().get(SilkBoss.MAD) ? "Anim-1_stand2"
                    : "Anim-1_stand";
            boolean loop = attack == 0 && boss.isAlive();
            boolean restartCast = attack != 0 && state.lastSerial != serial;

            if (!animation.equals(state.animation) || restartCast) {
                state.animation = animation;
                state.lastSerial = serial;
                state.animationStartTick = boss.tickCount;
            }

            float animationSeconds = Math.max(0.0F,
                    (boss.tickCount - state.animationStartTick + partialTick) / 20.0F);

            boolean rendered = false;
            pose.pushPose();
            try {
                // Same entity transform used by NetCraft's own generic GLTF renderer.
                pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
                pose.scale(SCALE, SCALE, SCALE);
                rendered = NetcraftBridge.render(
                        MODEL, TEXTURE, pose, buffers, packedLight,
                        animationSeconds, animation, loop, 0.0F);
            } finally {
                pose.popPose();
            }

            if (!rendered) {
                fallback.render(boss, yaw, partialTick, pose, buffers, packedLight);
                return;
            }
            super.render(boss, yaw, partialTick, pose, buffers, packedLight);
        }
    }

    private static final class AnimationState {
        private String animation = "";
        private int lastSerial = Integer.MIN_VALUE;
        private int animationStartTick;
    }

    /** Old renderer retained only as a safety fallback. */
    private static final class FallbackRenderer extends GltfEntityRenderer<SilkBoss> {
        private final Map<SilkBoss, Integer> serials = new WeakHashMap<>();

        private FallbackRenderer(EntityRendererProvider.Context context) {
            super(context, new ResourceLocation("yellowduck", "silk_boss_embedded"),
                    GltfRenderOptions.builder()
                            .scale(0.15F)
                            .shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU)
                            .preferGpuAnimatedMeshes(false)
                            .preferGpuStaticMeshes(false)
                            .loopAnimation(true)
                            .build());
        }

        @Override
        public void render(SilkBoss boss, float yaw, float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int light) {
            try {
                AnimationController controller = getAnimationController(boss);
                if (controller != null) {
                    int attack = boss.getEntityData().get(SilkBoss.ANIMATION);
                    int serial = boss.getEntityData().get(SilkBoss.CAST_SERIAL);
                    String animation = !boss.isAlive() || attack < 0 ? "Anim-1_death"
                            : attack > 0 ? "Anim-1_attack_0" + attack
                            : boss.getEntityData().get(SilkBoss.WALKING) ? "Anim-1_walk"
                            : boss.getEntityData().get(SilkBoss.MAD) ? "Anim-1_stand2"
                            : "Anim-1_stand";
                    boolean restart = attack != 0
                            && serials.getOrDefault(boss, Integer.MIN_VALUE) != serial;
                    if (restart || !animation.equals(controller.getAnimationName())) {
                        controller.play(animation, attack == 0 && boss.isAlive());
                        serials.put(boss, serial);
                    }
                }
            } catch (Throwable ignored) {
            }
            super.render(boss, yaw, partialTick, pose, buffers, light);
        }
    }

    /** Reflection-only bridge so YellowDuck does not compile against NetCraft. */
    private static final class NetcraftBridge {
        private static final Method GET_OR_LOAD;
        private static final Method RENDER_MODEL;
        private static final boolean AVAILABLE;
        private static volatile Object cachedModel;
        private static volatile boolean disabled;

        static {
            Method getOrLoad = null;
            Method renderModel = null;
            boolean available = false;
            try {
                Class<?> modelClass = Class.forName("com.jiufeng.netcraft.client.model.gltf.GltfModel");
                Class<?> cacheClass = Class.forName("com.jiufeng.netcraft.client.model.gltf.GltfModelCache");
                Class<?> renderClass = Class.forName("com.jiufeng.netcraft.client.model.gltf.GltfRenderUtil");

                getOrLoad = cacheClass.getMethod(
                        "getOrLoad", ResourceLocation.class, ResourceLocation.class);
                renderModel = renderClass.getMethod(
                        "renderModel",
                        modelClass,
                        PoseStack.class,
                        MultiBufferSource.class,
                        ResourceLocation.class,
                        int.class,
                        float.class,
                        String.class,
                        boolean.class,
                        boolean.class,
                        float.class);
                available = true;
            } catch (Throwable ignored) {
                // NetCraft not installed or its internal GLTF API changed.
            }
            GET_OR_LOAD = getOrLoad;
            RENDER_MODEL = renderModel;
            AVAILABLE = available;
        }

        private NetcraftBridge() {
        }

        private static boolean render(ResourceLocation modelPath,
                                      ResourceLocation texturePath,
                                      PoseStack pose,
                                      MultiBufferSource buffers,
                                      int packedLight,
                                      float animationSeconds,
                                      String animation,
                                      boolean loop,
                                      float yawOffsetDegrees) {
            if (!AVAILABLE || disabled) {
                return false;
            }
            try {
                Object model = cachedModel;
                if (model == null) {
                    model = GET_OR_LOAD.invoke(null, modelPath, texturePath);
                    if (model == null) {
                        return false;
                    }
                    cachedModel = model;
                }

                RENDER_MODEL.invoke(
                        null,
                        model,
                        pose,
                        buffers,
                        texturePath,
                        packedLight,
                        animationSeconds,
                        animation,
                        false,
                        loop,
                        yawOffsetDegrees);
                return true;
            } catch (Throwable error) {
                // Disable only this experimental bridge; PolyMesh fallback remains usable.
                disabled = true;
                LOGGER.error("[YellowDuck] NetCraft GLTF bridge failed; falling back to PolyMesh", error);
                return false;
            }
        }
    }
}
