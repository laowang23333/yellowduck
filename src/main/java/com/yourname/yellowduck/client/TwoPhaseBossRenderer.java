package com.yourname.yellowduck.client;

import com.yourname.yellowduck.entity.TwoPhaseBossEntity;
import com.yourname.yellowduck.entity.TwoPhaseBossModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class TwoPhaseBossRenderer extends GeoEntityRenderer<TwoPhaseBossEntity> {
    public TwoPhaseBossRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new TwoPhaseBossModel());
    }
}
