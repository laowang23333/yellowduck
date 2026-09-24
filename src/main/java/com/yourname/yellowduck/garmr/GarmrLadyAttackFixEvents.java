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
 * 修复冰/火亡灵夫人追到玩家身边却看起来不攻击：
 * 原 Boss AOE 只按实体中心 3.5 格判断，大模型/寻路停止距离会产生明显死区。
 * 这里仅补 3.5~5 格的接触死区；<=3.5 仍由原 GarmrBoss AOE 处理，避免双倍伤害。
 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class GarmrLadyAttackFixEvents {
    private static final double ORIGINAL_RADIUS = GarmrConfig.LADY_AOE_RADIUS;
    private static final double CONTACT_RADIUS = 5.0D;

    private GarmrLadyAttackFixEvents() {}

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.getServer().getTickCount() % 20 != 0) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (Entity raw : level.getAllEntities()) {
                if (!(raw instanceof GarmrHelperEntity lady) || !lady.isLady() || !lady.isAlive()) continue;
                if (!lady.getPersistentData().hasUUID(GarmrBoss.TAG_OWNER)) continue;

                Entity owner = level.getEntity(lady.getPersistentData().getUUID(GarmrBoss.TAG_OWNER));
                if (!(owner instanceof GarmrBoss boss) || !boss.isAlive()) continue;

                ServerPlayer target = boss.getHatredManager().getHighestHatredTarget() instanceof ServerPlayer p ? p : null;
                if (target == null || !boss.isParticipant(target)) continue;

                // 保证亡灵夫人持续追到真正能攻击的位置。
                lady.getNavigation().moveTo(target, 1.05D);

                double d2 = target.distanceToSqr(lady);
                if (d2 <= ORIGINAL_RADIUS * ORIGINAL_RADIUS || d2 > CONTACT_RADIUS * CONTACT_RADIUS) continue;

                int type = lady.getPersistentData().getInt(GarmrBoss.TAG_LADY_TYPE);
                String section = type == GarmrBoss.BREATH_FIRE ? "garmr_fire_lady" : "garmr_ice_lady";
                int born = lady.getPersistentData().getInt(GarmrBoss.TAG_LADY_SPAWN_TICK);
                int elapsedSeconds = Math.max(0, (lady.tickCount - born) / 20);
                float damage = (float) EntityTuningConfig.configured(
                        section, "attack_damage", GarmrConfig.LADY_BASE_DAMAGE)
                        * (1.0F + elapsedSeconds * GarmrConfig.LADY_DAMAGE_GROWTH_PER_SECOND);

                boss.damageNoKnockback(target, damage);
                level.sendParticles(
                        type == GarmrBoss.BREATH_FIRE ? ParticleTypes.FLAME : ParticleTypes.SNOWFLAKE,
                        target.getX(), target.getY() + 1.0D, target.getZ(),
                        8, 0.35D, 0.45D, 0.35D, 0.02D);
            }
        }
    }
}
