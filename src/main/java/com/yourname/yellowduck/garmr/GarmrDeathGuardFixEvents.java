package com.yourname.yellowduck.garmr;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.config.EntityTuningConfig;
import com.yourname.yellowduck.dungeon.DungeonManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 修复恐惧之地玩家死亡后不生成亡灵战士。
 *
 * 原 GarmrCombatEvents#findBossFor 会调用 boss.isParticipant(player)，
 * 而 isParticipant -> isValidHatredPlayer 要求 player.isAlive()。
 * LivingDeathEvent 触发时玩家已经死亡，因此原逻辑永远找不到 Boss。
 *
 * 本事件专门为“死亡瞬间”使用不要求 isAlive 的副本归属判断。
 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class GarmrDeathGuardFixEvents {
    private static final String SPAWNED_AT = "YellowDuckGarmrDeathGuardSpawnedAt";

    private GarmrDeathGuardFixEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void spawnDeathGuardForDeadPlayer(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        long now = level.getGameTime();

        // 防止同一个死亡事件被其它兼容路径重复处理。
        if (player.getPersistentData().getLong(SPAWNED_AT) == now) return;

        GarmrBoss boss = findBossForDeadPlayer(level, player);
        if (boss == null) return;

        GarmrHelperEntity guard = GarmrContent.HELPER.get().create(level);
        if (guard == null) return;

        guard.setVariant(GarmrHelperEntity.DEATH_GUARD);
        guard.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        guard.setPersistenceRequired();

        guard.getPersistentData().putString(GarmrBoss.TAG_ROLE, GarmrBoss.ROLE_DEATH_GUARD);
        guard.getPersistentData().putUUID(GarmrBoss.TAG_OWNER, boss.getUUID());

        if (boss.getPersistentData().hasUUID("YellowDuckDungeon")) {
            guard.getPersistentData().putUUID(
                    "YellowDuckDungeon",
                    boss.getPersistentData().getUUID("YellowDuckDungeon")
            );
        }

        double maxHealth = EntityTuningConfig.configured(
                "garmr_death_guard", "max_health", GarmrConfig.DEATH_GUARD_HEALTH);
        double attackDamage = EntityTuningConfig.configured(
                "garmr_death_guard", "attack_damage", GarmrConfig.DEATH_GUARD_ATTACK);
        int defense = (int) Math.round(EntityTuningConfig.configured(
                "garmr_death_guard", "armor", GarmrConfig.DEATH_GUARD_DEFENSE));
        int attackLevel = (int) Math.round(EntityTuningConfig.configured(
                "garmr_death_guard", "attack_level", GarmrConfig.DEATH_GUARD_ATTACK_LEVEL));
        int defenseLevel = (int) Math.round(EntityTuningConfig.configured(
                "garmr_death_guard", "defense_level", GarmrConfig.DEATH_GUARD_DEFENSE_LEVEL));
        double knockbackResistance = EntityTuningConfig.configured(
                "garmr_death_guard", "knockback_resistance", 1.0D);

        AttributeInstance health = guard.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(maxHealth);
            guard.setHealth((float) maxHealth);
        }

        AttributeInstance damage = guard.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) damage.setBaseValue(attackDamage);

        AttributeInstance armor = guard.getAttribute(Attributes.ARMOR);
        if (armor != null) armor.setBaseValue(defense);

        AttributeInstance knockback = guard.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockback != null) knockback.setBaseValue(knockbackResistance);

        guard.getPersistentData().putInt(GarmrBoss.TAG_DEFENSE, defense);
        guard.getPersistentData().putInt(GarmrBoss.TAG_ATTACK_LEVEL, attackLevel);
        guard.getPersistentData().putInt(GarmrBoss.TAG_DEFENSE_LEVEL, defenseLevel);

        if (level.addFreshEntity(guard)) {
            player.getPersistentData().putLong(SPAWNED_AT, now);
        }
    }

    /**
     * 死亡事件专用匹配：
     * - 不要求 player.isAlive()
     * - 副本内严格匹配 YellowDuckDungeon 实例 ID
     * - 非副本测试环境则允许匹配 96 格内存活的加姆
     */
    private static GarmrBoss findBossForDeadPlayer(ServerLevel level, ServerPlayer player) {
        var instance = DungeonManager.instanceOf(player);

        return level.getEntitiesOfClass(
                        GarmrBoss.class,
                        player.getBoundingBox().inflate(96.0D),
                        GarmrBoss::isAlive
                )
                .stream()
                .filter(boss -> {
                    if (boss.getPersistentData().hasUUID("YellowDuckDungeon")) {
                        return instance != null
                                && instance.id.equals(
                                boss.getPersistentData().getUUID("YellowDuckDungeon"));
                    }

                    return !player.isRemoved()
                            && !player.isCreative()
                            && !player.isSpectator()
                            && player.level() == boss.level()
                            && player.distanceToSqr(boss) <= 96.0D * 96.0D;
                })
                .findFirst()
                .orElse(null);
    }
}
