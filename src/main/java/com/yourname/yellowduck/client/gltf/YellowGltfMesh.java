package com.yourname.yellowduck.client.gltf;

import org.joml.Matrix4f;

import java.util.List;

/** A single triangle primitive from a glTF mesh. */
public final class YellowGltfMesh {
    public final float[] positions;
    public final float[] normals;
    public final float[] uvs;
    public final int[] indices;
    public final int vertexCount;
    public final List<String> jointNames;
    public final Matrix4f[] inverseBindMatrices;
    public final int[] jointIndices;
    public final float[] jointWeights;

    public YellowGltfMesh(float[] positions,
                          float[] normals,
                          float[] uvs,
                          int[] indices,
                          int vertexCount,
                          List<String> jointNames,
                          Matrix4f[] inverseBindMatrices,
                          int[] jointIndices,
                          float[] jointWeights) {
        this.positions = positions;
        this.normals = normals;
        this.uvs = uvs;
        this.indices = indices;
        this.vertexCount = vertexCount;
        this.jointNames = jointNames;
        this.inverseBindMatrices = inverseBindMatrices;
        this.jointIndices = jointIndices;
        this.jointWeights = jointWeights;
    }

    public boolean hasSkinning() {
        return jointNames != null && !jointNames.isEmpty()
                && jointIndices != null && jointWeights != null;
    }
}
