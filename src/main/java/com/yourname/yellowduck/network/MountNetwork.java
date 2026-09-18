package com.yourname.yellowduck.network;

import com.yourname.yellowduck.util.MountData;
import com.yourname.yellowduck.util.MountManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 坐骑 GUI 与服务端之间的 C2S/S2C 通讯。 */
public final class MountNetwork {
    // 坐骑 ID 从旧名称切换为 demon_tengu，协议同步升级，防止新旧客户端混用。
    private static final String PROTOCOL = "4";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("yellowduck", "mount"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static int id = 0;
    private static boolean initialized;

    private MountNetwork() {}

    public static void init() {
        if (initialized) return;
        initialized = true;

        CHANNEL.registerMessage(id++, MountActionPacket.class,
                MountActionPacket::encode, MountActionPacket::decode, MountActionPacket::handle);
        CHANNEL.registerMessage(id++, OpenMountGuiPacket.class,
                OpenMountGuiPacket::encode, OpenMountGuiPacket::decode, OpenMountGuiPacket::handle);
        CHANNEL.registerMessage(id++, MountSyncPacket.class,
                MountSyncPacket::encode, MountSyncPacket::decode, MountSyncPacket::handle);
    }

    public static void syncTo(ServerPlayer player) {
        List<String> owned = new ArrayList<>();
        if (MountData.hasDemonTengu(player)) {
            owned.add(MountData.DEMON_TENGU_ID);
        }
        if (MountData.hasMount(player, MountData.ALPACA_ID)) {
            owned.add(MountData.ALPACA_ID);
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new MountSyncPacket(owned));
    }

    public record MountActionPacket(int action, String mountId) {
        public static void encode(MountActionPacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.action);
            buf.writeUtf(msg.mountId == null ? "" : msg.mountId, 64);
        }

        public static MountActionPacket decode(FriendlyByteBuf buf) {
            return new MountActionPacket(buf.readVarInt(), buf.readUtf(64));
        }

        public static void handle(MountActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> {
                ServerPlayer player = c.getSender();
                if (player == null) return;
                String mountId = MountData.canonicalizeMountId(msg.mountId);
                if (msg.action == 0) MountManager.startMountCountdown(player, mountId);
                else if (msg.action == 1) MountManager.releaseMount(player, mountId);
            });
            c.setPacketHandled(true);
        }
    }

    public static final class OpenMountGuiPacket {
        public static void encode(OpenMountGuiPacket msg, FriendlyByteBuf buf) {}

        public static OpenMountGuiPacket decode(FriendlyByteBuf buf) {
            return new OpenMountGuiPacket();
        }

        public static void handle(OpenMountGuiPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> {
                ServerPlayer player = c.getSender();
                if (player == null) return;
                syncTo(player);
                if (!MountData.hasDemonTengu(player)) {
                    player.displayClientMessage(
                            Component.literal("§e还没有绑定魔化天狗坐骑。"), true);
                }
            });
            c.setPacketHandled(true);
        }
    }

    public record MountSyncPacket(List<String> ownedIds) {
        public static void encode(MountSyncPacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.ownedIds.size());
            for (String id : msg.ownedIds) buf.writeUtf(id, 64);
        }

        public static MountSyncPacket decode(FriendlyByteBuf buf) {
            int size = Math.min(buf.readVarInt(), 128);
            List<String> ids = new ArrayList<>(size);
            for (int i = 0; i < size; i++) ids.add(buf.readUtf(64));
            return new MountSyncPacket(ids);
        }

        public static void handle(MountSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.yourname.yellowduck.client.MountClientState.replace(msg.ownedIds)));
            c.setPacketHandled(true);
        }
    }
}
