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

    protected RenderedGltfModel renderedModel;

    public MountRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    // 告诉 MCglTF 模型文件在哪
    @Override
    public ResourceLocation getModelLocation() {
        return new ResourceLocation("yellowduck", "models/entity/mount.glb");
    }

    // 模型加载完成后保存引用
    @Override
    public void onReceiveSharedModel(RenderedGltfModel model) {
        this.renderedModel = model;
    }

    // EntityRenderer 必须实现的方法，返回一个贴图位置（这里随便给一个，因为我们用 glb 自带的贴图）
    @Override
    public ResourceLocation getTextureLocation(MountEntity entity) {
        return new ResourceLocation("yellowduck", "textures/entity/mount.png");
    }

    // 实际渲染
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

        // 如果你的模型比例不对，可以在这里调整：
        // poseStack.scale(1.0F, 1.0F, 1.0F);
        // poseStack.translate(0, 0, 0);

        // 调用 MCglTF 渲染（不需要参数，它自己从 PoseStack 和 RenderSystem 拿状态）
        renderedModel.renderedGltfScenes.get(0).renderForVanilla();

        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }
}
