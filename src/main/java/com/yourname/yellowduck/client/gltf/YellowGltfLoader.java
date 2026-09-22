package com.yourname.yellowduck.client.gltf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Small GLB 2.0 loader used by YellowDuck's native entity renderer.
 *
 * <p>The loader intentionally supports the subset YellowDuck's exported GLB models use:
 * triangle primitives, POSITION/NORMAL/TEXCOORD_0, JOINTS_0/WEIGHTS_0, skins,
 * embedded PNG images and TRS animation channels. It also handles interleaved
 * bufferViews and the standard glTF integer/float accessor component types.</p>
 */
public final class YellowGltfLoader {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int GLB_MAGIC = 0x46546C67;
    private static final int CHUNK_JSON = 0x4E4F534A;
    private static final int CHUNK_BIN = 0x004E4942;

    private YellowGltfLoader() {
    }

    public static YellowGltfModel load(InputStream input, ResourceLocation source) throws IOException {
        byte[] bytes = input.readAllBytes();
        ByteBuffer all = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (all.remaining() < 12) {
            throw new IOException("GLB header is truncated: " + source);
        }

        int magic = all.getInt();
        int version = all.getInt();
        int declaredLength = all.getInt();
        if (magic != GLB_MAGIC) {
            throw new IOException("Not a GLB file: " + source);
        }
        if (version != 2) {
            LOGGER.warn("[YellowDuck GLTF] {} uses GLB version {}, expected 2", source, version);
        }
        if (declaredLength > bytes.length) {
            throw new IOException("GLB declares " + declaredLength + " bytes but only " + bytes.length + " are available");
        }

        String jsonText = null;
        ByteBuffer binary = null;
        while (all.remaining() >= 8) {
            int chunkLength = all.getInt();
            int chunkType = all.getInt();
            if (chunkLength < 0 || chunkLength > all.remaining()) {
                throw new IOException("Invalid GLB chunk length " + chunkLength + " in " + source);
            }
            byte[] chunk = new byte[chunkLength];
            all.get(chunk);
            if (chunkType == CHUNK_JSON) {
                jsonText = new String(chunk, StandardCharsets.UTF_8).trim();
            } else if (chunkType == CHUNK_BIN) {
                binary = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN);
            }
        }

        if (jsonText == null) {
            throw new IOException("GLB has no JSON chunk: " + source);
        }
        if (binary == null) {
            binary = ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN);
        }

        JsonObject root = JsonParser.parseString(jsonText).getAsJsonObject();
        return parse(root, binary, source);
    }

    private static YellowGltfModel parse(JsonObject root, ByteBuffer binary, ResourceLocation source) throws IOException {
        YellowGltfModel model = new YellowGltfModel();
        model.source = source;

        JsonArray bufferViewsJson = array(root, "bufferViews");
        BufferView[] bufferViews = parseBufferViews(bufferViewsJson, binary);
        Accessor[] accessors = parseAccessors(array(root, "accessors"));

        JsonArray nodesJson = array(root, "nodes");
        Map<String, Integer> duplicateNames = new HashMap<>();
        if (nodesJson != null) {
            for (int i = 0; i < nodesJson.size(); i++) {
                JsonObject nodeJson = nodesJson.get(i).getAsJsonObject();
                String baseName = string(nodeJson, "name", "node" + i);
                int used = duplicateNames.getOrDefault(baseName, 0);
                duplicateNames.put(baseName, used + 1);
                String name = used == 0 ? baseName : baseName + "_" + used;

                NodeTransform transform = parseNodeTransform(nodeJson);
                YellowGltfNode node = new YellowGltfNode(
                        i,
                        name,
                        convertBasis(transform.matrix),
                        transform.translation,
                        transform.rotation,
                        transform.scale
                );
                model.nodes.add(node);
                model.nodeByName.put(name, node);
            }

            for (int i = 0; i < nodesJson.size(); i++) {
                JsonObject nodeJson = nodesJson.get(i).getAsJsonObject();
                YellowGltfNode parent = model.nodes.get(i);
                JsonArray children = array(nodeJson, "children");
                if (children == null) continue;
                for (JsonElement childElement : children) {
                    int childIndex = childElement.getAsInt();
                    if (childIndex < 0 || childIndex >= model.nodes.size()) continue;
                    YellowGltfNode child = model.nodes.get(childIndex);
                    parent.children.add(child);
                    child.parent = parent;
                }
            }
        }

        List<SkinInfo> skins = new ArrayList<>();
        JsonArray skinsJson = array(root, "skins");
        if (skinsJson != null) {
            for (JsonElement element : skinsJson) {
                skins.add(parseSkin(element.getAsJsonObject(), model.nodes, bufferViews, accessors, binary));
            }
        }

        List<MeshInfo> meshInfos = new ArrayList<>();
        JsonArray meshesJson = array(root, "meshes");
        if (meshesJson != null) {
            for (JsonElement element : meshesJson) {
                meshInfos.add(parseMeshInfo(element.getAsJsonObject(), bufferViews, accessors, binary));
            }
        }

        int sceneIndex = intValue(root, "scene", 0);
        JsonArray scenesJson = array(root, "scenes");
        if (scenesJson != null && !scenesJson.isEmpty()) {
            if (sceneIndex < 0 || sceneIndex >= scenesJson.size()) sceneIndex = 0;
            JsonObject scene = scenesJson.get(sceneIndex).getAsJsonObject();
            JsonArray roots = array(scene, "nodes");
            if (roots != null) {
                for (JsonElement rootElement : roots) {
                    int index = rootElement.getAsInt();
                    if (index >= 0 && index < model.nodes.size()) {
                        model.rootNodes.add(model.nodes.get(index));
                    }
                }
            }
        }
        if (model.rootNodes.isEmpty()) {
            for (YellowGltfNode node : model.nodes) {
                if (node.parent == null) model.rootNodes.add(node);
            }
        }

        if (nodesJson != null) {
            for (int nodeIndex = 0; nodeIndex < nodesJson.size() && nodeIndex < model.nodes.size(); nodeIndex++) {
                JsonObject nodeJson = nodesJson.get(nodeIndex).getAsJsonObject();
                int meshIndex = intValue(nodeJson, "mesh", -1);
                int skinIndex = intValue(nodeJson, "skin", -1);
                if (meshIndex < 0 || meshIndex >= meshInfos.size()) continue;

                YellowGltfNode node = model.nodes.get(nodeIndex);
                SkinInfo skin = skinIndex >= 0 && skinIndex < skins.size() ? skins.get(skinIndex) : null;
                for (YellowGltfMesh primitive : meshInfos.get(meshIndex).primitives) {
                    YellowGltfMesh attached = primitive;
                    if (skin != null && primitive.jointNames == null) {
                        attached = new YellowGltfMesh(
                                primitive.positions,
                                primitive.normals,
                                primitive.uvs,
                                primitive.indices,
                                primitive.vertexCount,
                                skin.jointNames,
                                skin.inverseBindMatrices,
                                primitive.jointIndices,
                                primitive.jointWeights
                        );
                    }
                    int finalMeshIndex = model.meshes.size();
                    model.meshes.add(attached);
                    node.meshIndices.add(finalMeshIndex);
                }
                node.skinIndex = skinIndex;
            }
        }

        parseAnimations(array(root, "animations"), model, bufferViews, accessors, binary);
        model.embeddedTextureBytes = extractEmbeddedTexture(root, bufferViews, binary);
        return model;
    }

    private static NodeTransform parseNodeTransform(JsonObject node) {
        Vector3f translation = new Vector3f();
        Quaternionf rotation = new Quaternionf();
        Vector3f scale = new Vector3f(1.0F, 1.0F, 1.0F);

        JsonArray t = array(node, "translation");
        if (t != null && t.size() >= 3) {
            translation.set(t.get(0).getAsFloat(), t.get(1).getAsFloat(), t.get(2).getAsFloat());
        }
        JsonArray r = array(node, "rotation");
        if (r != null && r.size() >= 4) {
            rotation.set(r.get(0).getAsFloat(), r.get(1).getAsFloat(), r.get(2).getAsFloat(), r.get(3).getAsFloat());
        }
        JsonArray s = array(node, "scale");
        if (s != null && s.size() >= 3) {
            scale.set(s.get(0).getAsFloat(), s.get(1).getAsFloat(), s.get(2).getAsFloat());
        }

        Matrix4f matrix;
        JsonArray matrixJson = array(node, "matrix");
        if (matrixJson != null && matrixJson.size() >= 16) {
            float[] values = new float[16];
            for (int i = 0; i < 16; i++) values[i] = matrixJson.get(i).getAsFloat();
            matrix = new Matrix4f().set(values);
            matrix.getTranslation(translation);
            matrix.getUnnormalizedRotation(rotation);
            matrix.getScale(scale);
        } else {
            matrix = new Matrix4f().translationRotateScale(translation, rotation, scale);
        }
        return new NodeTransform(matrix, translation, rotation, scale);
    }

    /** glTF is right-handed; YellowDuck/NetCraft rendering mirrors Z into Minecraft's basis. */
    static Matrix4f convertBasis(Matrix4f matrix) {
        Matrix4f mirror = new Matrix4f().scaling(1.0F, 1.0F, -1.0F);
        return new Matrix4f(mirror).mul(matrix).mul(mirror);
    }

    private static BufferView[] parseBufferViews(JsonArray array, ByteBuffer binary) {
        if (array == null) return new BufferView[0];
        BufferView[] result = new BufferView[array.size()];
        for (int i = 0; i < array.size(); i++) {
            JsonObject json = array.get(i).getAsJsonObject();
            int offset = intValue(json, "byteOffset", 0);
            int length = intValue(json, "byteLength", 0);
            int stride = intValue(json, "byteStride", 0);
            result[i] = new BufferView(offset, length, stride);
        }
        return result;
    }

    private static Accessor[] parseAccessors(JsonArray array) {
        if (array == null) return new Accessor[0];
        Accessor[] result = new Accessor[array.size()];
        for (int i = 0; i < array.size(); i++) {
            JsonObject json = array.get(i).getAsJsonObject();
            result[i] = new Accessor(
                    intValue(json, "bufferView", -1),
                    intValue(json, "byteOffset", 0),
                    intValue(json, "componentType", 5126),
                    intValue(json, "count", 0),
                    string(json, "type", "SCALAR"),
                    json.has("normalized") && json.get("normalized").getAsBoolean()
            );
        }
        return result;
    }

    private static MeshInfo parseMeshInfo(JsonObject meshJson,
                                          BufferView[] views,
                                          Accessor[] accessors,
                                          ByteBuffer binary) throws IOException {
        List<YellowGltfMesh> primitives = new ArrayList<>();
        JsonArray primitiveArray = array(meshJson, "primitives");
        if (primitiveArray == null) return new MeshInfo(primitives);
        for (JsonElement element : primitiveArray) {
            JsonObject primitive = element.getAsJsonObject();
            int mode = intValue(primitive, "mode", 4);
            if (mode != 4) {
                LOGGER.warn("[YellowDuck GLTF] skipping unsupported primitive mode {}", mode);
                continue;
            }
            YellowGltfMesh parsed = parsePrimitive(primitive, views, accessors, binary);
            if (parsed != null) primitives.add(parsed);
        }
        return new MeshInfo(primitives);
    }

    private static YellowGltfMesh parsePrimitive(JsonObject primitive,
                                                 BufferView[] views,
                                                 Accessor[] accessors,
                                                 ByteBuffer binary) throws IOException {
        JsonObject attrs = primitive.has("attributes") ? primitive.getAsJsonObject("attributes") : null;
        if (attrs == null || !attrs.has("POSITION")) return null;

        Accessor positionAccessor = accessor(accessors, attrs.get("POSITION").getAsInt());
        int vertexCount = positionAccessor.count;
        float[] positions = readFloats(positionAccessor, views, binary);
        if (positions.length < vertexCount * 3) return null;
        for (int i = 0; i < vertexCount; i++) positions[i * 3 + 2] = -positions[i * 3 + 2];

        float[] normals;
        if (attrs.has("NORMAL")) {
            normals = readFloats(accessor(accessors, attrs.get("NORMAL").getAsInt()), views, binary);
            for (int i = 0; i < vertexCount && i * 3 + 2 < normals.length; i++) {
                normals[i * 3 + 2] = -normals[i * 3 + 2];
            }
        } else {
            normals = new float[vertexCount * 3];
            for (int i = 0; i < vertexCount; i++) normals[i * 3 + 1] = 1.0F;
        }

        float[] uvs = attrs.has("TEXCOORD_0")
                ? readFloats(accessor(accessors, attrs.get("TEXCOORD_0").getAsInt()), views, binary)
                : new float[vertexCount * 2];

        int[] indices;
        if (primitive.has("indices")) {
            indices = readInts(accessor(accessors, primitive.get("indices").getAsInt()), views, binary);
        } else {
            indices = new int[vertexCount];
            for (int i = 0; i < vertexCount; i++) indices[i] = i;
        }

        int[] joints = attrs.has("JOINTS_0")
                ? readInts(accessor(accessors, attrs.get("JOINTS_0").getAsInt()), views, binary)
                : null;
        float[] weights = attrs.has("WEIGHTS_0")
                ? readFloats(accessor(accessors, attrs.get("WEIGHTS_0").getAsInt()), views, binary)
                : null;
        if (weights != null) normalizeWeights(weights, vertexCount);
        if (joints != null && weights == null) {
            weights = new float[vertexCount * 4];
            for (int i = 0; i < vertexCount; i++) weights[i * 4] = 1.0F;
        }

        return new YellowGltfMesh(
                positions, normals, uvs, indices, vertexCount,
                null, null, joints, weights
        );
    }

    private static SkinInfo parseSkin(JsonObject skin,
                                      List<YellowGltfNode> nodes,
                                      BufferView[] views,
                                      Accessor[] accessors,
                                      ByteBuffer binary) throws IOException {
        List<String> jointNames = new ArrayList<>();
        JsonArray joints = array(skin, "joints");
        if (joints != null) {
            for (JsonElement element : joints) {
                int index = element.getAsInt();
                if (index >= 0 && index < nodes.size()) jointNames.add(nodes.get(index).name);
                else jointNames.add("joint_" + jointNames.size());
            }
        }

        Matrix4f[] inverseBind = new Matrix4f[jointNames.size()];
        if (skin.has("inverseBindMatrices")) {
            float[] values = readFloats(accessor(accessors, skin.get("inverseBindMatrices").getAsInt()), views, binary);
            int matrixCount = values.length / 16;
            for (int i = 0; i < inverseBind.length; i++) {
                if (i < matrixCount) {
                    inverseBind[i] = convertBasis(new Matrix4f().set(values, i * 16));
                } else {
                    inverseBind[i] = new Matrix4f();
                }
            }
        } else {
            for (int i = 0; i < inverseBind.length; i++) inverseBind[i] = new Matrix4f();
        }
        return new SkinInfo(jointNames, inverseBind);
    }

    private static void parseAnimations(JsonArray animations,
                                        YellowGltfModel model,
                                        BufferView[] views,
                                        Accessor[] accessors,
                                        ByteBuffer binary) throws IOException {
        if (animations == null) return;
        for (int animationIndex = 0; animationIndex < animations.size(); animationIndex++) {
            JsonObject animationJson = animations.get(animationIndex).getAsJsonObject();
            String name = string(animationJson, "name", "animation_" + animationIndex);
            JsonArray channelsJson = array(animationJson, "channels");
            JsonArray samplersJson = array(animationJson, "samplers");
            if (channelsJson == null || samplersJson == null) continue;

            List<SamplerData> samplers = new ArrayList<>();
            for (JsonElement samplerElement : samplersJson) {
                JsonObject samplerJson = samplerElement.getAsJsonObject();
                samplers.add(new SamplerData(
                        intValue(samplerJson, "input", -1),
                        intValue(samplerJson, "output", -1),
                        string(samplerJson, "interpolation", "LINEAR")
                ));
            }

            List<YellowGltfAnimation.Channel> channels = new ArrayList<>();
            float duration = 0.0F;
            for (JsonElement channelElement : channelsJson) {
                JsonObject channelJson = channelElement.getAsJsonObject();
                int samplerIndex = intValue(channelJson, "sampler", -1);
                JsonObject target = channelJson.has("target") ? channelJson.getAsJsonObject("target") : null;
                if (target == null || samplerIndex < 0 || samplerIndex >= samplers.size()) continue;

                int nodeIndex = intValue(target, "node", -1);
                YellowGltfAnimation.TargetPath path = parseTargetPath(string(target, "path", ""));
                if (nodeIndex < 0 || path == null) continue;

                SamplerData sampler = samplers.get(samplerIndex);
                if (sampler.input < 0 || sampler.output < 0
                        || sampler.input >= accessors.length || sampler.output >= accessors.length) continue;

                float[] timestamps = readFloats(accessors[sampler.input], views, binary);
                float[] values = readFloats(accessors[sampler.output], views, binary);
                if (timestamps.length > 0) duration = Math.max(duration, timestamps[timestamps.length - 1]);

                YellowGltfAnimation.Interpolation interpolation = switch (sampler.interpolation) {
                    case "STEP" -> YellowGltfAnimation.Interpolation.STEP;
                    case "CUBICSPLINE" -> YellowGltfAnimation.Interpolation.CUBICSPLINE;
                    default -> YellowGltfAnimation.Interpolation.LINEAR;
                };
                channels.add(new YellowGltfAnimation.Channel(nodeIndex, path, timestamps, values, interpolation));
            }

            YellowGltfAnimation animation = new YellowGltfAnimation(name, duration, channels);
            model.animations.add(animation);
            model.animationByName.put(name, animation);
        }
    }

    private static YellowGltfAnimation.TargetPath parseTargetPath(String path) {
        return switch (path) {
            case "translation" -> YellowGltfAnimation.TargetPath.TRANSLATION;
            case "rotation" -> YellowGltfAnimation.TargetPath.ROTATION;
            case "scale" -> YellowGltfAnimation.TargetPath.SCALE;
            default -> null;
        };
    }

    private static byte[] extractEmbeddedTexture(JsonObject root, BufferView[] views, ByteBuffer binary) {
        try {
            JsonArray images = array(root, "images");
            if (images == null || images.isEmpty()) return null;

            int imageIndex = 0;
            JsonArray materials = array(root, "materials");
            JsonArray textures = array(root, "textures");
            if (materials != null && !materials.isEmpty() && textures != null && !textures.isEmpty()) {
                JsonObject material = materials.get(0).getAsJsonObject();
                JsonObject pbr = material.has("pbrMetallicRoughness")
                        ? material.getAsJsonObject("pbrMetallicRoughness") : null;
                if (pbr != null && pbr.has("baseColorTexture")) {
                    int textureIndex = intValue(pbr.getAsJsonObject("baseColorTexture"), "index", 0);
                    if (textureIndex >= 0 && textureIndex < textures.size()) {
                        imageIndex = intValue(textures.get(textureIndex).getAsJsonObject(), "source", 0);
                    }
                }
            }
            if (imageIndex < 0 || imageIndex >= images.size()) imageIndex = 0;
            JsonObject image = images.get(imageIndex).getAsJsonObject();
            if (!image.has("bufferView")) return null;
            int viewIndex = image.get("bufferView").getAsInt();
            if (viewIndex < 0 || viewIndex >= views.length) return null;
            BufferView view = views[viewIndex];
            if (view.offset < 0 || view.length < 0 || view.offset + view.length > binary.capacity()) return null;
            byte[] bytes = new byte[view.length];
            ByteBuffer duplicate = binary.duplicate().order(ByteOrder.LITTLE_ENDIAN);
            duplicate.position(view.offset);
            duplicate.get(bytes);
            return bytes;
        } catch (Throwable error) {
            LOGGER.warn("[YellowDuck GLTF] failed to extract embedded texture", error);
            return null;
        }
    }

    private static float[] readFloats(Accessor accessor, BufferView[] views, ByteBuffer binary) throws IOException {
        if (accessor.bufferView < 0 || accessor.bufferView >= views.length) return new float[0];
        BufferView view = views[accessor.bufferView];
        int components = componentCount(accessor.type);
        int size = componentSize(accessor.componentType);
        int stride = view.stride > 0 ? view.stride : components * size;
        float[] result = new float[Math.max(0, accessor.count * components)];
        int base = view.offset + accessor.byteOffset;

        for (int i = 0; i < accessor.count; i++) {
            int elementOffset = base + i * stride;
            for (int c = 0; c < components; c++) {
                result[i * components + c] = readFloatComponent(
                        binary, elementOffset + c * size, accessor.componentType, accessor.normalized);
            }
        }
        return result;
    }

    private static int[] readInts(Accessor accessor, BufferView[] views, ByteBuffer binary) throws IOException {
        if (accessor.bufferView < 0 || accessor.bufferView >= views.length) return new int[0];
        BufferView view = views[accessor.bufferView];
        int components = componentCount(accessor.type);
        int size = componentSize(accessor.componentType);
        int stride = view.stride > 0 ? view.stride : components * size;
        int[] result = new int[Math.max(0, accessor.count * components)];
        int base = view.offset + accessor.byteOffset;
        for (int i = 0; i < accessor.count; i++) {
            int elementOffset = base + i * stride;
            for (int c = 0; c < components; c++) {
                result[i * components + c] = readIntComponent(binary, elementOffset + c * size, accessor.componentType);
            }
        }
        return result;
    }

    private static float readFloatComponent(ByteBuffer buffer, int offset, int componentType, boolean normalized) throws IOException {
        checkRange(buffer, offset, componentSize(componentType));
        return switch (componentType) {
            case 5126 -> buffer.getFloat(offset);
            case 5121 -> normalized ? (buffer.get(offset) & 0xFF) / 255.0F : (buffer.get(offset) & 0xFF);
            case 5123 -> normalized ? (buffer.getShort(offset) & 0xFFFF) / 65535.0F : (buffer.getShort(offset) & 0xFFFF);
            case 5120 -> {
                byte v = buffer.get(offset);
                yield normalized ? Math.max(-1.0F, v / 127.0F) : v;
            }
            case 5122 -> {
                short v = buffer.getShort(offset);
                yield normalized ? Math.max(-1.0F, v / 32767.0F) : v;
            }
            case 5125 -> {
                long v = Integer.toUnsignedLong(buffer.getInt(offset));
                yield normalized ? (float) (v / 4294967295.0D) : (float) v;
            }
            default -> throw new IOException("Unsupported glTF componentType " + componentType);
        };
    }

    private static int readIntComponent(ByteBuffer buffer, int offset, int componentType) throws IOException {
        checkRange(buffer, offset, componentSize(componentType));
        return switch (componentType) {
            case 5120 -> buffer.get(offset);
            case 5121 -> buffer.get(offset) & 0xFF;
            case 5122 -> buffer.getShort(offset);
            case 5123 -> buffer.getShort(offset) & 0xFFFF;
            case 5125 -> buffer.getInt(offset);
            case 5126 -> (int) buffer.getFloat(offset);
            default -> throw new IOException("Unsupported glTF integer componentType " + componentType);
        };
    }

    private static void checkRange(ByteBuffer buffer, int offset, int size) throws IOException {
        if (offset < 0 || offset + size > buffer.capacity()) {
            throw new IOException("glTF accessor points outside BIN chunk");
        }
    }

    private static int componentSize(int componentType) throws IOException {
        return switch (componentType) {
            case 5120, 5121 -> 1;
            case 5122, 5123 -> 2;
            case 5125, 5126 -> 4;
            default -> throw new IOException("Unsupported glTF componentType " + componentType);
        };
    }

    private static int componentCount(String type) {
        return switch (type) {
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4", "MAT2" -> 4;
            case "MAT3" -> 9;
            case "MAT4" -> 16;
            default -> 1;
        };
    }

    private static void normalizeWeights(float[] weights, int vertexCount) {
        int count = Math.min(vertexCount, weights.length / 4);
        for (int i = 0; i < count; i++) {
            int base = i * 4;
            float sum = weights[base] + weights[base + 1] + weights[base + 2] + weights[base + 3];
            if (sum > 1.0E-6F && Math.abs(sum - 1.0F) > 1.0E-6F) {
                weights[base] /= sum;
                weights[base + 1] /= sum;
                weights[base + 2] /= sum;
                weights[base + 3] /= sum;
            }
        }
    }

    private static Accessor accessor(Accessor[] accessors, int index) throws IOException {
        if (index < 0 || index >= accessors.length) throw new IOException("Invalid glTF accessor index " + index);
        return accessors[index];
    }

    private static JsonArray array(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonArray()
                ? object.getAsJsonArray(key) : null;
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        return object != null && object.has(key) ? object.get(key).getAsInt() : fallback;
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object != null && object.has(key) ? object.get(key).getAsString() : fallback;
    }

    private record NodeTransform(Matrix4f matrix, Vector3f translation, Quaternionf rotation, Vector3f scale) {}
    private record BufferView(int offset, int length, int stride) {}
    private record Accessor(int bufferView, int byteOffset, int componentType, int count, String type, boolean normalized) {}
    private record SkinInfo(List<String> jointNames, Matrix4f[] inverseBindMatrices) {}
    private record MeshInfo(List<YellowGltfMesh> primitives) {}
    private record SamplerData(int input, int output, String interpolation) {}
}
