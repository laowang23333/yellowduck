package com.yourname.yellowduck.client.gltf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.cleopatra.CleopatraBoss;
import com.yourname.yellowduck.cleopatra.CleopatraEntities;
import com.yourname.yellowduck.cleopatra.CleopatraVenomSnake;
import com.yourname.yellowduck.client.ClientEntityMotionState;
import com.yourname.yellowduck.client.MountAnimationState;
import com.yourname.yellowduck.entity.AlpacaMountEntity;
import com.yourname.yellowduck.entity.BambooHorseEntity;
import com.yourname.yellowduck.entity.MountEntity;
import com.yourname.yellowduck.entity.RabbitMountEntity;
import com.yourname.yellowduck.entity.SakurawitchEntity;
import com.yourname.yellowduck.entity.ToyBearEntity;
import com.yourname.yellowduck.registry.ModEntities;
import com.yourname.yellowduck.silk.SilkContent;
import com.yourname.yellowduck.silk.SilkDarkTeddy;
import com.yourname.yellowduck.silk.SilkPlagueBear;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Runtime override that moves every animated YellowDuck GLB entity away from PolyMesh.
 *
 * <p>Old renderer classes remain in source for compatibility/reference, but these LOWEST
 * registrations replace their providers before Minecraft constructs entity renderers.
 * Static block GLBs and GeckoLib .geo.json entities are intentionally untouched: they do
 * not use the problematic animated PolyMesh entity path.</p>
 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class YellowNativeGltfAllRenderers {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD = "yellowduck";

    private YellowNativeGltfAllRenderers() {
    }

    private static ResourceLocation glb(String file) {
        return new ResourceLocation(MOD, "models/gltf/" + file + ".glb");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        // Sakura / teddy
        event.registerEntityRenderer(ModEntities.SAKURA_WITCH.get(), SakuraRenderer::new);
        event.registerEntityRenderer(ModEntities.TOY_BEAR.get(), ToyBearRenderer::new);

        // Mounts
        event.registerEntityRenderer(ModEntities.MOUNT.get(), GhostWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.ALPACA_MOUNT.get(), AlpacaRenderer::new);
        event.registerEntityRenderer(ModEntities.RABBIT_MOUNT.get(), RabbitRenderer::new);
        event.registerEntityRenderer(ModEntities.BAMBOO_HORSE_MOUNT.get(), BambooHorseRenderer::new);

        // Cleopatra and three snakes
        event.registerEntityRenderer(CleopatraEntities.BOSS.get(), CleopatraRenderer::new);
        event.registerEntityRenderer(CleopatraEntities.SNAKE_POISON.get(),
                c -> new CleopatraSnakeRenderer(c, "cleopatra_snake_poison"));
        event.registerEntityRenderer(CleopatraEntities.SNAKE_FIRE.get(),
                c -> new CleopatraSnakeRenderer(c, "cleopatra_snake_fire"));
        event.registerEntityRenderer(CleopatraEntities.SNAKE_ICE.get(),
                c -> new CleopatraSnakeRenderer(c, "cleopatra_snake_ice"));

        // Professor Silk's GLB summons. The professor itself is already handled by
        // SilkNativeGltfRenderer, so do not double-register the boss here.
        event.registerEntityRenderer(SilkContent.PLAGUE_BEAR.get(), PlagueBearRenderer::new);
        event.registerEntityRenderer(SilkContent.DARK_TEDDY.get(), DarkTeddyRenderer::new);

        LOGGER.info("[YellowDuck GLTF] all animated GLB entity providers switched to YellowDuck Native GLTF");
    }

    private static final class SakuraRenderer extends YellowNativeEntityRenderer<SakurawitchEntity> {
        private static final int MOVE_GRACE_TICKS = 4;

        SakuraRenderer(EntityRendererProvider.Context context) {
            super(context, glb("entity_boss_t2_sakurawitch"), 0.15F, 0.65F);
        }

        @Override
        protected AnimationSpec animationFor(SakurawitchEntity entity) {
            if (entity.getEntityData().get(SakurawitchEntity.IS_DYING) || !entity.isAlive()) {
                return once("Anim-1_death");
            }
            int attack = entity.getEntityData().get(SakurawitchEntity.ATTACK_INDEX);
            if (attack > 0) {
                return once("Anim-1_attack_" + String.format(java.util.Locale.ROOT, "%02d", attack));
            }
            boolean moving = ClientEntityMotionState.isMoving(
                    entity, entity.getEntityData().get(SakurawitchEntity.IS_WALKING), MOVE_GRACE_TICKS);
            return loop(moving ? "Anim-1_walk" : "Anim-1_stand");
        }
    }

    private static final class ToyBearRenderer extends YellowNativeEntityRenderer<ToyBearEntity> {
        ToyBearRenderer(EntityRendererProvider.Context context) {
            super(context, glb("entity_toy_bear"), 0.15F, 0.65F);
        }

        @Override
        protected AnimationSpec animationFor(ToyBearEntity entity) {
            if (entity.getEntityData().get(ToyBearEntity.DYING) || !entity.isAlive()) {
                return once("ToyBearDeath");
            }
            if (entity.getEntityData().get(ToyBearEntity.ATTACKING)) {
                return once("ToyBearAttack", entity.getEntityData().get(ToyBearEntity.ATTACK_SERIAL));
            }
            if (entity.getDeltaMovement().horizontalDistanceSqr() > 0.0004D) {
                return loop("ToyBearWalk");
            }
            return loop("ToyBearIdle");
        }
    }

    private static final class GhostWolfRenderer extends YellowNativeEntityRenderer<MountEntity> {
        GhostWolfRenderer(EntityRendererProvider.Context context) {
            super(context, glb("ghost_wolf_mount_final_v2"), 0.16F, 0.75F);
        }

        @Override
        protected AnimationSpec animationFor(MountEntity entity) {
            return loop(MountAnimationState.isWalking(entity) ? "run" : "idle");
        }

        @Override
        protected void beforeModelTransform(MountEntity entity, float entityYaw, float partialTick, PoseStack pose) {
            // Preserve the empirically tuned body/collision-box alignment from the old renderer.
            float renderYaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
            double yaw = Math.toRadians(renderYaw);
            double forwardX = -Math.sin(yaw);
            double forwardZ = Math.cos(yaw);
            pose.translate(-2.30D * forwardX, 0.0D, -2.30D * forwardZ);

            if (entity.isGuiPreview()) {
                pose.scale(0.50F, 0.50F, 0.50F);
                pose.translate(0.0D, 0.18D, 0.0D);
            }
        }
    }

    private static final class AlpacaRenderer extends YellowNativeEntityRenderer<AlpacaMountEntity> {
        AlpacaRenderer(EntityRendererProvider.Context context) {
            super(context, glb("alpaca_embedded"), 0.12F, 0.55F);
        }

        @Override
        protected AnimationSpec animationFor(AlpacaMountEntity entity) {
            return loop(MountAnimationState.isWalking(entity) ? "Anim-1_ride" : "Anim-1_stand");
        }
    }

    private static final class RabbitRenderer extends YellowNativeEntityRenderer<RabbitMountEntity> {
        RabbitRenderer(EntityRendererProvider.Context context) {
            super(context, glb("rabbit_mount_embedded"), 0.12F, 0.55F);
        }

        @Override
        protected AnimationSpec animationFor(RabbitMountEntity entity) {
            boolean moving = MountAnimationState.isWalking(entity);
            String animation = entity.isFlying()
                    ? (moving ? "Anim-1_fly_ride" : "Anim-1_fly_stand")
                    : (entity.isVehicle() && moving ? "Anim-1_ride" : "Anim-1_stand");
            return loop(animation);
        }
    }

    private static final class BambooHorseRenderer extends YellowNativeEntityRenderer<BambooHorseEntity> {
        BambooHorseRenderer(EntityRendererProvider.Context context) {
            super(context, glb("bamboo_horse_embedded"), 0.12F, 0.55F);
        }

        @Override
        protected AnimationSpec animationFor(BambooHorseEntity entity) {
            boolean moving = MountAnimationState.isWalking(entity);
            String animation = entity.isFlying()
                    ? (moving ? "Anim-1_fly_ride" : "Anim-1_fly_stand")
                    : (entity.isVehicle() && moving ? "Anim-1_ride" : "Anim-1_stand");
            return loop(animation);
        }
    }

    private static final class CleopatraRenderer extends YellowNativeEntityRenderer<CleopatraBoss> {
        CleopatraRenderer(EntityRendererProvider.Context context) {
            super(context, glb("cleopatra_embedded"), 0.10F, 0.85F);
        }

        @Override
        protected boolean centerLikeLegacyPolyMesh(CleopatraBoss entity) {
            // The previous renderer explicitly cancelled PolyMesh's bbox centering so the
            // model rotates around the GLB root/entity origin. Keep that behavior.
            return false;
        }

        @Override
        protected void applyModelTransform(CleopatraBoss entity, float entityYaw, float partialTick,
                                           PoseStack pose, YellowGltfModel current, Bounds currentBounds) {
            float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
            pose.scale(modelScale(), modelScale(), modelScale());
        }

        @Override
        protected AnimationSpec animationFor(CleopatraBoss entity) {
            int state = entity.getAttackState();
            int serial = entity.getEntityData().get(CleopatraBoss.CAST_SERIAL);
            return switch (state) {
                case CleopatraBoss.ANIM_ATTACK1 -> once("Attack1", serial);
                case CleopatraBoss.ANIM_ATTACK2 -> once("Attack2", serial);
                case CleopatraBoss.ANIM_ATTACK3 -> once("Attack3", serial);
                case CleopatraBoss.ANIM_DEATH -> once("Death", serial);
                default -> entity.isDeadOrDying() ? once("Death", serial) : loop("Idle");
            };
        }
    }

    private static final class CleopatraSnakeRenderer extends YellowNativeEntityRenderer<CleopatraVenomSnake> {
        CleopatraSnakeRenderer(EntityRendererProvider.Context context, String model) {
            super(context, glb(model), 0.10F, 0.70F);
        }

        @Override
        protected boolean centerLikeLegacyPolyMesh(CleopatraVenomSnake entity) {
            return false;
        }

        @Override
        protected void applyModelTransform(CleopatraVenomSnake entity, float entityYaw, float partialTick,
                                           PoseStack pose, YellowGltfModel current, Bounds currentBounds) {
            float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
            pose.scale(modelScale(), modelScale(), modelScale());
        }

        @Override
        protected AnimationSpec animationFor(CleopatraVenomSnake entity) {
            int state = entity.getAttackState();
            int serial = entity.getAnimationSerial();
            return switch (state) {
                case CleopatraVenomSnake.ANIM_APPEAR -> once("Appear", serial);
                case CleopatraVenomSnake.ANIM_ATTACK -> once("Attack", serial);
                case CleopatraVenomSnake.ANIM_DEATH -> once("Death", serial);
                default -> entity.isDeadOrDying() ? once("Death", serial) : loop("Idle");
            };
        }
    }

    private static final class PlagueBearRenderer extends YellowNativeEntityRenderer<SilkPlagueBear> {
        PlagueBearRenderer(EntityRendererProvider.Context context) {
            super(context, glb("entity_toy_bear"), 0.15F, 0.65F);
        }

        @Override
        protected AnimationSpec animationFor(SilkPlagueBear entity) {
            return loop("ToyBearIdle");
        }
    }

    private static final class DarkTeddyRenderer extends YellowNativeEntityRenderer<SilkDarkTeddy> {
        DarkTeddyRenderer(EntityRendererProvider.Context context) {
            super(context, glb("entity_toy_bear"), 0.15F, 0.65F, 0xFF555565);
        }

        @Override
        protected AnimationSpec animationFor(SilkDarkTeddy entity) {
            if (!entity.isAlive()) return once("ToyBearDeath");
            int action = entity.getEntityData().get(SilkDarkTeddy.ACTION);
            if (action != 0) {
                return once("ToyBearAttack", entity.getEntityData().get(SilkDarkTeddy.ACTION_SERIAL));
            }
            return loop(entity.getDeltaMovement().horizontalDistanceSqr() > 0.0004D
                    ? "ToyBearWalk" : "ToyBearIdle");
        }
    }
}
