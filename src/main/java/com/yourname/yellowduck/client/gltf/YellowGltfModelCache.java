package com.yourname.yellowduck.client.gltf;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/** Client-only cache. A GLB is parsed once instead of being rebuilt every render frame. */
public final class YellowGltfModelCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, YellowGltfModel> MODELS = new HashMap<>();

    private YellowGltfModelCache() {
    }

    public static synchronized YellowGltfModel getOrLoad(ResourceLocation modelLocation) {
        YellowGltfModel cached = MODELS.get(modelLocation);
        if (cached != null) return cached;

        try {
            Resource resource = Minecraft.getInstance().getResourceManager()
                    .getResource(modelLocation)
                    .orElseThrow(() -> new IllegalStateException("Missing GLB resource " + modelLocation));
            YellowGltfModel model;
            try (InputStream input = resource.open()) {
                model = YellowGltfLoader.load(input, modelLocation);
            }
            model.texture = uploadEmbeddedTexture(modelLocation, model.embeddedTextureBytes);
            MODELS.put(modelLocation, model);
            LOGGER.info("[YellowDuck GLTF] loaded {} ({} meshes, {} nodes, {} animations)",
                    modelLocation, model.meshes.size(), model.nodes.size(), model.animations.size());
            return model;
        } catch (Throwable error) {
            LOGGER.error("[YellowDuck GLTF] failed to load {}", modelLocation, error);
            return null;
        }
    }

    private static ResourceLocation uploadEmbeddedTexture(ResourceLocation modelLocation, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            LOGGER.warn("[YellowDuck GLTF] {} has no embedded base-color texture", modelLocation);
            return MissingTextureAtlasSprite.getLocation();
        }

        String safe = modelLocation.getPath()
                .replace('/', '_')
                .replace('.', '_');
        ResourceLocation textureLocation = new ResourceLocation(
                modelLocation.getNamespace(), "dynamic/gltf/" + safe + "_embedded");
        try {
            ByteBuffer encoded = ByteBuffer.allocateDirect(bytes.length);
            encoded.put(bytes).flip();
            NativeImage image = NativeImage.read(encoded);
            DynamicTexture texture = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(textureLocation, texture);
            return textureLocation;
        } catch (Throwable error) {
            LOGGER.error("[YellowDuck GLTF] failed to upload embedded texture for {}", modelLocation, error);
            return MissingTextureAtlasSprite.getLocation();
        }
    }

    public static synchronized void clear() {
        MODELS.clear();
    }
}
