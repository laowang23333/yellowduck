package com.yourname.yellowduck.client.gltf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Shared YellowDuck-native GLB entity renderer.
 *
 * <p>Models are parsed/cached once by {@link YellowGltfModelCache}; animation is sampled
 * every render frame using tick + partialTick. The default horizontal centering/grounding
 * mirrors PolyMesh's old entity placement so renderer migration does not move entities.</p>
 */
public abstract class YellowNativeEntityRenderer<T extends Entity> extends EntityRenderer<T> {
    protected static final int NO_SERIAL = Integer.MIN_VALUE;
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ResourceLocation modelLocation;
    private final float modelScale;
    private final int tintArgb;
    private final Map<T, PlaybackState> playback = new WeakHashMap<>();

    private YellowGltfModel model;
    private Bounds bounds;
    private boolean loadFailed;
    private boolean readyLogged;

    protected YellowNativeEntityRenderer(EntityRendererProvider.Context context,
                                         ResourceLocation modelLocation,
                                         float modelScale,
                                         float shadowRadius) {
        this(context, modelLocation, modelScale, shadowRadius, 0xFFFFFFFF);
    }

    protected YellowNativeEntityRenderer(EntityRendererProvider.Context context,
                                         ResourceLocation modelLocation,
                                         float modelScale,
                                         float shadowRadius,
                                         int tintArgb) {
        super(context);
        this.modelLocation = modelLocation;
        this.modelScale = modelScale;
        this.shadowRadius = shadowRadius;
        this.tintArgb = tintArgb;
    }

    /** Resolve the animation that should be displayed on this render frame. */
    protected abstract AnimationSpec animationFor(T entity);

    protected final AnimationSpec loop(String name) {
        return new AnimationSpec(name, true, NO_SERIAL);
    }

    protected final AnimationSpec once(String name) {
        return new AnimationSpec(name, false, NO_SERIAL);
    }

    protected final AnimationSpec once(String name, int serial) {
        return new AnimationSpec(name, false, serial);
    }

    /** Most migrated PolyMesh entities were horizontally centered and placed on minY=0. */
    protected boolean centerLikeLegacyPolyMesh(T entity) {
        return true;
    }

    /** Hook for old per-entity translations/scales that happened before PolyMesh rendered. */
    protected void beforeModelTransform(T entity, float entityYaw, float partialTick, PoseStack pose) {
    }

    /**
     * NetCraft-style facing plus optional legacy PolyMesh centering.
     * Cleopatra overrides centering because its old renderer intentionally cancelled it.
     */
    protected void applyModelTransform(T entity, float entityYaw, float partialTick,
                                       PoseStack pose, YellowGltfModel current, Bounds currentBounds) {
        beforeModelTransform(entity, entityYaw, partialTick, pose);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        if (centerLikeLegacyPolyMesh(entity) && currentBounds != null) {
            pose.translate(
                    -currentBounds.centerX() * modelScale,
                    -currentBounds.minY() * modelScale,
                    -currentBounds.centerZ() * modelScale
            );
        }
        pose.scale(modelScale, modelScale, modelScale);
    }

    protected final float modelScale() {
        return modelScale;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        YellowGltfModel current = model;
        return current != null && current.texture != null
                ? current.texture
                : MissingTextureAtlasSprite.getLocation();
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        if (loadFailed) {
            super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
            return;
        }

        try {
            YellowGltfModel current = model;
            if (current == null) {
                current = YellowGltfModelCache.getOrLoad(modelLocation);
                if (current == null) {
                    loadFailed = true;
                    LOGGER.error("[YellowDuck GLTF] disabling renderer because model failed to load: {}", modelLocation);
                    super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
                    return;
                }
                model = current;
                bounds = Bounds.from(current);
                if (!readyLogged) {
                    readyLogged = true;
                    LOGGER.info("[YellowDuck GLTF] native entity renderer ready: {}", modelLocation);
                }
            }

            AnimationSpec wanted = animationFor(entity);
            String animation = wanted == null ? null : wanted.name();
            boolean looping = wanted != null && wanted.looping();
            int serial = wanted == null ? NO_SERIAL : wanted.serial();

            PlaybackState state = playback.computeIfAbsent(entity, ignored -> new PlaybackState());
            boolean nameChanged = animation == null ? state.animation != null : !animation.equals(state.animation);
            boolean serialChanged = serial != NO_SERIAL && serial != state.serial;
            if (nameChanged || serialChanged) {
                state.animation = animation;
                state.serial = serial;
                state.startTick = entity.tickCount;
            }

            float animationSeconds = Math.max(0.0F,
                    (entity.tickCount - state.startTick + partialTick) / 20.0F);

            pose.pushPose();
            try {
                applyModelTransform(entity, entityYaw, partialTick, pose, current, bounds);
                YellowGltfRenderUtil.renderModel(
                        current, pose, buffers, packedLight,
                        animationSeconds, animation, looping, tintArgb
                );
            } finally {
                pose.popPose();
            }
            super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
        } catch (Throwable error) {
            loadFailed = true;
            LOGGER.error("[YellowDuck GLTF] native renderer failed for {}", modelLocation, error);
            super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
        }
    }

    protected record AnimationSpec(String name, boolean looping, int serial) {
    }

    /** Bounds are measured after YellowDuck's glTF basis conversion. */
    protected record Bounds(float minX, float minY, float minZ,
                            float maxX, float maxY, float maxZ) {
        static Bounds from(YellowGltfModel model) {
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;

            for (YellowGltfMesh mesh : model.meshes) {
                float[] positions = mesh.positions;
                for (int i = 0; i + 2 < positions.length; i += 3) {
                    float x = positions[i];
                    float y = positions[i + 1];
                    float z = positions[i + 2];
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    maxZ = Math.max(maxZ, z);
                }
            }

            if (!Float.isFinite(minX)) return new Bounds(0, 0, 0, 0, 0, 0);
            return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        float centerX() { return (minX + maxX) * 0.5F; }
        float centerZ() { return (minZ + maxZ) * 0.5F; }
    }

    private static final class PlaybackState {
        String animation;
        int serial = NO_SERIAL;
        int startTick;
    }
}
