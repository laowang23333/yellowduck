package com.yourname.yellowduck.party;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 冒险队伍 SavedData，存放在主世界 data/yellowduck_parties.dat。 */
public final class PartySavedData extends SavedData {
    private static final String ID = "yellowduck_parties";
    final Map<UUID, AdventureParty> parties = new LinkedHashMap<>();
    final Map<UUID, UUID> playerIndex = new LinkedHashMap<>();

    public static PartySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PartySavedData::load, PartySavedData::new, ID);
    }

    public static PartySavedData load(CompoundTag tag) {
        PartySavedData data = new PartySavedData();
        ListTag list = tag.getList("Parties", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            AdventureParty party = AdventureParty.load(list.getCompound(i));
            data.parties.put(party.id(), party);
            for (UUID member : party.members()) data.playerIndex.put(member, party.id());
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (AdventureParty party : parties.values()) list.add(party.save());
        tag.put("Parties", list);
        return tag;
    }
}
