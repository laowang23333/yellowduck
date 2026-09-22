package com.yourname.yellowduck.client.gltf;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Samples GLB animation channels using render-frame time, not server-tick-only time. */
public final class YellowGltfAnimationPlayer {
    private YellowGltfAnimationPlayer() {
    }

    public static Map<Integer, Matrix4f> sampleAnimation(YellowGltfModel model,
                                                          String animationName,
                                                          float seconds,
                                                          boolean loop) {
        YellowGltfAnimation animation = model.animationByName.get(animationName);
        if (animation == null) return Collections.emptyMap();

        float first = firstTimestamp(animation);
        float time = normalizeTime(seconds, first, animation.duration, loop);
        Map<Integer, List<YellowGltfAnimation.Channel>> byNode = new HashMap<>();
        for (YellowGltfAnimation.Channel channel : animation.channels) {
            byNode.computeIfAbsent(channel.targetNodeIndex, ignored -> new java.util.ArrayList<>()).add(channel);
        }

        Map<Integer, Matrix4f> sampled = new HashMap<>();
        for (Map.Entry<Integer, List<YellowGltfAnimation.Channel>> entry : byNode.entrySet()) {
            int nodeIndex = entry.getKey();
            if (nodeIndex < 0 || nodeIndex >= model.nodes.size()) continue;
            YellowGltfNode node = model.nodes.get(nodeIndex);

            Vector3f translation = new Vector3f(node.baseTranslation);
            Quaternionf rotation = new Quaternionf(node.baseRotation);
            Vector3f scale = new Vector3f(node.baseScale);

            for (YellowGltfAnimation.Channel channel : entry.getValue()) {
                switch (channel.targetPath) {
                    case TRANSLATION -> sampleVec3(channel, time, translation);
                    case ROTATION -> sampleQuaternion(channel, time, rotation);
                    case SCALE -> sampleVec3(channel, time, scale);
                }
            }

            Matrix4f gltfLocal = new Matrix4f().translationRotateScale(translation, rotation, scale);
            sampled.put(nodeIndex, YellowGltfLoader.convertBasis(gltfLocal));
        }
        return sampled;
    }

    public static float getAnimationDuration(YellowGltfModel model, String animationName) {
        YellowGltfAnimation animation = model.animationByName.get(animationName);
        return animation == null ? 0.0F : animation.duration;
    }

    private static float firstTimestamp(YellowGltfAnimation animation) {
        float first = Float.MAX_VALUE;
        for (YellowGltfAnimation.Channel channel : animation.channels) {
            if (channel.timestamps.length > 0) first = Math.min(first, channel.timestamps[0]);
        }
        return first == Float.MAX_VALUE ? 0.0F : first;
    }

    private static float normalizeTime(float seconds, float first, float duration, boolean loop) {
        if (duration <= first + 1.0E-6F) return first;
        if (!loop) return Math.max(first, Math.min(seconds, duration));
        float length = duration - first;
        float local = (seconds - first) % length;
        if (local < 0.0F) local += length;
        return first + local;
    }

    private static void sampleVec3(YellowGltfAnimation.Channel channel, float time, Vector3f destination) {
        int keyCount = channel.timestamps.length;
        if (keyCount == 0) return;
        int stride = channel.interpolation == YellowGltfAnimation.Interpolation.CUBICSPLINE ? 9 : 3;
        if (channel.values.length < keyCount * stride) return;

        int left = findLeftKey(channel.timestamps, time);
        if (left >= keyCount - 1 || channel.interpolation == YellowGltfAnimation.Interpolation.STEP) {
            readVec3(channel, left, destination);
            return;
        }

        int right = left + 1;
        float alpha = interpolationAlpha(channel.timestamps[left], channel.timestamps[right], time);
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        readVec3(channel, left, a);
        readVec3(channel, right, b);
        destination.set(
                a.x + (b.x - a.x) * alpha,
                a.y + (b.y - a.y) * alpha,
                a.z + (b.z - a.z) * alpha
        );
    }

    private static void readVec3(YellowGltfAnimation.Channel channel, int key, Vector3f destination) {
        if (channel.interpolation == YellowGltfAnimation.Interpolation.CUBICSPLINE) {
            int base = key * 9 + 3; // in tangent, VALUE, out tangent
            destination.set(channel.values[base], channel.values[base + 1], channel.values[base + 2]);
        } else {
            int base = key * 3;
            destination.set(channel.values[base], channel.values[base + 1], channel.values[base + 2]);
        }
    }

    private static void sampleQuaternion(YellowGltfAnimation.Channel channel, float time, Quaternionf destination) {
        int keyCount = channel.timestamps.length;
        if (keyCount == 0) return;
        int stride = channel.interpolation == YellowGltfAnimation.Interpolation.CUBICSPLINE ? 12 : 4;
        if (channel.values.length < keyCount * stride) return;

        int left = findLeftKey(channel.timestamps, time);
        if (left >= keyCount - 1 || channel.interpolation == YellowGltfAnimation.Interpolation.STEP) {
            readQuaternion(channel, left, destination);
            destination.normalize();
            return;
        }

        int right = left + 1;
        float alpha = interpolationAlpha(channel.timestamps[left], channel.timestamps[right], time);
        Quaternionf a = new Quaternionf();
        Quaternionf b = new Quaternionf();
        readQuaternion(channel, left, a);
        readQuaternion(channel, right, b);
        a.normalize().slerp(b.normalize(), alpha).normalize();
        destination.set(a);
    }

    private static void readQuaternion(YellowGltfAnimation.Channel channel, int key, Quaternionf destination) {
        if (channel.interpolation == YellowGltfAnimation.Interpolation.CUBICSPLINE) {
            int base = key * 12 + 4;
            destination.set(channel.values[base], channel.values[base + 1], channel.values[base + 2], channel.values[base + 3]);
        } else {
            int base = key * 4;
            destination.set(channel.values[base], channel.values[base + 1], channel.values[base + 2], channel.values[base + 3]);
        }
    }

    private static int findLeftKey(float[] timestamps, float time) {
        if (timestamps.length <= 1 || time <= timestamps[0]) return 0;
        int last = timestamps.length - 1;
        if (time >= timestamps[last]) return last;

        int low = 0;
        int high = last;
        while (low + 1 < high) {
            int mid = (low + high) >>> 1;
            if (timestamps[mid] <= time) low = mid;
            else high = mid;
        }
        return low;
    }

    private static float interpolationAlpha(float leftTime, float rightTime, float time) {
        float span = rightTime - leftTime;
        if (span <= 1.0E-6F) return 1.0F;
        return Math.max(0.0F, Math.min(1.0F, (time - leftTime) / span));
    }
}
