package com.yourname.yellowduck.client.gltf;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Parsed GLB model plus the runtime texture created from its embedded image. */
public final class YellowGltfModel {
    public final List<YellowGltfMesh> meshes = new ArrayList<>();
    public final List<YellowGltfNode> nodes = new ArrayList<>();
    public final List<YellowGltfNode> rootNodes = new ArrayList<>();
    public final Map<String, YellowGltfNode> nodeByName = new HashMap<>();
    public final List<YellowGltfAnimation> animations = new ArrayList<>();
    public final Map<String, YellowGltfAnimation> animationByName = new HashMap<>();

    public ResourceLocation source;
    public ResourceLocation texture;
    public byte[] embeddedTextureBytes;
}
