package com.yourname.yellowduck.change;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.client.gltf.YellowGltfModel;
import com.yourname.yellowduck.client.gltf.YellowGltfModelCache;
import com.yourname.yellowduck.client.gltf.YellowGltfRenderUtil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

@Mod.EventBusSubscriber(modid=YellowDuckMod.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ChangeClient {
    private static ResourceLocation model(String name){ return new ResourceLocation(YellowDuckMod.MOD_ID,"models/gltf/change/"+name); }

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){
        e.registerEntityRenderer(ChangeContent.BOSS.get(), c->new BossRenderer(c,false));
        e.registerEntityRenderer(ChangeContent.CLONE.get(), c->new CloneRenderer(c));
        e.registerEntityRenderer(ChangeContent.RABBIT.get(), c->new RabbitRenderer(c));
        e.registerEntityRenderer(ChangeContent.BREW.get(), c->new BrewRenderer(c));
    }

    private abstract static class Base<T extends net.minecraft.world.entity.Entity> extends EntityRenderer<T>{
        final YellowGltfModel model; final float scale;
        Base(EntityRendererProvider.Context c,String file,float scale,float shadow){ super(c); this.scale=scale; shadowRadius=shadow; model=YellowGltfModelCache.getOrLoad(model(file)); }
        @Override public ResourceLocation getTextureLocation(T e){ return model!=null&&model.texture!=null?model.texture:MissingTextureAtlasSprite.getLocation(); }
        void draw(T e,float yaw,float partial,PoseStack pose,MultiBufferSource buf,int light,float sec){
            if(model==null)return; pose.pushPose(); pose.mulPose(new Quaternionf().rotationY((float)Math.toRadians(180-yaw))); pose.scale(scale,scale,scale);
            YellowGltfRenderUtil.renderModel(model,pose,buf,light,sec,"Anim-1",true,0xFFFFFFFF); pose.popPose();
        }
    }

    private static final class BossRenderer extends Base<ChangeBoss>{
        BossRenderer(EntityRendererProvider.Context c,boolean ignored){ super(c,"boss_elite_chang_e.glb",0.065F,0.75F); }
        @Override public void render(ChangeBoss e,float y,float p,PoseStack s,MultiBufferSource b,int l){
            float sec=frameTime(e.action(),e.tickCount+p); draw(e,y,p,s,b,l,sec); super.render(e,y,p,s,b,l);
        }
    }
    private static final class CloneRenderer extends Base<ChangeClone>{
        CloneRenderer(EntityRendererProvider.Context c){ super(c,"boss_elite_chang_e.glb",0.065F,0.75F); }
        @Override public void render(ChangeClone e,float y,float p,PoseStack s,MultiBufferSource b,int l){
            draw(e,y,p,s,b,l,(e.attacking()?57F:(e.getDeltaMovement().horizontalDistanceSqr()>0.002?21F:0F))/30F+(e.tickCount%20)/30F);
            super.render(e,y,p,s,b,l);
        }
    }
    private static final class RabbitRenderer extends Base<ChangeRabbit>{
        RabbitRenderer(EntityRendererProvider.Context c){ super(c,"change_rabbit.glb",0.065F,0.45F); }
        @Override public void render(ChangeRabbit e,float y,float p,PoseStack s,MultiBufferSource b,int l){
            int f=e.action()==2?48:e.action()==1?20:0; draw(e,y,p,s,b,l,f/30F+(e.tickCount%18)/30F); super.render(e,y,p,s,b,l);
        }
    }
    private static final class BrewRenderer extends Base<GuiHuaNiangEntity>{
        BrewRenderer(EntityRendererProvider.Context c){ super(c,"elite_pool_purple.glb",0.065F,0.1F); }
        @Override public void render(GuiHuaNiangEntity e,float y,float p,PoseStack s,MultiBufferSource b,int l){ draw(e,y,p,s,b,l,(e.tickCount+p)/20F); super.render(e,y,p,s,b,l); }
    }
    private static float frameTime(int action,float ticks){
        int start=switch(action){case ChangeBoss.WALK->21;case ChangeBoss.ATTACK->57;case ChangeBoss.SKILL->79;case ChangeBoss.SUMMON->106;case ChangeBoss.RAGE->127;default->0;};
        int end=switch(action){case ChangeBoss.WALK->56;case ChangeBoss.ATTACK->78;case ChangeBoss.SKILL->105;case ChangeBoss.SUMMON->126;case ChangeBoss.RAGE->150;default->20;};
        float len=Math.max(1,end-start); return (start+(ticks%len))/30F;
    }
}
