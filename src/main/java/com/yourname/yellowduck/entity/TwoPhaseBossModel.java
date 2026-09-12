package com.yourname.yellowduck.entity;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class TwoPhaseBossModel extends GeoModel<TwoPhaseBossEntity> {
    @Override
    public ResourceLocation getModelResource(TwoPhaseBossEntity animatable) {
        if (animatable.isPhaseTwo()) {
            return new ResourceLocation("yellowduck", "geo/muscle_yellow_bird.geo.json");
        } else {
            return new ResourceLocation("yellowduck", "geo/yellow_bow_bird.geo.json");
        }
    }

    @Override
    public ResourceLocation getTextureResource(TwoPhaseBossEntity animatable) {
        if (animatable.isPhaseTwo()) {
            return new ResourceLocation("yellowduck", "textures/entity/muscle_yellow_bird.png");
        } else {
            return new ResourceLocation("yellowduck", "textures/entity/yellow_bow_bird.png");
        }
    }

    @Override
    public ResourceLocation getAnimationResource(TwoPhaseBossEntity animatable) {
        if (animatable.isPhaseTwo()) {
            return new ResourceLocation("yellowduck", "animations/muscle_yellow_bird.animation.json");
        } else {
            return new ResourceLocation("yellowduck", "animations/yellow_bow_bird.animation.json");
        }
    }
}
