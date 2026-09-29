package com.yourname.yellowduck.daji;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.client.gltf.YellowGltfAnimationPlayer;
import com.yourname.yellowduck.client.gltf.YellowGltfModel;
import com.yourname.yellowduck.client.gltf.YellowGltfNode;
import com.yourname.yellowduck.client.gltf.YellowGltfModelCache;
import com.yourname.yellowduck.client.gltf.YellowGltfRenderUtil;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 妲己本体/主要技能使用 Native GLTF，原技能粒子图使用全亮透明精灵表现。 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DajiClient {
    private static final float GLB_TIMELINE_FPS = 15.0F;

    // 人形头部资源以头部挂点为局部原点导出，需要放回身体颈部位置。
    private static final double HUMAN_HEAD_X = 0.0D;
    private static final double HUMAN_HEAD_Y = 10.324472D;
    private static final double HUMAN_HEAD_Z = 0.119830D;

    private DajiClient() {}

    private static ResourceLocation model(String name){
        return new ResourceLocation(YellowDuckMod.MOD_ID,"models/gltf/daji/"+name+".glb");
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event){
        event.registerEntityRenderer(DajiContent.BOSS.get(), BossRenderer::new);
        event.registerEntityRenderer(DajiContent.FOX.get(), FoxRenderer::new);
        event.registerEntityRenderer(DajiContent.EFFECT.get(), EffectRenderer::new);
    }

    private static final class BossRenderer extends EntityRenderer<DajiBoss>{
        private final YellowGltfModel[] human = new YellowGltfModel[4];
        // 客户端 NPC 表明确 2138/2140 两个 Boss 狐形都使用 01_01 红狐贴图；青/白贴图只属于召唤精英狐。
        private final YellowGltfModel[] bossFox = new YellowGltfModel[3];
        private final YellowGltfModel shield;
        private final YellowGltfModel redPower;
        private final Map<UUID, Clock> clocks = new HashMap<>();

        BossRenderer(EntityRendererProvider.Context c){
            super(c);
            shadowRadius = 0.85F;
            human[0]=load("daji_human_body");
            human[1]=load("daji_human_head");
            human[2]=load("daji_human_fan");
            human[3]=load("daji_human_tail");
            bossFox[0]=load("daji_fox_body_red");
            bossFox[1]=load("daji_fox_wing_red");
            bossFox[2]=load("daji_fox_tail_red");
            shield=load("daji_shield");
            redPower=load("daji_red_power");
        }

        @Override public ResourceLocation getTextureLocation(DajiBoss e){
            YellowGltfModel m = e.form()==DajiBoss.FORM_HUMAN ? human[0] : bossFox[0];
            return m!=null&&m.texture!=null ? m.texture : MissingTextureAtlasSprite.getLocation();
        }

        @Override public void render(DajiBoss e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
            float now = e.tickCount + partial;
            Clock clock = clocks.get(e.getUUID());
            int serial = e.actionSerial();
            if(clock==null || clock.serial!=serial){
                clock=new Clock(serial,now);
                clocks.put(e.getUUID(),clock);
            }
            float actionSeconds = Math.max(0.0F,(now-clock.startTick)/20.0F);

            pose.pushPose();
            if(e.form()==DajiBoss.FORM_HUMAN){
                pose.mulPose(Axis.YP.rotationDegrees(-yaw));
                pose.scale(0.22F,0.22F,0.22F);
                float sample=humanSample(e,actionSeconds,now);

                // 身体、扇子和尾巴本身已经在角色坐标系中。
                if(human[0]!=null) YellowGltfRenderUtil.renderModel(human[0],pose,buffers,light,sample,"Anim-1",false);
                if(human[2]!=null) YellowGltfRenderUtil.renderModel(human[2],pose,buffers,light,sample,"Anim-1",false);
                if(human[3]!=null) YellowGltfRenderUtil.renderModel(human[3],pose,buffers,light,sample,"Anim-1",false);

                // 头部模型使用独立骨架导出。位置不能只做固定平移，
                // 必须跟随身体 Bip001_Neck 的当前动画矩阵，否则身体动作时头会悬空不动。
                if(human[1]!=null){
                    pose.pushPose();
                    Matrix4f neckDelta = animatedNodeDelta(human[0], "Bip001_Neck", sample);
                    if(neckDelta!=null){
                        // 只应用颈部相对静止姿势的动画变化。
                        // 不能直接乘完整颈部矩阵，否则会把模型导出时的轴向旋转再套一次，头就会侧歪。
                        pose.mulPoseMatrix(neckDelta);
                    }
                    pose.translate(HUMAN_HEAD_X,HUMAN_HEAD_Y,HUMAN_HEAD_Z);
                    YellowGltfRenderUtil.renderModel(human[1],pose,buffers,light,sample,"Anim-1",false);
                    pose.popPose();
                }
            }else{
                // 狐形资源自身正面与人形资源相反，不再额外旋转 180°。
                pose.mulPose(Axis.YP.rotationDegrees(-yaw));
                pose.scale(0.10F,0.10F,0.10F);
                float sample=foxSample(e,actionSeconds,now);
                for(YellowGltfModel m:bossFox){
                    if(m!=null) YellowGltfRenderUtil.renderModel(m,pose,buffers,light,sample,"Anim-1",false);
                }
                // SC 表现资源：护灵镜与赤狐之力都是跟随狐形本体的独立模型。
                if(e.shield()>0.0F && shield!=null){
                    YellowGltfRenderUtil.renderModel(shield,pose,buffers,light,0.0F,null,false,0xCCFFFFFF);
                }
                if(e.redPowerActive() && redPower!=null){
                    YellowGltfRenderUtil.renderModel(redPower,pose,buffers,light,0.0F,null,false,0xDFFFFFFF);
                }
            }
            pose.popPose();
            super.render(e,yaw,partial,pose,buffers,light);
        }

        private static float humanSample(DajiBoss e,float actionAge,float nowTicks){
            return switch(e.action()){
                case DajiBoss.ACT_HUMAN_BASIC -> segment(1181,1196,15,actionAge,false);
                case DajiBoss.ACT_WIND -> segment(1197,1214,15,actionAge,false);
                case DajiBoss.ACT_RAGE -> segment(139,191,15,actionAge,false);
                case DajiBoss.ACT_WALK -> segment(69,85,15,nowTicks/20.0F,true);
                default -> segment(46,66,15,nowTicks/20.0F,true);
            };
        }

        private static float foxSample(DajiBoss e,float actionAge,float nowTicks){
            return switch(e.action()){
                case DajiBoss.ACT_FOX_BASIC -> (e.actionSerial()&1)==0
                        ? segment(111,134,15,actionAge,false)
                        : segment(137,156,15,actionAge,false);
                case DajiBoss.ACT_CHARGE_PREP -> segment(80,96,25,actionAge,true);
                case DajiBoss.ACT_CHARGE -> segment(53,71,15,actionAge,false);
                case DajiBoss.ACT_RED_POWER, DajiBoss.ACT_SHIELD -> segment(184,217,15,actionAge,false);
                case DajiBoss.ACT_FIRE -> segment(159,181,15,actionAge,false);
                case DajiBoss.ACT_WALK -> segment(23,39,15,nowTicks/20.0F,true);
                default -> segment(0,20,15,nowTicks/20.0F,true);
            };
        }
    }

    private static final class FoxRenderer extends EntityRenderer<DajiFoxMinion>{
        private final YellowGltfModel small=load("daji_small_fox");
        private final YellowGltfModel[][] elite=new YellowGltfModel[2][3];
        private final Map<UUID,Clock> attacks=new HashMap<>();

        FoxRenderer(EntityRendererProvider.Context c){
            super(c);
            shadowRadius=.45F;
            String[] colors={"blue","white"};
            for(int i=0;i<2;i++){
                elite[i][0]=load("daji_fox_body_"+colors[i]);
                elite[i][1]=load("daji_fox_wing_"+colors[i]);
                elite[i][2]=load("daji_fox_tail_"+colors[i]);
            }
        }

        @Override public ResourceLocation getTextureLocation(DajiFoxMinion e){
            YellowGltfModel m=e.variant()==DajiFoxMinion.SMALL
                    ? small : elite[e.variant()==DajiFoxMinion.BLUE?0:1][0];
            return m!=null&&m.texture!=null ? m.texture : MissingTextureAtlasSprite.getLocation();
        }

        @Override public void render(DajiFoxMinion e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
            int serial=e.getEntityData().get(DajiFoxMinion.ATTACK_SERIAL);
            float now=e.tickCount+partial;
            Clock c=attacks.get(e.getUUID());
            if(c==null||c.serial!=serial){
                c=new Clock(serial,now);
                attacks.put(e.getUUID(),c);
            }
            float since=(now-c.startTick)/20.0F;
            boolean attacking=serial>0&&since<1.60F;
            boolean moving=e.getDeltaMovement().horizontalDistanceSqr()>0.002D;

            pose.pushPose();
            // 小狐与精英狐资源正面和原版实体朝向相反，去掉额外的 180° 翻转。
            pose.mulPose(Axis.YP.rotationDegrees(-yaw));
            if(e.variant()==DajiFoxMinion.SMALL){
                pose.scale(.050F,.050F,.050F);
                float s=attacking ? segment(68,90,15,since,false)
                        : moving ? segment(51,67,15,now/20.0F,true)
                        : segment(0,16,15,now/20.0F,true);
                if(small!=null) YellowGltfRenderUtil.renderModel(small,pose,buffers,light,s,"Anim-1",false);
            }else{
                pose.scale(.080F,.080F,.080F);
                float s=attacking ? segment(111,134,15,since,false)
                        : moving ? segment(42,50,15,now/20.0F,true)
                        : segment(0,20,15,now/20.0F,true);
                for(YellowGltfModel m:elite[e.variant()==DajiFoxMinion.BLUE?0:1]){
                    if(m!=null) YellowGltfRenderUtil.renderModel(m,pose,buffers,light,s,"Anim-1",false);
                }
            }
            pose.popPose();
            super.render(e,yaw,partial,pose,buffers,light);
        }
    }

    private static final class EffectRenderer extends EntityRenderer<DajiEffectEntity>{
        private final YellowGltfModel wind=load("daji_skill_fly_01");
        private final YellowGltfModel lightning=load("daji_skill_fly_02");
        private final YellowGltfModel spiral=load("daji_skill_xhh_01");

        EffectRenderer(EntityRendererProvider.Context c){
            super(c);
            shadowRadius=0.0F;
        }

        @Override public ResourceLocation getTextureLocation(DajiEffectEntity e){
            YellowGltfModel m=modelFor(e);
            if(m!=null&&m.texture!=null) return m.texture;
            ResourceLocation sprite=spriteTexture(e.variant());
            return sprite!=null ? sprite : MissingTextureAtlasSprite.getLocation();
        }

        @Override public void render(DajiEffectEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
            YellowGltfModel m=modelFor(e);
            if(m!=null){
                float age=(e.localAge()+partial)/20.0F;
                float sample;
                float scale;
                if(e.variant()==DajiEffectEntity.WIND){
                    sample=segment(0,32,15,age,false);
                    scale=.10F;
                }else if(e.variant()==DajiEffectEntity.LIGHTNING){
                    sample=segment(0,32,15,age,false);
                    scale=.10F;
                }else{
                    sample=segment(0,8,15,age,true);
                    scale=.065F;
                }
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(180.0F-yaw));
                pose.scale(scale,scale,scale);
                YellowGltfRenderUtil.renderModel(m,pose,buffers,light,sample,"Anim-1",false,0xEFFFFFFF);
                pose.popPose();
            }else{
                renderSpriteEffect(e,partial,pose,buffers);
            }
            super.render(e,yaw,partial,pose,buffers,light);
        }

        private void renderSpriteEffect(DajiEffectEntity e,float partial,PoseStack pose,MultiBufferSource buffers){
            float age=(e.localAge()+partial)/20.0F;
            switch(e.variant()){
                case DajiEffectEntity.WIND_BURST ->
                        billboard(pose,buffers,tex("daji_skill_fly_03"),age,2,2,16.0F,2.15F,2.15F,0.65F,255,0.0F);
                case DajiEffectEntity.FIRE -> {
                    billboard(pose,buffers,tex("daji_skill_xhh_05"),age,1,1,1.0F,2.25F,2.25F,0.32F,220,age*32.0F);
                    billboard(pose,buffers,tex("daji_skill_xhh_04"),age,1,1,1.0F,2.70F,2.70F,0.20F,210,-age*44.0F);
                    billboard(pose,buffers,tex("daji_skill_xhh_03"),age,1,4,15.0F,1.75F,1.75F,0.72F,245,0.0F);
                    billboard(pose,buffers,tex("daji_skill_xhh_02"),age,2,2,4.0F,1.10F,1.10F,1.20F,235,age*90.0F);
                }
                case DajiEffectEntity.SHIELD -> {
                    // 护灵镜主体仍由 daji_shield.glb 表现，这里补回原 hlj_04 粒子图。
                    billboard(pose,buffers,tex("daji_skill_hlj_02"),age,2,2,15.0F,1.45F,1.45F,1.45F,235,-age*55.0F);
                    billboard(pose,buffers,tex("daji_skill_hlj_02"),age+0.08F,2,2,15.0F,1.05F,1.05F,2.05F,205,age*70.0F);
                }
                case DajiEffectEntity.TRANSFORM_TS ->
                        billboard(pose,buffers,tex("daji_skill_ts_effect"),age,1,4,15.0F,2.25F,3.10F,1.20F,245,0.0F);
                case DajiEffectEntity.TRANSFORM_QQ ->
                        billboard(pose,buffers,tex("daji_skill_qq_effect"),age,1,4,15.0F,2.25F,3.10F,1.20F,245,0.0F);
                case DajiEffectEntity.TRANSFORM_YS -> {
                    billboard(pose,buffers,tex("daji_skill_ys_effect"),age,1,4,15.0F,2.25F,3.10F,1.20F,245,0.0F);
                    billboard(pose,buffers,tex("daji_skill_ys_02"),age,2,2,4.0F,1.25F,1.25F,1.65F,235,age*70.0F);
                }
                case DajiEffectEntity.AURA_TS ->
                        billboard(pose,buffers,tex("daji_skill_ts_effect"),age,1,4,15.0F,1.60F,2.40F,1.10F,205,0.0F);
                case DajiEffectEntity.AURA_QQ ->
                        billboard(pose,buffers,tex("daji_skill_qq_effect"),age,1,4,15.0F,1.60F,2.40F,1.10F,205,0.0F);
                case DajiEffectEntity.AURA_YS ->
                        billboard(pose,buffers,tex("daji_skill_ys_effect"),age,1,4,15.0F,1.60F,2.40F,1.10F,205,0.0F);
                case DajiEffectEntity.RELEASE ->
                        billboard(pose,buffers,tex("daji_skill_release_henshin"),age,1,8,8.0F,2.75F,2.75F,1.20F,245,0.0F);
                default -> { }
            }
        }

        private void billboard(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture,
                               float ageSeconds, int columns, int rows, float fps,
                               float width, float height, float yOffset, int alpha, float spinDegrees){
            int total=Math.max(1,columns*rows);
            int frame=total==1 ? 0 : ((int)Math.floor(ageSeconds*fps))%total;
            int col=frame%columns;
            int row=frame/columns;
            float u0=(float)col/columns;
            float u1=(float)(col+1)/columns;
            float v0=(float)row/rows;
            float v1=(float)(row+1)/rows;

            pose.pushPose();
            pose.translate(0.0D,yOffset,0.0D);
            pose.mulPose(entityRenderDispatcher.cameraOrientation());
            pose.mulPose(Axis.ZP.rotationDegrees(spinDegrees));
            PoseStack.Pose last=pose.last();
            VertexConsumer vc=buffers.getBuffer(RenderType.entityTranslucent(texture));
            float hw=width*0.5F;
            float hh=height*0.5F;
            vertex(vc,last,-hw,-hh,0.0F,u0,v1,alpha);
            vertex(vc,last, hw,-hh,0.0F,u1,v1,alpha);
            vertex(vc,last, hw, hh,0.0F,u1,v0,alpha);
            vertex(vc,last,-hw, hh,0.0F,u0,v0,alpha);
            pose.popPose();
        }

        private void vertex(VertexConsumer vc, PoseStack.Pose pose,
                            float x,float y,float z,float u,float v,int alpha){
            vc.vertex(pose.pose(),x,y,z)
                    .color(255,255,255,alpha)
                    .uv(u,v)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT)
                    .normal(pose.normal(),0.0F,0.0F,1.0F)
                    .endVertex();
        }

        private YellowGltfModel modelFor(DajiEffectEntity e){
            if(e.variant()==DajiEffectEntity.WIND) return wind;
            if(e.variant()==DajiEffectEntity.LIGHTNING) return lightning;
            if(e.variant()==DajiEffectEntity.SPIRAL) return spiral;
            return null;
        }

        private ResourceLocation spriteTexture(int variant){
            return switch(variant){
                case DajiEffectEntity.WIND_BURST -> tex("daji_skill_fly_03");
                case DajiEffectEntity.FIRE -> tex("daji_skill_xhh_05");
                case DajiEffectEntity.SHIELD -> tex("daji_skill_hlj_02");
                case DajiEffectEntity.TRANSFORM_TS -> tex("daji_skill_ts_effect");
                case DajiEffectEntity.TRANSFORM_QQ -> tex("daji_skill_qq_effect");
                case DajiEffectEntity.TRANSFORM_YS -> tex("daji_skill_ys_effect");
                case DajiEffectEntity.AURA_TS -> tex("daji_skill_ts_effect");
                case DajiEffectEntity.AURA_QQ -> tex("daji_skill_qq_effect");
                case DajiEffectEntity.AURA_YS -> tex("daji_skill_ys_effect");
                case DajiEffectEntity.RELEASE -> tex("daji_skill_release_henshin");
                default -> null;
            };
        }
    }

    /** 返回指定节点相对静止姿势的动画增量矩阵。 */
    private static Matrix4f animatedNodeDelta(YellowGltfModel model, String nodeName, float seconds){
        if(model==null) return null;
        YellowGltfNode target=model.nodeByName.get(nodeName);
        if(target==null) return null;

        Map<Integer,Matrix4f> sampled = YellowGltfAnimationPlayer.sampleAnimation(
                model,"Anim-1",seconds,false);
        Matrix4f animated = nodeGlobal(target, sampled);
        Matrix4f rest = nodeGlobal(target, Map.of());
        if(animated==null || rest==null) return null;

        Matrix4f inverseRest = new Matrix4f(rest).invert();
        return new Matrix4f(animated).mul(inverseRest);
    }

    private static Matrix4f nodeGlobal(YellowGltfNode target, Map<Integer,Matrix4f> sampled){
        java.util.ArrayList<YellowGltfNode> chain=new java.util.ArrayList<>();
        for(YellowGltfNode n=target;n!=null;n=n.parent) chain.add(n);

        Matrix4f global=new Matrix4f();
        for(int i=chain.size()-1;i>=0;i--){
            YellowGltfNode n=chain.get(i);
            Matrix4f local=sampled.get(n.index);
            global.mul(local!=null ? local : n.localTransform);
        }
        return global;
    }

    private static ResourceLocation tex(String name){
        return new ResourceLocation(YellowDuckMod.MOD_ID,"textures/effect/daji/"+name+".png");
    }

    private static YellowGltfModel load(String name){
        return YellowGltfModelCache.getOrLoad(model(name));
    }

    /** GLB 统一按 15fps 时间轴导出；sourceFps 来自客户端动作表。 */
    private static float segment(int start,int end,float sourceFps,float seconds,boolean loop){
        float len=Math.max(1,end-start);
        float frames=seconds*sourceFps;
        if(loop) frames%=len;
        else frames=Math.min(len,Math.max(0,frames));
        return (start+frames)/GLB_TIMELINE_FPS;
    }

    private record Clock(int serial,float startTick){}
}
