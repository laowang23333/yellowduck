package com.yourname.yellowduck.party;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** 持久化冒险队伍数据。 */
public final class AdventureParty {
    private final UUID id;
    private UUID leader;
    private final LinkedHashSet<UUID> members = new LinkedHashSet<>();
    private final LinkedHashSet<UUID> ready = new LinkedHashSet<>();
    private String selectedDungeon = "cleopatra";

    public AdventureParty(UUID id, UUID leader) {
        this.id = id;
        this.leader = leader;
        this.members.add(leader);
    }

    public UUID id() { return id; }
    public UUID leader() { return leader; }
    public Set<UUID> members() { return Collections.unmodifiableSet(new LinkedHashSet<>(members)); }
    public String selectedDungeon() { return selectedDungeon; }
    public boolean isLeader(UUID player) { return leader.equals(player); }
    public boolean isMember(UUID player) { return members.contains(player); }
    public boolean isReady(UUID player) { return ready.contains(player); }

    public void setLeader(UUID leader) {
        if (members.contains(leader)) this.leader = leader;
    }

    public void setSelectedDungeon(String selectedDungeon) {
        if (selectedDungeon != null && !selectedDungeon.isBlank()) this.selectedDungeon = selectedDungeon;
        clearReady();
    }

    public void addMember(UUID player) {
        members.add(player);
        clearReady();
    }

    public void removeMember(UUID player) {
        members.remove(player);
        ready.remove(player);
        if (leader.equals(player) && !members.isEmpty()) leader = members.iterator().next();
        clearReady();
    }

    public boolean toggleReady(UUID player) {
        if (!members.contains(player)) return false;
        if (ready.remove(player)) return false;
        ready.add(player);
        return true;
    }

    public void clearReady() { ready.clear(); }

    public boolean allReady() {
        return !members.isEmpty() && ready.containsAll(members);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("Leader", leader);
        tag.putString("Dungeon", selectedDungeon);
        ListTag membersTag = new ListTag();
        for (UUID member : members) {
            CompoundTag m = new CompoundTag();
            m.putUUID("Uuid", member);
            m.putBoolean("Ready", ready.contains(member));
            membersTag.add(m);
        }
        tag.put("Members", membersTag);
        return tag;
    }

    public static AdventureParty load(CompoundTag tag) {
        UUID id = tag.getUUID("Id");
        UUID leader = tag.getUUID("Leader");
        AdventureParty party = new AdventureParty(id, leader);
        party.members.clear();
        party.ready.clear();
        ListTag list = tag.getList("Members", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag m = list.getCompound(i);
            if (!m.hasUUID("Uuid")) continue;
            UUID uuid = m.getUUID("Uuid");
            party.members.add(uuid);
            if (m.getBoolean("Ready")) party.ready.add(uuid);
        }
        if (!party.members.contains(leader)) party.members.add(leader);
        String dungeon = tag.getString("Dungeon");
        if (!dungeon.isBlank()) party.selectedDungeon = dungeon;
        return party;
    }
}
