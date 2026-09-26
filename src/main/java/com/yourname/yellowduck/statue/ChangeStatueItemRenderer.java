package com.yourname.yellowduck.statue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yourname.yellowduck.client.gltf.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import java.util.HashMap;
import java.util.Map;

public final class ChangeStatueItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static ChangeStatueItemRenderer instance;
    private final Map<ResourceLocation, YellowGltfModel> models = new HashMap<>();
    private final Map<ResourceLocation, ChangeStatueRenderer.Bounds> bounds = new HashMap<>();
    private ChangeStatueItemRenderer(){ super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels()); }
    public static ChangeStatueItemRenderer getInstance(){ if(instance==null) instance=new ChangeStatueItemRenderer(); return instance; }

    @Override public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers, int light, int overlay){
        if (!(stack.getItem() instanceof BlockItem bi)) return;
        ResourceLocation loc=ChangeStatueRenderer.modelLocation(ChangeStatueContent.modelId(bi.getBlock()));
        YellowGltfModel model=models.computeIfAbsent(loc, YellowGltfModelCache::getOrLoad); if(model==null)return;
        ChangeStatueRenderer.Bounds b=bounds.computeIfAbsent(loc, ignored->ChangeStatueRenderer.calculateBounds(model)); if(b==null)return;
        int renderLight=switch(ctx){case GUI,GROUND,FIXED->LightTexture.FULL_BRIGHT;default->light;};
        pose.pushPose();
        try{
            pose.translate(.5D,.5D,.5D);
            if(ctx==ItemDisplayContext.GUI){pose.mulPose(Axis.XP.rotationDegrees(18F));pose.mulPose(Axis.YP.rotationDegrees(205F));}
            else pose.mulPose(Axis.YP.rotationDegrees(180F));
            float visual=switch(ctx){case GUI->.95F;case GROUND->.58F;case FIXED->.82F;case FIRST_PERSON_LEFT_HAND,FIRST_PERSON_RIGHT_HAND->.72F;case THIRD_PERSON_LEFT_HAND,THIRD_PERSON_RIGHT_HAND->.66F;case HEAD->.72F;default->.78F;};
            float fit=visual/Math.max(1.0E-6F,b.maxExtent()); pose.scale(fit,fit,fit); pose.translate(-b.centerX(),-b.centerY(),-b.centerZ());
            YellowGltfRenderUtil.renderModel(model,pose,buffers,renderLight,0F,null,false);
        }finally{pose.popPose();}
    }
}
