package com.yourname.yellowduck.dungeon;

/** 单个副本在开始挑战时使用的配置快照。 */
public record DungeonDefinition(
        String id,
        String displayName,
        String bossEntity,
        String completionType,
        boolean enabled,
        int minPlayers,
        int maxPlayers,
        int timeLimitSeconds,
        int bossSpawnDelaySeconds,
        int rewardPreviewSeconds,
        int wipeCloseSeconds,
        String reviveMode,
        int fixedRevives,
        int experience,
        String itemRules
) {
    public int initialRevives(int playerCount) {
        return "fixed".equalsIgnoreCase(reviveMode)
                ? Math.max(0, fixedRevives)
                : Math.max(0, playerCount);
    }
}
