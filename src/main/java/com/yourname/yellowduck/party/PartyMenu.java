package com.yourname.yellowduck.party;

import com.mojang.authlib.GameProfile;
import com.yourname.yellowduck.dungeon.DungeonConfig;
import com.yourname.yellowduck.dungeon.DungeonDefinition;
import com.yourname.yellowduck.dungeon.DungeonManager;
import com.yourname.yellowduck.block.MeetStoneBlockEntity;
import com.yourname.yellowduck.registry.ModBlocks;
import com.yourname.yellowduck.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 自定义组队 GUI 的服务端菜单。
 *
 * 客户端不再显示原版箱子槽位；54 个只读隐藏槽只负责把队伍状态同步给客户端。
 * 所有按钮最终仍由服务端 clickMenuButton 校验并执行，客户端不能直接改队伍数据。
 */
public class PartyMenu extends AbstractContainerMenu {
    public static final int STATE_SIZE = 54;
    public static final int META_SLOT = 0;
    public static final int MEMBER_START = 1;
    public static final int MEMBER_COUNT = 12;
    public static final int INVITE_START = 16;
    public static final int INVITE_COUNT = 20;
    public static final int BOUND_DUNGEON_SLOT = 40;
    // 保留旧常量名，避免其他客户端代码/旧存档升级时直接断引用。
    public static final int DUNGEON_CLEOPATRA_SLOT = BOUND_DUNGEON_SLOT;
    public static final int DUNGEON_SAKURA_SLOT = 41;
    public static final double INVITE_RADIUS = 20.0D;
    private static final double MENU_USE_RADIUS_SQR = 8.0D * 8.0D;

    public static final int ACTION_CREATE = 1;
    public static final int ACTION_ACCEPT = 2;
    public static final int ACTION_READY = 3;
    public static final int ACTION_LEAVE = 4;
    public static final int ACTION_START = 5;
    public static final int ACTION_SELECT_CLEOPATRA = 10;
    public static final int ACTION_SELECT_SAKURA = 11;
    public static final int ACTION_REFRESH = 90;

    // 动态按钮统一使用低位 ID。
    // 一些 Forge + Bukkit/Mohist 混合端对过大的 container button id 兼容并不稳定，
    // 原来的 1000/2000/3000 可能导致客户端看起来点了“邀请”，服务端却没有进入邀请分支。
    // 这些区间彼此不重叠，并且与上面的固定按钮 ID 保持分离。
    public static final int ACTION_INVITE_BASE = 20;  // 20 ~ 39
    public static final int ACTION_KICK_BASE = 40;    // 40 ~ 51
    public static final int ACTION_LEADER_BASE = 60;  // 60 ~ 71

    private final SimpleContainer state = new SimpleContainer(STATE_SIZE);
    private final BlockPos stationPos;
    private final Map<Integer, UUID> inviteTargets = new LinkedHashMap<>();
    private final Map<Integer, UUID> memberTargets = new LinkedHashMap<>();

    public PartyMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf == null ? inv.player.blockPosition() : buf.readBlockPos());
    }

    public PartyMenu(int id, Inventory inv) {
        this(id, inv, inv.player.blockPosition());
    }

    public PartyMenu(int id, Inventory inv, BlockPos stationPos) {
        super(ModMenuTypes.PARTY.get(), id);
        this.stationPos = stationPos.immutable();
        // 隐藏状态槽。坐标放到屏幕外，客户端自定义 Screen 只读取内容，不绘制/交互这些槽。
        for (int i = 0; i < STATE_SIZE; i++) addSlot(new StateSlot(state, i));
        if (!inv.player.level().isClientSide && inv.player instanceof ServerPlayer serverPlayer) refresh(serverPlayer);
    }

    public void refresh(ServerPlayer viewer) {
        inviteTargets.clear();
        memberTargets.clear();
        for (int i = 0; i < STATE_SIZE; i++) state.setItem(i, ItemStack.EMPTY);

        AdventureParty party = PartyManager.getParty(viewer);
        ItemStack meta = tagged(Items.NETHER_STAR, "meta");
        CompoundTag mt = meta.getOrCreateTag();
        mt.putBoolean("HasParty", party != null);
        mt.putBoolean("HasInvite", PartyManager.hasPendingInvite(viewer.getUUID()));
        mt.putString("ViewerUuid", viewer.getUUID().toString());
        mt.putString("ViewerName", viewer.getGameProfile().getName());
        String stationDungeonId = stationDungeonId(viewer);
        DungeonDefinition stationDef = DungeonConfig.get(stationDungeonId);
        mt.putString("StationDungeonId", stationDungeonId);
        mt.putBoolean("StationBound", stationDef != null);
        if (stationDef != null) mt.putString("StationDungeonName", stationDef.displayName());

        if (party != null) {
            String leaderName = resolveName(viewer, party.leader());
            mt.putString("PartyId", party.id().toString());
            mt.putString("LeaderUuid", party.leader().toString());
            mt.putString("LeaderName", leaderName);
            mt.putString("PartyName", leaderName + "的小队");
            mt.putString("SelectedDungeon", party.selectedDungeon());
            mt.putBoolean("ViewerLeader", party.isLeader(viewer.getUUID()));
            mt.putBoolean("ViewerReady", party.isReady(viewer.getUUID()));
            mt.putBoolean("AllReady", party.allReady());
            mt.putBoolean("Locked", DungeonManager.isPartyInDungeon(party.id()));
            mt.putInt("MemberTotal", party.members().size());

            int memberIndex = 0;
            for (UUID uuid : party.members()) {
                if (memberIndex >= MEMBER_COUNT) break;
                int actionIndex = memberIndex;
                memberTargets.put(actionIndex, uuid);
                ServerPlayer online = viewer.server.getPlayerList().getPlayer(uuid);
                ItemStack member = tagged(Items.PLAYER_HEAD, "member");
                CompoundTag t = member.getOrCreateTag();
                t.putString("Uuid", uuid.toString());
                t.putString("Name", resolveName(viewer, uuid));
                t.putBoolean("Leader", party.isLeader(uuid));
                t.putBoolean("Ready", party.isReady(uuid));
                t.putBoolean("Online", online != null);
                t.putInt("Index", actionIndex);
                state.setItem(MEMBER_START + memberIndex, member);
                memberIndex++;
            }

            if (party.isLeader(viewer.getUUID()) && !DungeonManager.isPartyInDungeon(party.id())) {
                int inviteIndex = 0;
                List<ServerPlayer> nearby = nearbyPlayers(viewer);
                nearby.sort(Comparator.comparingDouble(this::distanceToStationSqr));
                for (ServerPlayer target : nearby) {
                    if (inviteIndex >= INVITE_COUNT) break;
                    if (target == viewer || party.isMember(target.getUUID()) || PartyManager.getParty(target) != null) continue;
                    inviteTargets.put(inviteIndex, target.getUUID());
                    ItemStack invite = tagged(Items.ENDER_EYE, "invite");
                    CompoundTag t = invite.getOrCreateTag();
                    t.putString("Uuid", target.getUUID().toString());
                    t.putString("Name", target.getGameProfile().getName());
                    t.putInt("Index", inviteIndex);
                    state.setItem(INVITE_START + inviteIndex, invite);
                    inviteIndex++;
                }
            }
        }
        state.setItem(META_SLOT, meta);

        writeDungeonState(BOUND_DUNGEON_SLOT, stationDef, party, stationDungeonId);
        state.setItem(DUNGEON_SAKURA_SLOT, ItemStack.EMPTY);
        broadcastChanges();
    }

    private void writeDungeonState(int slot, DungeonDefinition def, AdventureParty party, String id) {
        ItemStack stack = tagged(def != null && def.enabled() ? Items.COMPASS : Items.BARRIER, "dungeon");
        CompoundTag t = stack.getOrCreateTag();
        t.putString("Id", id);
        t.putBoolean("Enabled", def != null && def.enabled());
        t.putBoolean("Selected", party != null && id.equalsIgnoreCase(party.selectedDungeon()));
        if (def != null) {
            t.putString("DisplayName", def.displayName());
            t.putString("Boss", def.bossEntity());
            t.putInt("MinPlayers", def.minPlayers());
            t.putInt("MaxPlayers", def.maxPlayers());
            t.putInt("TimeLimit", def.timeLimitSeconds());
            t.putString("ReviveMode", def.reviveMode());
            t.putInt("FixedRevives", def.fixedRevives());
            t.putInt("Experience", def.experience());
        } else {
            t.putString("DisplayName", id == null || id.isBlank() ? "未绑定副本" : id);
        }
        state.setItem(slot, stack);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        boolean handled = true;
        switch (id) {
            case ACTION_CREATE -> PartyManager.create(serverPlayer, stationDungeonId(serverPlayer));
            case ACTION_ACCEPT -> PartyManager.accept(serverPlayer, stationDungeonId(serverPlayer));
            case ACTION_READY -> PartyManager.toggleReady(serverPlayer);
            case ACTION_LEAVE -> PartyManager.leave(serverPlayer);
            case ACTION_START -> DungeonManager.startDungeonFromPillar(serverPlayer, stationPos);
            case ACTION_SELECT_CLEOPATRA, ACTION_SELECT_SAKURA -> {
                serverPlayer.sendSystemMessage(Component.literal("§e副本由当前柱子固定绑定，不能在GUI里切换。"));
            }
            case ACTION_REFRESH -> { }
            default -> {
                if (id >= ACTION_INVITE_BASE && id < ACTION_INVITE_BASE + INVITE_COUNT) {
                    int inviteIndex = id - ACTION_INVITE_BASE;
                    UUID targetId = inviteTargets.get(inviteIndex);

                    if (targetId == null) {
                        // 客户端列表与服务端列表刚好发生刷新时，旧版本会直接什么都不做，
                        // 玩家就会误以为已经邀请成功。现在明确提示并立即刷新列表。
                        serverPlayer.sendSystemMessage(Component.literal(
                                "§6[副本系统]§e邀请列表刚刚发生变化，已自动刷新，请重新点击一次目标玩家。"
                        ));
                        refresh(serverPlayer);
                    } else {
                        ServerPlayer target = serverPlayer.server.getPlayerList().getPlayer(targetId);
                        if (target == null) {
                            serverPlayer.sendSystemMessage(Component.literal(
                                    "§6[副本系统]§c该玩家已经离线，无法发送邀请。"
                            ));
                            refresh(serverPlayer);
                        } else if (!isWithinInviteRange(target)) {
                            serverPlayer.sendSystemMessage(Component.literal(
                                    "§6[副本系统]§c该玩家已经离开副本柱子20格范围，无法邀请。"
                            ));
                            refresh(serverPlayer);
                        } else {
                            // PartyManager.invite() 会登记服务端待处理邀请，
                            // 并立即向目标玩家发送聊天栏 + 动作栏提示。
                            PartyManager.invite(serverPlayer, target);
                        }
                    }
                } else if (id >= ACTION_KICK_BASE && id < ACTION_KICK_BASE + MEMBER_COUNT) {
                    UUID targetId = memberTargets.get(id - ACTION_KICK_BASE);
                    if (targetId != null) PartyManager.kick(serverPlayer, targetId.toString());
                } else if (id >= ACTION_LEADER_BASE && id < ACTION_LEADER_BASE + MEMBER_COUNT) {
                    UUID targetId = memberTargets.get(id - ACTION_LEADER_BASE);
                    ServerPlayer target = targetId == null ? null : serverPlayer.server.getPlayerList().getPlayer(targetId);
                    if (target != null) PartyManager.transferLeader(serverPlayer, target);
                    else serverPlayer.sendSystemMessage(Component.literal("§c只能把队长转让给当前在线的队员。"));
                } else {
                    handled = false;
                }
            }
        }
        if (serverPlayer.containerMenu == this) refresh(serverPlayer);
        return handled;
    }

    public ItemStack stateStack(int slot) {
        if (slot < 0 || slot >= STATE_SIZE) return ItemStack.EMPTY;
        return getSlot(slot).getItem();
    }

    private static ItemStack tagged(net.minecraft.world.item.Item item, String type) {
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putString("YDType", type);
        return stack;
    }

    private static String resolveName(ServerPlayer viewer, UUID uuid) {
        ServerPlayer online = viewer.server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getGameProfile().getName();
        if (viewer.server.getProfileCache() != null) {
            Optional<GameProfile> cached = viewer.server.getProfileCache().get(uuid);
            if (cached.isPresent() && cached.get().getName() != null) return cached.get().getName();
        }
        return uuid.toString().substring(0, 8);
    }

    private String stationDungeonId(ServerPlayer viewer) {
        if (viewer == null || !viewer.level().getBlockState(stationPos).is(ModBlocks.MEET_STONE.get())) return "";
        if (viewer.level().getBlockEntity(stationPos) instanceof MeetStoneBlockEntity stone) return stone.getDungeonId();
        return "";
    }

    private List<ServerPlayer> nearbyPlayers(ServerPlayer viewer) {
        ServerLevel level = viewer.serverLevel();
        double cx = stationPos.getX() + 0.5D;
        double cy = stationPos.getY() + 0.5D;
        double cz = stationPos.getZ() + 0.5D;
        AABB box = new AABB(
                cx - INVITE_RADIUS, cy - INVITE_RADIUS, cz - INVITE_RADIUS,
                cx + INVITE_RADIUS, cy + INVITE_RADIUS, cz + INVITE_RADIUS);
        return level.getEntitiesOfClass(ServerPlayer.class, box, this::isWithinInviteRange);
    }

    private boolean isWithinInviteRange(ServerPlayer target) {
        return target != null && distanceToStationSqr(target) <= INVITE_RADIUS * INVITE_RADIUS;
    }

    private double distanceToStationSqr(ServerPlayer target) {
        double cx = stationPos.getX() + 0.5D;
        double cy = stationPos.getY() + 0.5D;
        double cz = stationPos.getZ() + 0.5D;
        return target.distanceToSqr(cx, cy, cz);
    }

    @Override
    public boolean stillValid(Player player) {
        if (!player.level().getBlockState(stationPos).is(ModBlocks.MEET_STONE.get())) return false;
        double cx = stationPos.getX() + 0.5D;
        double cy = stationPos.getY() + 0.5D;
        double cz = stationPos.getZ() + 0.5D;
        return player.distanceToSqr(cx, cy, cz) <= MENU_USE_RADIUS_SQR;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    private static final class StateSlot extends Slot {
        StateSlot(SimpleContainer container, int index) {
            super(container, index, -10000, -10000);
        }

        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean mayPickup(Player player) { return false; }
    }
}
