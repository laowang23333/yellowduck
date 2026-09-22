package com.yourname.yellowduck.client.gltf;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** One glTF scene node. */
public final class YellowGltfNode {
    public final int index;
    public final String name;
    public final Matrix4f localTransform;
    public final Vector3f baseTranslation;
    public final Quaternionf baseRotation;
    public final Vector3f baseScale;
    public final List<YellowGltfNode> children = new ArrayList<>();
    public final List<Integer> meshIndices = new ArrayList<>();
    public YellowGltfNode parent;
    public int skinIndex = -1;

    public YellowGltfNode(int index,
                          String name,
                          Matrix4f localTransform,
                          Vector3f baseTranslation,
                          Quaternionf baseRotation,
                          Vector3f baseScale) {
        this.index = index;
        this.name = name;
        this.localTransform = localTransform;
        this.baseTranslation = baseTranslation;
        this.baseRotation = baseRotation;
        this.baseScale = baseScale;
    }
}
