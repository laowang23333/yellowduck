package com.yourname.yellowduck.statue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.client.gltf.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import java.util.HashMap;
import java.util.Map;

public final class ChangeStatueRenderer implements BlockEntityRenderer<ChangeStatueBlockEntity> {
    public static final ResourceLocation MODEL = modelLocation("change_statue");
    private static final float TARGET_HEIGHT = 1.80F;
    private final Map<ResourceLocation, YellowGltfModel> models = new HashMap<>();
    private final Map<ResourceLocation, Bounds> bounds = new HashMap<>();

    public ChangeStatueRenderer(BlockEntityRendererProvider.Context context) {}

    public static ResourceLocation modelLocation(String id) {
        return new ResourceLocation(YellowDuckMod.MOD_ID, "models/gltf/" + id + ".glb");
    }

    @Override public void render(ChangeStatueBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ResourceLocation location = modelLocation(ChangeStatueContent.modelId(be.getBlockState().getBlock()));
        YellowGltfModel model = models.computeIfAbsent(location, YellowGltfModelCache::getOrLoad);
        if (model == null) return;
        Bounds b = bounds.computeIfAbsent(location, ignored -> calculateBounds(model));
        if (b == null) return;
        Direction facing = be.getBlockState().getValue(ChangeStatueBlock.FACING);
        float rotation = switch (facing) { case SOUTH -> 180F; case EAST -> 270F; case WEST -> 90F; default -> 0F; };
        pose.pushPose();
        try {
            pose.translate(0.5D, 0.0D, 0.5D);
            pose.mulPose(Axis.YP.rotationDegrees(rotation));
            float scale = TARGET_HEIGHT / Math.max(1.0E-6F, b.maxY - b.minY);
            pose.scale(scale, scale, scale);
            pose.translate(-b.centerX, -b.minY, -b.centerZ);
            YellowGltfRenderUtil.renderModel(model, pose, buffers, light, 0F, null, false);
        } finally { pose.popPose(); }
    }

    static Bounds calculateBounds(YellowGltfModel model) {
        float minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY,minZ=Float.POSITIVE_INFINITY;
        float maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY,maxZ=Float.NEGATIVE_INFINITY;
        for (YellowGltfMesh mesh:model.meshes) for(int i=0;i+2<mesh.positions.length;i+=3){
            minX=Math.min(minX,mesh.positions[i]); minY=Math.min(minY,mesh.positions[i+1]); minZ=Math.min(minZ,mesh.positions[i+2]);
            maxX=Math.max(maxX,mesh.positions[i]); maxY=Math.max(maxY,mesh.positions[i+1]); maxZ=Math.max(maxZ,mesh.positions[i+2]);
        }
        if(!Float.isFinite(minX)) return null;
        return new Bounds(minX,minY,minZ,maxX,maxY,maxZ,(minX+maxX)*.5F,(minY+maxY)*.5F,(minZ+maxZ)*.5F,Math.max(maxX-minX,Math.max(maxY-minY,maxZ-minZ)));
    }
    record Bounds(float minX,float minY,float minZ,float maxX,float maxY,float maxZ,float centerX,float centerY,float centerZ,float maxExtent){}
}
