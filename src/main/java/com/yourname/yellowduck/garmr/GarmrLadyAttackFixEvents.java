package com.yourname.yellowduck.garmr;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.config.EntityTuningConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 冰/火亡灵夫人攻击补偿。
 *
 * 原 GarmrBoss 已负责 <=3.5 格的每秒 AOE；
 * 本类补足模型/寻路造成的 3.5~6 格死区，并持续追最高仇恨目标。
 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class GarmrLadyAttackFixEvents {
    private static final double ORIGINAL_RADIUS = GarmrConfig.LADY_AOE_RADIUS;
    private static final double CONTACT_RADIUS = 6.0D;

    private GarmrLadyAttackFixEvents() {}

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (Entity raw : level.getAllEntities()) {
                if (!(raw instanceof GarmrHelperEntity lady)) continue;
                if (!lady.isLady() || !lady.isAlive()) continue;
                if (!lady.getPersistentData().hasUUID(GarmrBoss.TAG_OWNER)) continue;

                Entity owner = level.getEntity(
                        lady.getPersistentData().getUUID(GarmrBoss.TAG_OWNER));
                if (!(owner instanceof GarmrBoss boss) || !boss.isAlive()) continue;

                ServerPlayer target =
                        boss.getHatredManager().getHighestHatredTarget()
                                instanceof ServerPlayer p && boss.isParticipant(p)
                                ? p : nearestParticipant(boss, lady);

                if (target != null) {
                    lady.getNavigation().moveTo(target, 1.15D);
                    lady.getLookControl().setLookAt(target, 90.0F, 90.0F);
                }

                // 每秒结算一次。
                if (event.getServer().getTickCount() % 20 != 0) continue;

                int type = lady.getPersistentData().getInt(GarmrBoss.TAG_LADY_TYPE);
                String section = type == GarmrBoss.BREATH_FIRE
                        ? "garmr_fire_lady" : "garmr_ice_lady";

                int born = lady.getPersistentData().getInt(
                        GarmrBoss.TAG_LADY_SPAWN_TICK);

                // born 保存的是 Boss.tickCount，旧版错误用了 lady.tickCount - born。
                int elapsedSeconds =
                        Math.max(0, (boss.tickCount - born) / 20);

                float damage = (float) EntityTuningConfig.configured(
                        section, "attack_damage", GarmrConfig.LADY_BASE_DAMAGE);
                damage *= 1.0F
                        + elapsedSeconds * GarmrConfig.LADY_DAMAGE_GROWTH_PER_SECOND;

                double innerSq = ORIGINAL_RADIUS * ORIGINAL_RADIUS;
                double outerSq = CONTACT_RADIUS * CONTACT_RADIUS;

                for (ServerPlayer player : boss.participants()) {
                    double d2 = player.distanceToSqr(lady);

                    // <=3.5 格由 GarmrBoss 原本 AOE 负责，防止一秒吃两次。
                    if (d2 <= innerSq || d2 > outerSq) continue;

                    float actual =
                            Netcraft123CombatBridge.applyMinionTierSuppression(
                                    player,
                                    damage,
                                    section,
                                    GarmrConfig.LADY_ATTACK_LEVEL
                            );

                    if (boss.damageNoKnockback(player, actual)) {
                        level.sendParticles(
                                type == GarmrBoss.BREATH_FIRE
                                        ? ParticleTypes.FLAME
                                        : ParticleTypes.SNOWFLAKE,
                                player.getX(),
                                player.getY() + 1.0D,
                                player.getZ(),
                                10,
                                0.35D, 0.45D, 0.35D,
                                0.02D
                        );
                    }
                }
            }
        }
    }

    private static ServerPlayer nearestParticipant(
            GarmrBoss boss, GarmrHelperEntity lady) {
        ServerPlayer nearest = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer player : boss.participants()) {
            double d2 = player.distanceToSqr(lady);
            if (d2 < best) {
                best = d2;
                nearest = player;
            }
        }
        return nearest;
    }
}
