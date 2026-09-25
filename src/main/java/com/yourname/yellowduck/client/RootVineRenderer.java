package com.yourname.yellowduck.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.yourname.yellowduck.entity.RootVineEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public class RootVineRenderer extends EntityRenderer<RootVineEntity> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("minecraft", "textures/block/vine.png");

    public RootVineRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(RootVineEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        float age = entity.tickCount + partialTick;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 2.0F));

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(TEXTURE));
        Matrix4f pose = poseStack.last().pose();

        final float radius = 0.90F;
        final float height = 2.20F;
        final float spread = 0.30F;

        for (int i = 0; i < 4; i++) {
            float angle = (float) (i * Math.PI / 2.0D);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            float x1 = cos * radius;
            float z1 = sin * radius;
            float x2 = cos * radius - sin * spread;
            float z2 = sin * radius + cos * spread;

            vertex(consumer, pose, x1, 0.0F, z1, 0.0F, 1.0F, cos, sin, 0.20F, 0.60F, 0.20F, 0.90F);
            vertex(consumer, pose, x2, 0.0F, z2, 1.0F, 1.0F, cos, sin, 0.20F, 0.60F, 0.20F, 0.90F);
            vertex(consumer, pose, x2, height, z2, 1.0F, 0.0F, cos, sin, 0.30F, 0.80F, 0.30F, 0.90F);
            vertex(consumer, pose, x1, height, z1, 0.0F, 0.0F, cos, sin, 0.30F, 0.80F, 0.30F, 0.90F);
        }

        poseStack.popPose();
    }

    private static void vertex(VertexConsumer consumer,
                               Matrix4f pose,
                               float x, float y, float z,
                               float u, float v,
                               float normalX, float normalZ,
                               float red, float green, float blue, float alpha) {
        consumer.vertex(pose, x, y, z)
                .color((int) (red * 255.0F), (int) (green * 255.0F),
                        (int) (blue * 255.0F), (int) (alpha * 255.0F))
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(15728880)
                .normal(normalX, 0.0F, normalZ)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(RootVineEntity entity) {
        return TEXTURE;
    }
}
