package com.yourname.yellowduck.client.dungeon;

import com.yourname.yellowduck.network.MountNetwork;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 客户端最近一次副本HUD快照。 */
public final class DungeonHudClientState {
    private static boolean active;
    private static String dungeonName = "";
    private static int remainingSeconds;
    private static int revives;
    private static int maxRevives;
    private static float bossHealth;
    private static float bossMaxHealth;
    private static List<MountNetwork.DungeonHudMember> members = List.of();

    private DungeonHudClientState() {}

    public static void apply(MountNetwork.DungeonHudPacket packet) {
        active = packet.active();
        dungeonName = packet.dungeonName();
        remainingSeconds = packet.remainingSeconds();
        revives = packet.revives();
        maxRevives = packet.maxRevives();
        bossHealth = packet.bossHealth();
        bossMaxHealth = packet.bossMaxHealth();
        members = Collections.unmodifiableList(new ArrayList<>(packet.members()));
    }

    public static void clear() {
        active = false;
        dungeonName = "";
        remainingSeconds = 0;
        revives = 0;
        maxRevives = 0;
        bossHealth = 0;
        bossMaxHealth = 0;
        members = List.of();
    }

    public static boolean active() { return active; }
    public static String dungeonName() { return dungeonName; }
    public static int remainingSeconds() { return remainingSeconds; }
    public static int revives() { return revives; }
    public static int maxRevives() { return maxRevives; }
    public static float bossHealth() { return bossHealth; }
    public static float bossMaxHealth() { return bossMaxHealth; }
    public static List<MountNetwork.DungeonHudMember> members() { return members; }
}
