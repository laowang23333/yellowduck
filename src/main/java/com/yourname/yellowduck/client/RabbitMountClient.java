package com.yourname.yellowduck.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yourname.yellowduck.entity.RabbitMountEntity;
import com.yourname.yellowduck.network.MountNetwork;
import com.yourname.yellowduck.util.RabbitFlight;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "yellowduck", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RabbitMountClient {
    private static final KeyMapping DESCEND = new KeyMapping("key.yellowduck.rabbit_descend", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.yellowduck");
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) { event.register(DESCEND); }
    @SubscribeEvent public static void overlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("rabbit_stamina", (gui, graphics, partialTick, width, height) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.hideGui || mc.player == null || !(mc.player.getVehicle() instanceof RabbitMountEntity rabbit)) return;
            int x = width / 2 - 91;
            int y = height - Math.max(59, Math.max(gui.leftHeight, gui.rightHeight) + 10);
            int energy = rabbit.getEnergy();
            int color = rabbit.isFlying() ? 0xFF76DFFF : rabbit.isResting() ? 0xFFFFC466 : 0xFF9BECAD;
            graphics.fill(x, y, x + 182, y + 7, 0xDD172332);
            graphics.fill(x + 1, y + 1, x + 181, y + 6, 0xFF394555);
            graphics.fill(x + 1, y + 1, x + 1 + energy * 180 / RabbitFlight.MAX, y + 6, color);
            String label = rabbit.isFlying() ? "玉兔飞行 " + ((energy + 59) / 60) + "秒"
                    : rabbit.isResting() ? "玉兔恢复中 " + ((rabbit.getRestTicks() + 19) / 20) + "秒" : "玉兔耐力已满";
            graphics.drawCenteredString(mc.font, label, width / 2, y - 10, color);
        });
    }
    @Mod.EventBusSubscriber(modid = "yellowduck", value = Dist.CLIENT)
    public static final class Input {
        private static boolean jump, down;
        private static int mountId = -1, heartbeat;
        @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || !(mc.player.getVehicle() instanceof RabbitMountEntity rabbit)) {
                mountId = -1; jump = false; down = false; heartbeat = 0; return;
            }
            boolean nextJump = mc.screen == null && mc.options.keyJump.isDown();
            boolean nextDown = mc.screen == null && DESCEND.isDown();
            if (mountId != rabbit.getId() || jump != nextJump || down != nextDown || ++heartbeat >= 5) {
                MountNetwork.CHANNEL.sendToServer(new MountNetwork.RabbitInputPacket(nextJump, nextDown));
                mountId = rabbit.getId(); jump = nextJump; down = nextDown; heartbeat = 0;
            }
        }
    }
}
