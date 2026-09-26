package com.yourname.yellowduck.change.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yourname.yellowduck.change.ChangeEffectEntity;
import com.yourname.yellowduck.client.gltf.YellowGltfAnimation;
import com.yourname.yellowduck.client.gltf.YellowGltfModel;
import com.yourname.yellowduck.client.gltf.YellowGltfModelCache;
import com.yourname.yellowduck.client.gltf.YellowGltfRenderUtil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public final class ChangeEffectRenderer extends EntityRenderer<ChangeEffectEntity> {
    private final Map<ResourceLocation, YellowGltfModel> models = new HashMap<>();

    public ChangeEffectRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(
            ChangeEffectEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack pose,
            MultiBufferSource buffers,
            int packedLight
    ) {
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
        if (!entity.renderVisible()) return;

        ResourceLocation location = entity.effectModelPath();
        YellowGltfModel model = models.computeIfAbsent(
                location,
                YellowGltfModelCache::getOrLoad
        );
        if (model == null || model.meshes.isEmpty()) return;

        boolean loop = entity.effectLoop();
        float seconds = (entity.effectAgeTicks() + partialTick) / 20.0F;
        String animation = null;

        if (!model.animations.isEmpty()) {
            YellowGltfAnimation first = model.animations.get(0);
            animation = first.name;
            if (!loop && first.duration > 0.0F) {
                seconds = Math.min(seconds, first.duration);
            }
        }

        pose.pushPose();
        try {
            if (entity.isMark()) {
                pose.mulPose(entityRenderDispatcher.cameraOrientation());
                float bob = (float) Math.sin((entity.tickCount + partialTick) / 10.0D) * 0.1F;
                pose.translate(0.0F, bob, 0.0F);
            } else if (entity.followsHostYaw()) {
                pose.mulPose(Axis.YP.rotationDegrees(-entity.hostYawDegrees()));
            }

            float scale = entity.effectScale();
            pose.scale(scale, scale, scale);
            YellowGltfRenderUtil.renderModel(
                    model,
                    pose,
                    buffers,
                    packedLight,
                    seconds,
                    animation,
                    loop,
                    0xFFFFFFFF
            );
        } finally {
            pose.popPose();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(ChangeEffectEntity entity) {
        YellowGltfModel model = models.get(entity.effectModelPath());
        return model != null && model.texture != null
                ? model.texture
                : MissingTextureAtlasSprite.getLocation();
    }
}
