package com.yourname.yellowduck.silk;

import com.yourname.yellowduck.network.MountNetwork;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingUseTotemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 斯尔克战斗的跨实体事件：心智复活锁、召唤物生命周期、史莱姆死亡惩罚。 */
@Mod.EventBusSubscriber(modid = "yellowduck")
public final class SilkCombatEvents {
    private static final String KEY = "YellowduckSilkReviveLock";
    private static final String SLIME_OWNER = "SilkOwner";
    private static final String PROFESSOR_SLIME = "SilkProfessorSlime";
    private static final String SAFE_FLAME_KILL = "SilkFlameSafeKill";
    private static final String EXPLOSION_HANDLED = "SilkExplosionHandled";

    public static final double PROFESSOR_SLIME_HEALTH = 3000.0D;
    public static final double SLIME_PUNISH_RANGE = 50.0D;
    public static final float SLIME_PUNISH_DAMAGE = 85.0F;

    private SilkCombatEvents() {}

    public static long clock(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }

    public static void lock(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData().getCompound(KEY);
        long until = Math.max(tag.getLong("Until"), clock(player) + SilkBalance.REVIVE_LOCK_TICKS);
        tag.putLong("Until", until);
        player.getPersistentData().put(KEY, tag);
        player.displayClientMessage(Component.literal("§4心智腐蚀：NetCraft原地复活禁用20秒"), false);
        MountNetwork.sendSilkReviveLock(player, SilkBalance.REVIVE_LOCK_TICKS);
    }

    public static boolean locked(ServerPlayer player) {
        return player.getPersistentData().getCompound(KEY).getLong("Until") > clock(player);
    }

    @SubscribeEvent
    public static void totem(LivingUseTotemEvent event) {
        // 心智腐蚀死亡不允许图腾绕过 NetCraft 的死亡界面。
        if (event.getEntity() instanceof ServerPlayer player && locked(player)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        if (event.getOriginal().getPersistentData().contains(KEY)) {
            event.getEntity().getPersistentData().put(KEY,
                    event.getOriginal().getPersistentData().getCompound(KEY).copy());
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        CompoundTag tag = player.getPersistentData().getCompound(KEY);
        if (!tag.contains("Until")) return;
        long remaining = tag.getLong("Until") - clock(player);
        if (remaining <= 0L) {
            player.getPersistentData().remove(KEY);
            MountNetwork.sendSilkReviveLock(player, 0);
        } else if (player.tickCount % 20 == 0) {
            player.displayClientMessage(Component.literal(
                    "§4心智腐蚀：原地复活还需 " + ((remaining + 19L) / 20L) + " 秒"), true);
        }
    }

    /** 保留旧实现：通过 NBT 改 Slime 尺寸，避免直接依赖受保护的 setSize。 */
    public static void resizeSlime(Slime slime, int size) {
        CompoundTag saved = new CompoundTag();
        slime.addAdditionalSaveData(saved);
        saved.putInt("Size", size - 1);
        slime.readAdditionalSaveData(saved);
        if (slime.isAlive()) slime.setHealth(slime.getMaxHealth());
    }

    /** 教授召唤的特殊史莱姆：3000血，任何非教授喷火死亡都会触发惩罚爆炸。 */
    public static void configureProfessorSlime(SilkBoss boss, Slime slime) {
        resizeSlime(slime, 2);
        slime.setPersistenceRequired();
        slime.getPersistentData().putUUID(SLIME_OWNER, boss.getUUID());
        slime.getPersistentData().putBoolean(PROFESSOR_SLIME, true);
        var maxHealth = slime.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(PROFESSOR_SLIME_HEALTH);
        slime.setHealth((float) PROFESSOR_SLIME_HEALTH);
        slime.setCustomName(Component.literal("不稳定史莱姆"));
        slime.setCustomNameVisible(true);
    }

    public static void killProfessorSlimeByFlame(SilkBoss boss, Slime slime) {
        if (!slime.isAlive() || !isOwnedProfessorSlime(slime, boss)) return;
        slime.getPersistentData().putBoolean(SAFE_FLAME_KILL, true);
        // 先清尺寸，避免原版大史莱姆死亡时继续分裂。
        resizeSlime(slime, 1);
        // 用极高伤害结束；safe 标记确保 LivingDeathEvent 不会触发85点爆炸。
        slime.hurt(boss.damageSources().mobAttack(boss), Float.MAX_VALUE);
        if (slime.isAlive()) slime.kill();
    }

    private static boolean isOwnedProfessorSlime(Slime slime, SilkBoss boss) {
        CompoundTag data = slime.getPersistentData();
        return data.getBoolean(PROFESSOR_SLIME)
                && data.hasUUID(SLIME_OWNER)
                && data.getUUID(SLIME_OWNER).equals(boss.getUUID());
    }

    @SubscribeEvent
    public static void summonTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        var mob = event.getEntity();
        if (!(mob.level() instanceof ServerLevel sl) || mob.tickCount % 20 != 0) return;
        if (mob.getPersistentData().hasUUID(SLIME_OWNER)) {
            Entity owner = sl.getEntity(mob.getPersistentData().getUUID(SLIME_OWNER));
            if (!(owner instanceof SilkBoss boss) || !boss.isAlive()) mob.discard();
        }
    }

    @SubscribeEvent
    public static void slimeDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Slime slime) || slime.level().isClientSide) return;
        CompoundTag data = slime.getPersistentData();
        if (!data.getBoolean(PROFESSOR_SLIME) || !data.hasUUID(SLIME_OWNER)) return;

        // 先缩成1，阻止原版大史莱姆死亡分裂。
        resizeSlime(slime, 1);

        if (data.getBoolean(SAFE_FLAME_KILL) || data.getBoolean(EXPLOSION_HANDLED)) return;
        data.putBoolean(EXPLOSION_HANDLED, true);

        if (!(slime.level() instanceof ServerLevel serverLevel)) return;
        Entity owner = serverLevel.getEntity(data.getUUID(SLIME_OWNER));
        if (!(owner instanceof SilkBoss boss) || !boss.isAlive()) return;
        punishProfessorSlimeDeath(serverLevel, boss, slime.position());
    }

    private static void punishProfessorSlimeDeath(ServerLevel level, SilkBoss boss, Vec3 explosionPoint) {
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                explosionPoint.x, explosionPoint.y + 0.5D, explosionPoint.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, net.minecraft.core.BlockPos.containing(explosionPoint),
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4.0F, 0.75F);

        AABB area = new AABB(boss.position(), boss.position()).inflate(SLIME_PUNISH_RANGE);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area,
                p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(boss) <= SLIME_PUNISH_RANGE * SLIME_PUNISH_RANGE)) {
            applyFixedHealthDamage(player, SLIME_PUNISH_DAMAGE);
            player.displayClientMessage(Component.literal("§4不稳定史莱姆爆炸：受到85点固定伤害！"), true);
        }
    }

    /**
     * 直接按当前生命扣除固定值，不经过护甲、抗性、NetCraft装备减伤或受伤无敌帧。
     * 这样无论 NetCraft 侧怎么改 LivingHurtEvent，结果都严格是 -85 当前生命。
     */
    private static void applyFixedHealthDamage(ServerPlayer player, float amount) {
        if (!player.isAlive() || amount <= 0.0F) return;
        Vec3 oldMotion = player.getDeltaMovement();
        float remaining = player.getHealth() - amount;
        if (remaining <= 0.0F) {
            player.kill();
        } else {
            player.setHealth(remaining);
            player.hurtMarked = true;
        }
        player.setDeltaMovement(oldMotion);
    }

    @SubscribeEvent
    public static void summonDrops(LivingDropsEvent event) {
        if (event.getEntity().getPersistentData().hasUUID(SLIME_OWNER)) event.setCanceled(true);
    }
}
