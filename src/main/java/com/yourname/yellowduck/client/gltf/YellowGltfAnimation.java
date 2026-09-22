package com.yourname.yellowduck.client.gltf;

import java.util.List;

/** Lightweight glTF animation data used by YellowDuck's native renderer. */
public final class YellowGltfAnimation {
    public final String name;
    public final float duration;
    public final List<Channel> channels;

    public YellowGltfAnimation(String name, float duration, List<Channel> channels) {
        this.name = name;
        this.duration = duration;
        this.channels = channels;
    }

    public enum TargetPath {
        TRANSLATION,
        ROTATION,
        SCALE
    }

    public enum Interpolation {
        LINEAR,
        STEP,
        CUBICSPLINE
    }

    public static final class Channel {
        public final int targetNodeIndex;
        public final TargetPath targetPath;
        public final float[] timestamps;
        public final float[] values;
        public final Interpolation interpolation;

        public Channel(int targetNodeIndex,
                       TargetPath targetPath,
                       float[] timestamps,
                       float[] values,
                       Interpolation interpolation) {
            this.targetNodeIndex = targetNodeIndex;
            this.targetPath = targetPath;
            this.timestamps = timestamps;
            this.values = values;
            this.interpolation = interpolation;
        }

        public int componentCount() {
            return targetPath == TargetPath.ROTATION ? 4 : 3;
        }
    }
}
