package com.yourname.yellowduck.garmr;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 加姆副本里不能塞进 Boss.tick 的死亡/伤害事件。 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class GarmrCombatEvents {
    private GarmrCombatEvents() {}

    /** 亡灵夫人不能被玩家直接集火；当前测试版只允许所属 Garmr 的吐息伤害处理她。 */
    @SubscribeEvent
    public static void blockLadyDirectDamage(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Zombie lady)) return;
        if (!"lady_placeholder".equals(lady.getPersistentData().getString("GarmrRole"))) return;
        if (!(event.getSource().getEntity() instanceof GarmrBoss boss) || !boss.isOwnedLady(lady)) {
            event.setCanceled(true);
        }
    }

    /** 玩家死亡点生成骷髅类强敌；最终实体/数值等原资源确认后再替换。 */
    @SubscribeEvent
    public static void playerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        GarmrBoss boss = level.getEntitiesOfClass(GarmrBoss.class,
                        player.getBoundingBox().inflate(80.0D), b -> b.isAlive() && b.isParticipant(player))
                .stream().findFirst().orElse(null);
        if (boss == null) return;

        Skeleton guard = EntityType.SKELETON.create(level);
        if (guard == null) return;
        guard.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        guard.setPersistenceRequired();
        guard.setCustomName(Component.literal("§8骷髅守卫（原实体待解析）"));
        guard.setCustomNameVisible(true);
        guard.getPersistentData().putString("GarmrRole", "death_guard_placeholder");
        guard.getPersistentData().putUUID("GarmrOwner", boss.getUUID());
        if (boss.getPersistentData().hasUUID("YellowDuckDungeon")) {
            guard.getPersistentData().putUUID("YellowDuckDungeon", boss.getPersistentData().getUUID("YellowDuckDungeon"));
        }
        AttributeInstance damage = guard.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) damage.setBaseValue(14.0D); // TODO parsed original value
        AttributeInstance health = guard.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) { health.setBaseValue(80.0D); guard.setHealth(80.0F); } // TODO
        level.addFreshEntity(guard);
    }

    /** 死亡点骷髅攻击玩家时施加迟缓。 */
    @SubscribeEvent
    public static void guardSlow(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Skeleton skeleton)) return;
        if (!"death_guard_placeholder".equals(skeleton.getPersistentData().getString("GarmrRole"))) return;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1, false, true, true));
    }
}
