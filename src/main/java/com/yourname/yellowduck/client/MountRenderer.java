package com.yourname.yellowduck.client;

import com.modularmods.mcgltf.IGltfModelReceiver;
import com.modularmods.mcgltf.RenderedGltfModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yourname.yellowduck.entity.MountEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MountRenderer extends EntityRenderer<MountEntity> implements IGltfModelReceiver {

    // ============================================================
    // 🎚️ 调试用缩放值：如果模型看不见，就改这个数字！
    // 常用参考值：
    //   1.0    = 原尺寸
    //   0.1    = 缩小 10 倍
    //   0.01   = 缩小 100 倍
    //   10.0   = 放大 10 倍
    //   100.0  = 放大 100 倍
    //   0.001  = 缩小 1000 倍
    // ============================================================
    private static final float MODEL_SCALE = 0.05F;

    protected RenderedGltfModel renderedModel;

    public MountRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getModelLocation() {
        return new ResourceLocation("yellowduck", "models/entity/mount.glb");
    }

    @Override
    public void onReceiveSharedModel(RenderedGltfModel model) {
        this.renderedModel = model;
    }

    @Override
    public ResourceLocation getTextureLocation(MountEntity entity) {
        return new ResourceLocation("yellowduck", "textures/entity/mount.png");
    }

    @Override
    public void render(MountEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {

        if (renderedModel == null || renderedModel.renderedGltfScenes.isEmpty()) {
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
            return;
        }

        poseStack.pushPose();

        // 让模型跟随身体朝向
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - entityYaw));

        // 应用缩放
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);

        // 如果需要调整模型上下/前后位置，可以改这里：
        // poseStack.translate(0, 0.5, 0);

        renderedModel.renderedGltfScenes.get(0).renderForVanilla();

        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }
}
