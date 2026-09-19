package com.yourname.yellowduck.silk;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.phe.polymesh.animation.AnimationController;
import dev.phe.polymesh.api.GltfRenderOptions;
import dev.phe.polymesh.client.GltfEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.BatRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = "yellowduck", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SilkClient {
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SilkContent.BOSS.get(), BossRenderer::new);
        event.registerEntityRenderer(SilkContent.BAT.get(), BatRenderer::new);
        event.registerEntityRenderer(SilkContent.METEOR.get(), MeteorRenderer::new);
    }
    public static final class BossRenderer extends GltfEntityRenderer<SilkBoss> {
        private final Map<SilkBoss, Integer> serials = new WeakHashMap<>();
        public BossRenderer(EntityRendererProvider.Context context) {
            super(context, new ResourceLocation("yellowduck", "silk_boss_embedded"), GltfRenderOptions.builder()
                    .scale(0.15F).shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU)
                    .preferGpuAnimatedMeshes(false).preferGpuStaticMeshes(false).loopAnimation(true).build());
        }
        @Override public void render(SilkBoss boss, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            AnimationController controller = getAnimationController(boss);
            if (controller != null) {
                int attack = boss.getEntityData().get(SilkBoss.ANIMATION);
                int serial = boss.getEntityData().get(SilkBoss.CAST_SERIAL);
                String animation = !boss.isAlive() || attack < 0 ? "Anim-1_death"
                        : attack > 0 ? "Anim-1_attack_0" + attack
                        : boss.getEntityData().get(SilkBoss.WALKING) ? "Anim-1_walk"
                        : boss.getEntityData().get(SilkBoss.MAD) ? "Anim-1_stand2" : "Anim-1_stand";
                if (!animation.equals(controller.getAnimationName()) || (attack > 0 && serials.getOrDefault(boss, -1) != serial)) {
                    controller.play(animation, attack == 0 && boss.isAlive()); serials.put(boss, serial);
                }
            }
            super.render(boss, yaw, partialTick, pose, buffers, light);
        }
    }
    public static final class MeteorRenderer extends EntityRenderer<SilkMeteor> {
        public MeteorRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = 0.6F; }
        @Override public ResourceLocation getTextureLocation(SilkMeteor entity) { return new ResourceLocation("minecraft", "textures/atlas/blocks.png"); }
        @Override public void render(SilkMeteor entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            pose.pushPose();
            pose.translate(0, 0.65, 0); pose.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * 3));
            pose.mulPose(Axis.ZP.rotationDegrees(25)); pose.scale(1.1F, 1.1F, 1.1F); pose.translate(-0.5, -0.5, -0.5);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.CRYING_OBSIDIAN.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose(); super.render(entity, yaw, partialTick, pose, buffers, light);
        }
    }
}