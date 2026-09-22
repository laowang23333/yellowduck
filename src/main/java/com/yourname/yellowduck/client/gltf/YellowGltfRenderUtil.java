package com.yourname.yellowduck.client.gltf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** CPU skinning renderer matching the stable NetCraft-style frame interpolation path. */
public final class YellowGltfRenderUtil {
    private YellowGltfRenderUtil() {
    }

    public static void renderModel(YellowGltfModel model,
                                   PoseStack pose,
                                   MultiBufferSource buffers,
                                   int packedLight,
                                   float animationSeconds,
                                   String animationName,
                                   boolean loop) {
        if (model == null || model.texture == null) return;

        Map<Integer, Matrix4f> sampled = animationName != null
                ? YellowGltfAnimationPlayer.sampleAnimation(model, animationName, animationSeconds, loop)
                : Map.of();
        Map<String, Matrix4f> globals = buildGlobalMap(model, sampled);

        Matrix4f poseMatrix = new Matrix4f(pose.last().pose());
        Matrix3f poseNormal = new Matrix3f(pose.last().normal());
        VertexConsumer vertex = buffers.getBuffer(RenderType.entityTranslucent(model.texture));

        for (int meshIndex = 0; meshIndex < model.meshes.size(); meshIndex++) {
            YellowGltfMesh mesh = model.meshes.get(meshIndex);
            if (mesh.hasSkinning()) {
                Matrix4f[] bones = buildBoneMatrices(mesh, globals);
                renderSkinnedMesh(vertex, poseMatrix, poseNormal, mesh, bones, packedLight);
            } else {
                Matrix4f meshGlobal = findMeshNodeGlobal(model, meshIndex, globals);
                Matrix4f combined = new Matrix4f(poseMatrix);
                if (meshGlobal != null) combined.mul(meshGlobal);
                Matrix3f normal = new Matrix3f();
                combined.normal(normal);
                renderStaticMesh(vertex, combined, normal, mesh, packedLight);
            }
        }
    }

    static Map<String, Matrix4f> buildGlobalMap(YellowGltfModel model, Map<Integer, Matrix4f> sampledLocals) {
        Map<String, Matrix4f> globals = new HashMap<>();
        ArrayDeque<YellowGltfNode> queue = new ArrayDeque<>();

        for (YellowGltfNode root : model.rootNodes) {
            Matrix4f local = local(root, sampledLocals);
            globals.put(root.name, local);
            queue.addLast(root);
        }

        while (!queue.isEmpty()) {
            YellowGltfNode parent = queue.pollFirst();
            Matrix4f parentGlobal = globals.get(parent.name);
            for (YellowGltfNode child : parent.children) {
                Matrix4f global = new Matrix4f(parentGlobal).mul(local(child, sampledLocals));
                globals.put(child.name, global);
                queue.addLast(child);
            }
        }
        return globals;
    }

    private static Matrix4f local(YellowGltfNode node, Map<Integer, Matrix4f> sampledLocals) {
        Matrix4f sampled = sampledLocals.get(node.index);
        return sampled != null ? new Matrix4f(sampled) : new Matrix4f(node.localTransform);
    }

    static Matrix4f[] buildBoneMatrices(YellowGltfMesh mesh, Map<String, Matrix4f> globals) {
        int count = mesh.jointNames.size();
        Matrix4f[] bones = new Matrix4f[count];
        for (int i = 0; i < count; i++) {
            Matrix4f jointGlobal = globals.get(mesh.jointNames.get(i));
            Matrix4f inverseBind = mesh.inverseBindMatrices != null && i < mesh.inverseBindMatrices.length
                    ? mesh.inverseBindMatrices[i] : null;
            if (jointGlobal != null && inverseBind != null) {
                bones[i] = new Matrix4f(jointGlobal).mul(inverseBind);
            } else if (jointGlobal != null) {
                bones[i] = new Matrix4f(jointGlobal);
            } else {
                bones[i] = new Matrix4f();
            }
        }
        return bones;
    }

    static Matrix4f findMeshNodeGlobal(YellowGltfModel model,
                                       int meshIndex,
                                       Map<String, Matrix4f> globals) {
        for (YellowGltfNode node : model.nodes) {
            if (node.meshIndices.contains(meshIndex)) return globals.get(node.name);
        }
        return null;
    }

    private static void renderSkinnedMesh(VertexConsumer vertex,
                                          Matrix4f poseMatrix,
                                          Matrix3f poseNormal,
                                          YellowGltfMesh mesh,
                                          Matrix4f[] bones,
                                          int packedLight) {
        if (mesh.indices != null && mesh.indices.length > 0) {
            int triangleCount = mesh.indices.length / 3;
            for (int triangle = 0; triangle < triangleCount; triangle++) {
                int a = mesh.indices[triangle * 3];
                int b = mesh.indices[triangle * 3 + 1];
                int c = mesh.indices[triangle * 3 + 2];
                if (a == b || b == c || a == c) continue;
                emitSkinnedVertex(vertex, poseMatrix, poseNormal, mesh, a, bones, packedLight);
                emitSkinnedVertex(vertex, poseMatrix, poseNormal, mesh, b, bones, packedLight);
                emitSkinnedVertex(vertex, poseMatrix, poseNormal, mesh, c, bones, packedLight);
                // Entity render types use QUADS. NetCraft renders GLTF triangles as a
                // degenerate quad by repeating the third vertex; keep the exact behavior.
                emitSkinnedVertex(vertex, poseMatrix, poseNormal, mesh, c, bones, packedLight);
            }
        } else {
            for (int i = 0; i < mesh.vertexCount; i++) {
                emitSkinnedVertex(vertex, poseMatrix, poseNormal, mesh, i, bones, packedLight);
            }
        }
    }

    private static void emitSkinnedVertex(VertexConsumer vertex,
                                          Matrix4f poseMatrix,
                                          Matrix3f poseNormal,
                                          YellowGltfMesh mesh,
                                          int vertexIndex,
                                          Matrix4f[] bones,
                                          int packedLight) {
        int p = vertexIndex * 3;
        if (p + 2 >= mesh.positions.length) return;

        float x = mesh.positions[p];
        float y = mesh.positions[p + 1];
        float z = mesh.positions[p + 2];
        float nx = p + 2 < mesh.normals.length ? mesh.normals[p] : 0.0F;
        float ny = p + 2 < mesh.normals.length ? mesh.normals[p + 1] : 1.0F;
        float nz = p + 2 < mesh.normals.length ? mesh.normals[p + 2] : 0.0F;
        int uvIndex = vertexIndex * 2;
        float u = uvIndex + 1 < mesh.uvs.length ? mesh.uvs[uvIndex] : 0.0F;
        float v = uvIndex + 1 < mesh.uvs.length ? mesh.uvs[uvIndex + 1] : 0.0F;

        Vector3f position = new Vector3f(x, y, z);
        Vector3f normal = new Vector3f(nx, ny, nz);

        if (mesh.jointIndices != null && mesh.jointWeights != null) {
            int j = vertexIndex * 4;
            if (j + 3 < mesh.jointIndices.length && j + 3 < mesh.jointWeights.length) {
                float weightSum = 0.0F;
                for (int i = 0; i < 4; i++) {
                    float weight = mesh.jointWeights[j + i];
                    int boneIndex = mesh.jointIndices[j + i];
                    if (weight > 0.0F && boneIndex >= 0 && boneIndex < bones.length) weightSum += weight;
                }

                if (weightSum > 0.001F) {
                    position.set(0.0F, 0.0F, 0.0F);
                    normal.set(0.0F, 0.0F, 0.0F);
                    Vector3f tempPosition = new Vector3f();
                    Vector3f tempNormal = new Vector3f();
                    Quaternionf rotation = new Quaternionf();
                    for (int i = 0; i < 4; i++) {
                        float rawWeight = mesh.jointWeights[j + i];
                        int boneIndex = mesh.jointIndices[j + i];
                        if (rawWeight <= 0.0F || boneIndex < 0 || boneIndex >= bones.length) continue;
                        float weight = rawWeight / weightSum;
                        Matrix4f bone = bones[boneIndex];

                        tempPosition.set(x, y, z);
                        bone.transformPosition(tempPosition);
                        tempPosition.mul(weight);
                        position.add(tempPosition);

                        bone.getUnnormalizedRotation(rotation);
                        tempNormal.set(nx, ny, nz);
                        rotation.transform(tempNormal);
                        tempNormal.mul(weight);
                        normal.add(tempNormal);
                    }
                    if (normal.lengthSquared() > 1.0E-8F) normal.normalize();
                }
            }
        }

        vertex.vertex(poseMatrix, position.x, position.y, position.z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight)
                .normal(poseNormal, normal.x, normal.y, normal.z)
                .endVertex();
    }

    private static void renderStaticMesh(VertexConsumer vertex,
                                         Matrix4f matrix,
                                         Matrix3f normalMatrix,
                                         YellowGltfMesh mesh,
                                         int packedLight) {
        if (mesh.indices != null && mesh.indices.length > 0) {
            int triangleCount = mesh.indices.length / 3;
            for (int triangle = 0; triangle < triangleCount; triangle++) {
                int a = mesh.indices[triangle * 3];
                int b = mesh.indices[triangle * 3 + 1];
                int c = mesh.indices[triangle * 3 + 2];
                if (a == b || b == c || a == c) continue;
                emitStaticVertex(vertex, matrix, normalMatrix, mesh, a, packedLight);
                emitStaticVertex(vertex, matrix, normalMatrix, mesh, b, packedLight);
                emitStaticVertex(vertex, matrix, normalMatrix, mesh, c, packedLight);
                emitStaticVertex(vertex, matrix, normalMatrix, mesh, c, packedLight);
            }
        } else {
            for (int i = 0; i < mesh.vertexCount; i++) {
                emitStaticVertex(vertex, matrix, normalMatrix, mesh, i, packedLight);
            }
        }
    }

    private static void emitStaticVertex(VertexConsumer vertex,
                                         Matrix4f matrix,
                                         Matrix3f normalMatrix,
                                         YellowGltfMesh mesh,
                                         int vertexIndex,
                                         int packedLight) {
        int p = vertexIndex * 3;
        if (p + 2 >= mesh.positions.length) return;
        int uvIndex = vertexIndex * 2;
        float u = uvIndex + 1 < mesh.uvs.length ? mesh.uvs[uvIndex] : 0.0F;
        float v = uvIndex + 1 < mesh.uvs.length ? mesh.uvs[uvIndex + 1] : 0.0F;
        float nx = p + 2 < mesh.normals.length ? mesh.normals[p] : 0.0F;
        float ny = p + 2 < mesh.normals.length ? mesh.normals[p + 1] : 1.0F;
        float nz = p + 2 < mesh.normals.length ? mesh.normals[p + 2] : 0.0F;

        vertex.vertex(matrix, mesh.positions[p], mesh.positions[p + 1], mesh.positions[p + 2])
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight)
                .normal(normalMatrix, nx, ny, nz)
                .endVertex();
    }
}
