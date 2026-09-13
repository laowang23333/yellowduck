package com.yourname.yellowduck.client;

import com.modularmods.mcgltf.IGltfModelReceiver;
import com.modularmods.mcgltf.RenderedGltfModel;
import com.modularmods.mcgltf.animation.AnimationModel;
import com.modularmods.mcgltf.animation.InterpolatedChannel;
import com.modularmods.mcgltf.animation.GltfAnimationCreator;
import com.modularmods.mcgltf.animation.Animation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yourname.yellowduck.entity.MountEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class MountRenderer extends EntityRenderer<MountEntity> implements IGltfModelReceiver {

    protected RenderedGltfModel renderedModel;
    protected List<List<InterpolatedChannel>> animations;

    public MountRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    // 告诉 MCglTF 模型文件的位置
    @Override
    public ResourceLocation getModelLocation() {
        return new ResourceLocation("yellowduck", "models/entity/mount.glb");
    }

    // 模型加载完成后，提取动画
    @Override
    public void onReceiveSharedModel(RenderedGltfModel model) {
        this.renderedModel = model;
        this.animations = new ArrayList<>();
        if (model.gltfModel != null && model.gltfModel.getAnimationModels() != null) {
            for (AnimationModel animationModel : model.gltfModel.getAnimationModels()) {
                this.animations.add(GltfAnimationCreator.createGltfAnimation(animationModel));
            }
        }
    }

    @Override
    public void render(MountEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {

        if (renderedModel == null || renderedModel.renderedGltfScenes.isEmpty()) return;

        // 获取世界时间，驱动动画
        float time = Animation.getWorldTime(entity.level(), partialTicks);
        if (animations != null) {
            for (List<InterpolatedChannel> animation : animations) {
                animation.parallelStream().forEach(channel -> {
                    float[] keys = channel.getKeys();
                    channel.update(time % keys[keys.length - 1]);
                });
            }
        }

        poseStack.pushPose();

        // 应用实体朝向（让模型跟随身体旋转）
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - entityYaw));

        // 如果你的模型比例不对，可以在这里调整
        // poseStack.scale(1.0F, 1.0F, 1.0F);

        // 渲染模型（第一个场景）
        renderedModel.renderedGltfScenes.get(0).render(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }
}
