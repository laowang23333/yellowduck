package com.yourname.yellowduck.party;

import com.yourname.yellowduck.dungeon.DungeonConfig;
import com.yourname.yellowduck.dungeon.DungeonDefinition;
import com.yourname.yellowduck.dungeon.DungeonManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 队伍的创建、邀请、准备、选本与离队逻辑。 */
public final class PartyManager {
    private static final Map<UUID, UUID> INVITES = new HashMap<>(); // target -> partyId

    private PartyManager() {}

    public static AdventureParty getParty(ServerPlayer player) {
        PartySavedData data = PartySavedData.get(player.server);
        UUID partyId = data.playerIndex.get(player.getUUID());
        return partyId == null ? null : data.parties.get(partyId);
    }

    public static AdventureParty getParty(MinecraftServer server, UUID partyId) {
        return partyId == null ? null : PartySavedData.get(server).parties.get(partyId);
    }

    public static AdventureParty create(ServerPlayer leader) {
        DungeonConfig.ensureLoaded();
        String dungeonId = DungeonConfig.enabledDungeons().isEmpty() ? "" : DungeonConfig.enabledDungeons().get(0).id();
        return create(leader, dungeonId);
    }

    /** 由副本柱子创建队伍，队伍从创建开始就固定到该柱子对应的副本。 */
    public static AdventureParty create(ServerPlayer leader, String dungeonId) {
        PartySavedData data = PartySavedData.get(leader.server);
        AdventureParty existing = getParty(leader);
        if (existing != null) return existing;
        DungeonDefinition def = DungeonConfig.get(dungeonId);
        if (def == null || !def.enabled()) {
            leader.sendSystemMessage(Component.literal("§c这根柱子绑定的副本不存在或未启用。"));
            return null;
        }
        AdventureParty party = new AdventureParty(UUID.randomUUID(), leader.getUUID());
        party.setSelectedDungeon(def.id());
        data.parties.put(party.id(), party);
        data.playerIndex.put(leader.getUUID(), party.id());
        data.setDirty();
        refreshOpenMenus(leader.server, party);
        leader.sendSystemMessage(Component.literal("§a已创建“" + def.displayName() + "”冒险队伍。"));
        return party;
    }

    /**
     * 队长右键另一根已绑定柱子时切换队伍目标副本。
     * 这里只允许未开本状态，切换后清空所有准备状态和旧邀请。
     */
    public static boolean bindPartyToDungeon(ServerPlayer leader, String dungeonId) {
        AdventureParty party = getParty(leader);
        if (party == null || !party.isLeader(leader.getUUID())) return false;
        if (DungeonManager.isPartyInDungeon(party.id())) {
            leader.sendSystemMessage(Component.literal("§c副本进行中不能切换副本柱子。"));
            return false;
        }
        DungeonDefinition def = DungeonConfig.get(dungeonId);
        if (def == null || !def.enabled()) {
            leader.sendSystemMessage(Component.literal("§c目标副本不存在或未启用。"));
            return false;
        }
        if (def.id().equalsIgnoreCase(party.selectedDungeon())) return true;
        party.setSelectedDungeon(def.id());
        invalidateInvitesForParty(party.id());
        PartySavedData.get(leader.server).setDirty();
        refreshOpenMenus(leader.server, party);
        broadcast(leader.server, party, Component.literal("§b队伍已切换到副本柱子：§f" + def.displayName() + "§b，准备状态已重置。"));
        return true;
    }

    public static boolean invite(ServerPlayer leader, ServerPlayer target) {
        AdventureParty party = getParty(leader);
        if (party == null || !party.isLeader(leader.getUUID())) {
            leader.sendSystemMessage(Component.literal("§c只有队长可以邀请玩家。"));
            return false;
        }
        if (DungeonManager.isPartyInDungeon(party.id())) {
            leader.sendSystemMessage(Component.literal("§c副本进行中队伍已锁定，不能修改队伍成员。"));
            return false;
        }
        if (getParty(target) != null) {
            leader.sendSystemMessage(Component.literal("§c该玩家已经在其他队伍中。"));
            return false;
        }
        DungeonDefinition def = DungeonConfig.get(party.selectedDungeon());
        int max = def == null ? 5 : def.maxPlayers();
        if (party.members().size() >= max) {
            leader.sendSystemMessage(Component.literal("§c队伍人数已经达到当前副本上限。"));
            return false;
        }
        // 先在服务端登记邀请，再向被邀请玩家主动推送明确的邀请提示。
        // INVITES 是服务端权威状态；玩家之后打开对应副本柱子的组队界面时，
        // PartyMenu 会读取 hasPendingInvite() 并显示“接受邀请”按钮。
        INVITES.put(target.getUUID(), party.id());

        String inviterName = leader.getGameProfile().getName();
        leader.sendSystemMessage(Component.literal("§a已邀请 §f" + target.getGameProfile().getName() + " §a加入队伍。"));

        // 聊天框邀请请求：按需求显示邀请人 ID，并额外说明接受方式。
        target.sendSystemMessage(Component.literal(
                "§6[副本系统]§a玩家[§4" + inviterName + "§a]§e邀请你加入队伍"
        ));
        target.sendSystemMessage(Component.literal(
                "§6[副本系统]§e请右键对应副本柱子，在组队界面点击§a“接受邀请”§e。"
        ));

        // 再发一条动作栏提醒，避免玩家聊天滚动过快导致看不到邀请请求。
        target.displayClientMessage(Component.literal(
                "§6[副本系统] §e收到来自 §c" + inviterName + " §e的组队邀请"
        ), true);

        // 如果被邀请玩家此时正好开着组队界面，立即刷新，不必等下一次周期刷新。
        if (target.containerMenu instanceof PartyMenu menu) menu.refresh(target);
        return true;
    }


    /** 自定义GUI用于显示“接受邀请”按钮。 */
    public static boolean hasPendingInvite(UUID playerId) {
        return playerId != null && INVITES.containsKey(playerId);
    }

    public static boolean accept(ServerPlayer player) {
        return accept(player, null);
    }

    /** 接受邀请时可要求必须站在与该队伍相同副本ID的柱子旁，防止从别的Boss柱子加入。 */
    public static boolean accept(ServerPlayer player, String stationDungeonId) {
        UUID partyId = INVITES.get(player.getUUID());
        if (partyId == null) {
            player.sendSystemMessage(Component.literal("§c你当前没有待处理的队伍邀请。"));
            return false;
        }
        if (getParty(player) != null) {
            player.sendSystemMessage(Component.literal("§c你已经在一个队伍中。"));
            return false;
        }
        PartySavedData data = PartySavedData.get(player.server);
        AdventureParty party = data.parties.get(partyId);
        if (party == null) {
            INVITES.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("§c这个队伍已经不存在。"));
            return false;
        }
        if (DungeonManager.isPartyInDungeon(party.id())) {
            INVITES.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("§c这个队伍已经进入副本，本次旧邀请已失效。"));
            return false;
        }
        if (stationDungeonId != null && !stationDungeonId.isBlank()
                && !stationDungeonId.equalsIgnoreCase(party.selectedDungeon())) {
            DungeonDefinition current = DungeonConfig.get(party.selectedDungeon());
            player.sendSystemMessage(Component.literal("§c这个邀请属于“"
                    + (current == null ? party.selectedDungeon() : current.displayName())
                    + "”，请到对应副本柱子旁接受。"));
            return false;
        }
        DungeonDefinition def = DungeonConfig.get(party.selectedDungeon());
        int max = def == null ? 5 : def.maxPlayers();
        if (party.members().size() >= max) {
            INVITES.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("§c这个队伍已经满员。"));
            return false;
        }
        INVITES.remove(player.getUUID());
        party.addMember(player.getUUID());
        data.playerIndex.put(player.getUUID(), party.id());
        data.setDirty();
        refreshOpenMenus(player.server, party);
        broadcast(player.server, party, Component.literal("§a" + player.getGameProfile().getName() + " 加入了队伍。"));
        return true;
    }

    public static boolean leave(ServerPlayer player) {
        PartySavedData data = PartySavedData.get(player.server);
        AdventureParty party = getParty(player);
        if (party == null) return false;
        if (DungeonManager.isPartyInDungeon(party.id())) {
            player.sendSystemMessage(Component.literal("§c队伍副本尚未结束，暂时不能退出队伍。"));
            return false;
        }
        UUID id = party.id();
        party.removeMember(player.getUUID());
        data.playerIndex.remove(player.getUUID());
        if (party.members().isEmpty()) data.parties.remove(id);
        data.setDirty();
        refreshOpenMenus(player.server, party);
        player.sendSystemMessage(Component.literal("§e你已离开冒险队伍。"));
        if (!party.members().isEmpty()) broadcast(player.server, party, Component.literal("§e" + player.getGameProfile().getName() + " 离开了队伍。"));
        return true;
    }


    public static boolean kick(ServerPlayer leader, ServerPlayer target) {
        return kickMember(leader, target.getUUID(), target.getGameProfile().getName());
    }

    /**
     * 命令版踢人按名字或UUID解析，不再依赖 EntityArgument.player()，因此离线队员也能移除。
     * 玩家加入过服务器后其 GameProfile 会在服务器缓存中；UUID 参数作为缓存缺失时的兜底。
     */
    public static boolean kick(ServerPlayer leader, String targetNameOrUuid) {
        AdventureParty party = getParty(leader);
        if (party == null || !party.isLeader(leader.getUUID())) {
            leader.sendSystemMessage(Component.literal("§c只有队长可以移除队员。"));
            return false;
        }
        if (DungeonManager.isPartyInDungeon(party.id())) {
            leader.sendSystemMessage(Component.literal("§c副本进行中队伍已锁定，不能修改队伍成员。"));
            return false;
        }

        UUID targetId = null;
        String displayName = targetNameOrUuid;
        for (UUID memberId : party.members()) {
            ServerPlayer online = leader.server.getPlayerList().getPlayer(memberId);
            if (online != null && online.getGameProfile().getName().equalsIgnoreCase(targetNameOrUuid)) {
                targetId = memberId;
                displayName = online.getGameProfile().getName();
                break;
            }
        }
        if (targetId == null) {
            try {
                UUID parsed = UUID.fromString(targetNameOrUuid);
                if (party.isMember(parsed)) targetId = parsed;
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (targetId == null && leader.server.getProfileCache() != null) {
            Optional<com.mojang.authlib.GameProfile> cached = leader.server.getProfileCache().get(targetNameOrUuid);
            if (cached.isPresent() && party.isMember(cached.get().getId())) {
                targetId = cached.get().getId();
                displayName = cached.get().getName();
            }
        }
        if (targetId == null) {
            leader.sendSystemMessage(Component.literal("§c找不到这个队员。离线玩家也可以使用其最后登录名字或UUID。"));
            return false;
        }
        return kickMember(leader, targetId, displayName);
    }

    private static boolean kickMember(ServerPlayer leader, UUID targetId, String displayName) {
        AdventureParty party = getParty(leader);
        if (party == null || !party.isLeader(leader.getUUID())) {
            leader.sendSystemMessage(Component.literal("§c只有队长可以移除队员。"));
            return false;
        }
        if (DungeonManager.isPartyInDungeon(party.id())) {
            leader.sendSystemMessage(Component.literal("§c副本进行中队伍已锁定，不能修改队伍成员。"));
            return false;
        }
        if (!party.isMember(targetId) || targetId.equals(leader.getUUID())) {
            leader.sendSystemMessage(Component.literal("§c该玩家不是可移除的队员。"));
            return false;
        }
        PartySavedData data = PartySavedData.get(leader.server);
        party.removeMember(targetId);
        data.playerIndex.remove(targetId);
        data.setDirty();

        ServerPlayer target = leader.server.getPlayerList().getPlayer(targetId);
        if (target != null) {
            if (target.containerMenu instanceof PartyMenu) target.closeContainer();
            target.sendSystemMessage(Component.literal("§e你已被移出冒险队伍。"));
            displayName = target.getGameProfile().getName();
        }
        refreshOpenMenus(leader.server, party);
        broadcast(leader.server, party, Component.literal("§e" + displayName + " 已被移出队伍。"));
        return true;
    }

    public static boolean transferLeader(ServerPlayer leader, ServerPlayer target) {
        AdventureParty party = getParty(leader);
        if (party == null || !party.isLeader(leader.getUUID())) {
            leader.sendSystemMessage(Component.literal("§c只有队长可以转让队长。"));
            return false;
        }
        if (DungeonManager.isPartyInDungeon(party.id())) {
            leader.sendSystemMessage(Component.literal("§c副本进行中队伍已锁定，不能转让队长。"));
            return false;
        }
        if (!party.isMember(target.getUUID())) {
            leader.sendSystemMessage(Component.literal("§c目标玩家不在你的队伍中。"));
            return false;
        }
        party.setLeader(target.getUUID());
        PartySavedData.get(leader.server).setDirty();
        refreshOpenMenus(leader.server, party);
        broadcast(leader.server, party, Component.literal("§6队长已转让给 §f" + target.getGameProfile().getName()));
        return true;
    }

    public static boolean toggleReady(ServerPlayer player) {
        AdventureParty party = getParty(player);
        if (party == null) {
            player.sendSystemMessage(Component.literal("§c你还没有队伍。"));
            return false;
        }
        if (DungeonManager.isPartyInDungeon(party.id())) {
            player.sendSystemMessage(Component.literal("§c副本进行中队伍已锁定，不能修改准备状态。"));
            return false;
        }
        boolean ready = party.toggleReady(player.getUUID());
        PartySavedData.get(player.server).setDirty();
        refreshOpenMenus(player.server, party);
        broadcast(player.server, party, Component.literal((ready ? "§a" : "§e") + player.getGameProfile().getName() + (ready ? " 已准备。" : " 取消准备。")));
        return ready;
    }

    /** v6 起不允许通过GUI/公共方法直接切本；副本目标只能来自玩家实际右键的已绑定柱子。 */
    public static boolean selectDungeon(ServerPlayer player, String dungeonId) {
        player.sendSystemMessage(Component.literal("§e副本由副本柱子固定绑定，请右键对应Boss的柱子切换。"));
        return false;
    }

    /** 开本时让这个队伍此前发出的所有邀请立即失效。 */
    public static void invalidateInvitesForParty(UUID partyId) {
        if (partyId == null) return;
        INVITES.entrySet().removeIf(entry -> partyId.equals(entry.getValue()));
    }

    public static void clearReady(MinecraftServer server, AdventureParty party) {
        party.clearReady();
        PartySavedData.get(server).setDirty();
        refreshOpenMenus(server, party);
    }

    /** 队伍状态改变时刷新所有仍打开组队GUI的成员。 */
    public static void refreshOpenMenus(MinecraftServer server, AdventureParty party) {
        for (UUID uuid : party.members()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null && player.containerMenu instanceof PartyMenu menu) menu.refresh(player);
        }
    }

    public static void broadcast(MinecraftServer server, AdventureParty party, Component message) {
        for (UUID uuid : party.members()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) player.sendSystemMessage(message);
        }
    }
}
