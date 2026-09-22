package com.yourname.yellowduck.client.gltf;

/**
 * Compatibility tombstone for the temporary NetCraft reflection bridge.
 *
 * <p>The actual renderer is now {@link SilkNativeGltfRenderer}. Keeping this file
 * means an update ZIP can safely overwrite the old bridge without requiring the
 * user to manually delete a Java file.</p>
 */
@Deprecated
public final class SilkNetcraftRendererBridge {
    private SilkNetcraftRendererBridge() {
    }
}
