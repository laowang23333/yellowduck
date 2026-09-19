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

        CHANNEL.registerMessage(id++, RabbitInputPacket.class,
                RabbitInputPacket::encode, RabbitInputPacket::decode, RabbitInputPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, MountActionPacket.class,
                MountActionPacket::encode, MountActionPacket::decode, MountActionPacket::handle);
        CHANNEL.registerMessage(id++, OpenMountGuiPacket.class,
                OpenMountGuiPacket::encode, OpenMountGuiPacket::decode, OpenMountGuiPacket::handle);
        CHANNEL.registerMessage(id++, SilkReviveLockPacket.class, SilkReviveLockPacket::encode, SilkReviveLockPacket::decode, SilkReviveLockPacket::handle);
        CHANNEL.registerMessage(id++, MountSyncPacket.class,
                MountSyncPacket::encode, MountSyncPacket::decode, MountSyncPacket::handle);
    }

    public static void syncTo(ServerPlayer player) {
        List<String> owned = new ArrayList<>();
        if (MountData.hasMount(player, "ghost_wolf_stars")) {
            owned.add("ghost_wolf_stars");
        }
        if (MountData.hasMount(player, "alpaca")) {
            owned.add("alpaca");
        }
        if (MountData.hasMount(player, "rabbit")) owned.add("rabbit");
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new MountSyncPacket(owned));
    }

    public record RabbitInputPacket(boolean jump, boolean down) {
        public static void encode(RabbitInputPacket msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.jump); buf.writeBoolean(msg.down);
        }
        public static RabbitInputPacket decode(FriendlyByteBuf buf) {
            return new RabbitInputPacket(buf.readBoolean(), buf.readBoolean());
        }
        public static void handle(RabbitInputPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> {
                ServerPlayer player = c.getSender();
                if (player != null && player.isAlive() && player.getVehicle() instanceof
                        com.yourname.yellowduck.entity.RabbitMountEntity rabbit && rabbit.isOwner(player)) {
                    rabbit.acceptInput(msg.jump, msg.down);
                }
            });
            c.setPacketHandled(true);
        }
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
                if (msg.action == 0) MountManager.startMountCountdown(player, msg.mountId);
                else if (msg.action == 1) MountManager.releaseMount(player, msg.mountId);
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
                if (!MountData.hasMount(player, "ghost_wolf_stars") && !MountData.hasMount(player, "alpaca")
                        && !MountData.hasMount(player, "rabbit")) {
                    player.displayClientMessage(
                            Component.literal("§e还没有绑定坐骑。"), true);
                }
            });
            c.setPacketHandled(true);
        }
    }


    public static void sendSilkReviveLock(ServerPlayer player, int ticks) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SilkReviveLockPacket(ticks));
    }
    public record SilkReviveLockPacket(int ticks) {
        public static void encode(SilkReviveLockPacket msg, FriendlyByteBuf buf) { buf.writeVarInt(Math.max(0, msg.ticks)); }
        public static SilkReviveLockPacket decode(FriendlyByteBuf buf) { return new SilkReviveLockPacket(buf.readVarInt()); }
        public static void handle(SilkReviveLockPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get(); c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.yourname.yellowduck.client.SilkReviveClientState.set(msg.ticks))); c.setPacketHandled(true);
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
