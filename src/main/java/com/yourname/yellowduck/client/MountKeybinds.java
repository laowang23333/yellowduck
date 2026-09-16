package com.yourname.yellowduck.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yourname.yellowduck.network.MountNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "yellowduck", value = net.minecraftforge.api.distmarker.Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MountKeybinds {
    public static final KeyMapping OPEN_MOUNT = new KeyMapping(
            "key.yellowduck.mounts", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M,
            "key.categories.yellowduck");

    private MountKeybinds() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MOUNT);
    }

    @Mod.EventBusSubscriber(modid = "yellowduck", value = net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static class InputHandler {
        @SubscribeEvent
        public static void onKey(InputEvent.Key event) {
            if (OPEN_MOUNT.consumeClick() && Minecraft.getInstance().screen == null) {
                Minecraft.getInstance().setScreen(new MountScreen());
                MountNetwork.CHANNEL.sendToServer(new MountNetwork.OpenMountGuiPacket());
            }
        }
    }
}
