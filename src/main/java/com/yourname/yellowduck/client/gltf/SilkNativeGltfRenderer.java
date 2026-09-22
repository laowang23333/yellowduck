package com.yourname.yellowduck.client.gltf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.yourname.yellowduck.client.ClientEntityMotionState;
import com.yourname.yellowduck.silk.SilkBoss;
import com.yourname.yellowduck.silk.SilkClient;
import com.yourname.yellowduck.silk.SilkContent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * YellowDuck-owned GLB renderer for Professor Silk.
 *
 * <p>This is the permanent replacement for the temporary NetCraft reflection bridge.
 * It reads the existing embedded-texture GLB directly, samples animation at
 * {@code tickCount + partialTick}, and has no NetCraft runtime dependency.</p>
 */
@Mod.EventBusSubscriber(modid = "yellowduck", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SilkNativeGltfRenderer extends EntityRenderer<SilkBoss> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation MODEL =
            new ResourceLocation("yellowduck", "models/gltf/silk_boss_embedded.glb");
    private static final float SCALE = 0.15F;
    private static final int MOVE_GRACE_TICKS = 4;

    private final Map<SilkBoss, AnimationState> states = new WeakHashMap<>();
    private final SilkClient.BossRenderer fallback;
    private YellowGltfModel model;
    private boolean nativeDisabled;
    private boolean loggedReady;

    public SilkNativeGltfRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.8F;
        this.fallback = new SilkClient.BossRenderer(context);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        // SilkClient registers the old PolyMesh provider earlier. Registering at LOWEST
        // intentionally replaces only Professor Silk, leaving every other model untouched.
        event.registerEntityRenderer(SilkContent.BOSS.get(), SilkNativeGltfRenderer::new);
        LOGGER.info("[YellowDuck] Professor Silk YellowDuck Native GLTF renderer registered");
    }

    @Override
    public ResourceLocation getTextureLocation(SilkBoss boss) {
        YellowGltfModel current = model;
        return current != null && current.texture != null
                ? current.texture
                : MissingTextureAtlasSprite.getLocation();
    }

    @Override
    public void render(SilkBoss boss, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        if (nativeDisabled) {
            fallback.render(boss, yaw, partialTick, pose, buffers, packedLight);
            return;
        }

        try {
            YellowGltfModel current = model;
            if (current == null) {
                current = YellowGltfModelCache.getOrLoad(MODEL);
                if (current == null) {
                    nativeDisabled = true;
                    fallback.render(boss, yaw, partialTick, pose, buffers, packedLight);
                    return;
                }
                model = current;
                if (!loggedReady) {
                    loggedReady = true;
                    LOGGER.info("[YellowDuck] Professor Silk now uses YellowDuck Native GLTF (NetCraft independent)");
                }
            }

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

            pose.pushPose();
            try {
                // Same transform as the already verified NetCraft bridge test.
                pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
                pose.scale(SCALE, SCALE, SCALE);
                YellowGltfRenderUtil.renderModel(
                        current, pose, buffers, packedLight,
                        animationSeconds, animation, loop);
            } finally {
                pose.popPose();
            }
            super.render(boss, yaw, partialTick, pose, buffers, packedLight);
        } catch (Throwable error) {
            nativeDisabled = true;
            LOGGER.error("[YellowDuck] Native GLTF renderer failed for Professor Silk; reverting to PolyMesh", error);
            fallback.render(boss, yaw, partialTick, pose, buffers, packedLight);
        }
    }

    private static final class AnimationState {
        String animation = "";
        int lastSerial = Integer.MIN_VALUE;
        int animationStartTick;
    }
}
