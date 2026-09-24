package com.yourname.yellowduck.tengu;

import com.yourname.yellowduck.boss.NetcraftBossBase;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 主世界夜间随机刷新的世界 Boss：天狗。
 *
 * 基础规则：100000 血 / 120 攻击；击杀时 10% 生成一只未驯服天狗。
 * 未驯服天狗 3 分钟后自动消失；真正的驯服交互逻辑留到后续版本接入。
 */
public final class TenguBoss extends NetcraftBossBase {
    private boolean mountRewardRolled;

    public TenguBoss(EntityType<? extends TenguBoss> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setBaseDamage((int) TenguConfig.BOSS_ATTACK);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, TenguConfig.BOSS_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, TenguConfig.BOSS_ATTACK)
                .add(Attributes.MOVEMENT_SPEED, TenguConfig.BOSS_SPEED)
                .add(Attributes.FOLLOW_RANGE, TenguConfig.BOSS_FOLLOW_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public Component getName() {
        return Component.literal("天狗");
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    /** 让夜间自然刷新的 Boss 会主动发现附近玩家。 */
    @Override
    public double getDetectionRadius() {
        return 32.0D;
    }

    @Override
    public double getDetectionHatred() {
        return 10.0D;
    }

    @Override
    public double getHatredClearRadius() {
        return 64.0D;
    }

    @Override
    public double getSpawnDistanceLimit() {
        return 48.0D;
    }

    @Override
    public double getNoPlayerDisengageRadius() {
        return 64.0D;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!(target instanceof LivingEntity living) || !living.isAlive()) {
            return false;
        }
        faceTargetForAttack(living);
        return hurtWithoutKnockback(living, damageSources().mobAttack(this), getNetcraftAttackDamage());
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && !mountRewardRolled) {
            mountRewardRolled = true;
            if (getKillCredit() instanceof Player
                    && random.nextFloat() < TenguConfig.MOUNT_SPAWN_CHANCE
                    && level() instanceof ServerLevel server) {
                TenguWildMountEntity mount = TenguContent.WILD_MOUNT.get().create(server);
                if (mount != null) {
                    mount.moveTo(getX(), getY() + 0.1D, getZ(), getYRot(), 0.0F);
                    server.addFreshEntity(mount);
                }
            }
        }
        super.die(source);
    }
}
