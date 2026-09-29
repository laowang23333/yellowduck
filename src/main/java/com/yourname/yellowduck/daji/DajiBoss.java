package com.yourname.yellowduck.daji;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.boss.NetcraftBossBase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 妲己 Boss。
 *
 * 客户端表能确定：基础属性、技能/CD、Buff持续、召唤物、动作与表现资源。
 * 原活动的服务端 AI 没有下发，因此形态轮转、破盾耐久、召唤数量等放在 DajiConfig 中可调。
 */
public final class DajiBoss extends NetcraftBossBase {
    public static final int FORM_HUMAN = 0, FORM_FOX = 1;

    public static final int ACT_IDLE=0, ACT_WALK=1, ACT_HUMAN_BASIC=2, ACT_WIND=3, ACT_RAGE=4,
            ACT_FOX_BASIC=5, ACT_CHARGE_PREP=6, ACT_CHARGE=7, ACT_RED_POWER=8, ACT_FIRE=9,
            ACT_SHIELD=10, ACT_SUMMON=11;

    public static final EntityDataAccessor<Integer> FORM =
            SynchedEntityData.defineId(DajiBoss.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> ACTION =
            SynchedEntityData.defineId(DajiBoss.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> ACTION_SERIAL =
            SynchedEntityData.defineId(DajiBoss.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Float> SHIELD =
            SynchedEntityData.defineId(DajiBoss.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Integer> RAGE_STACKS =
            SynchedEntityData.defineId(DajiBoss.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> RED_POWER_ACTIVE =
            SynchedEntityData.defineId(DajiBoss.class, EntityDataSerializers.BOOLEAN);

    /** 攻略表现复原：人形召狐 -> 灵狐魅火 -> 螺旋冲撞 -> 护灵镜 -> 回人形。 */
    private enum State { HUMAN, FIRE, SPIRAL, SHIELD }

    private State state = State.HUMAN;
    private int stateTicks;
    private int actionTicks;

    private int basicCd;
    private int windCd;
    private int summonCd;
    private int whiteFoxCd;
    private int chargeCd;
    private int redPowerCd;
    private int spiritFireCd;

    private int redPowerTicks;
    private int spiritFireTicks;
    private int spiritFireSpawnCd;
    private int rageTimer;

    private int pendingHitTicks;
    private int pendingHitKind;
    private UUID pendingTarget;
    private Vec3 pendingPos;

    private int chargePrepTicks;
    private int chargeMoveTicks;
    private Vec3 chargeDirection = Vec3.ZERO;
    private final Set<UUID> chargeHit = new HashSet<>();

    private boolean initialized;
    private double appliedHealth = -1.0D;
    private double appliedAttack = -1.0D;
    private double appliedSpeed = -1.0D;
    private double appliedFollow = -1.0D;

    public DajiBoss(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        DajiConfig.ensureLoaded();
        setPersistenceRequired();
        setBaseTier(5);
        setBaseDamage((int) DajiConfig.bossAttack);
        setBaseDefense(DajiConfig.bossDefense);
        xpReward = 0;
        rageTimer = DajiConfig.rageInterval;
        setCustomName(Component.literal("妲己"));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        DajiConfig.ensureLoaded();
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, DajiConfig.bossHealth)
                .add(Attributes.ATTACK_DAMAGE, DajiConfig.bossAttack)
                .add(Attributes.MOVEMENT_SPEED, DajiConfig.movementSpeed)
                .add(Attributes.FOLLOW_RANGE, DajiConfig.followRange)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(FORM, FORM_HUMAN);
        entityData.define(ACTION, ACT_IDLE);
        entityData.define(ACTION_SERIAL, 0);
        entityData.define(SHIELD, 0.0F);
        entityData.define(RAGE_STACKS, 0);
        entityData.define(RED_POWER_ACTIVE, false);
    }

    public int form(){return entityData.get(FORM);}
    public int action(){return entityData.get(ACTION);}
    public int actionSerial(){return entityData.get(ACTION_SERIAL);}
    public float shield(){return entityData.get(SHIELD);}
    public int rageStacks(){return entityData.get(RAGE_STACKS);}
    public boolean redPowerActive(){return entityData.get(RED_POWER_ACTIVE);}

    @Override public Component getName(){return Component.literal("妲己");}
    @Override public ResourceLocation getBossHudStandaloneIcon(){
        return new ResourceLocation(YellowDuckMod.MOD_ID,"textures/gui/daji_boss_icon.png");
    }

    @Override public int getMeleeDefense(){return DajiConfig.bossDefense;}
    @Override public int getRangedDefense(){return DajiConfig.bossDefense;}
    @Override public int getMagicDefense(){return DajiConfig.bossDefense;}
    @Override public boolean shouldIgnoreSpawnDistanceLimit(){return true;}
    @Override public boolean shouldDisengageOnDistance(){return false;}
    @Override public boolean shouldDisengageOnLowHatred(){return false;}
    @Override public boolean shouldDisengageOnAttackTimeout(){return false;}
    @Override public double getNoPlayerDisengageRadius(){return 100000.0D;}
    @Override public double getDetectionRadius(){return DajiConfig.followRange;}
    @Override public double getDetectionHatred(){return 4.0D;}
    @Override public double getHatredClearRadius(){return Math.max(48.0D, DajiConfig.followRange + 8.0D);}
    @Override public boolean isPushable(){return false;}
    @Override public void push(Entity e){}
    @Override public void push(double x,double y,double z){}
    @Override public boolean isPlayingAttackAnimation(){
        return action()!=ACT_IDLE && action()!=ACT_WALK || chargePrepTicks>0 || chargeMoveTicks>0;
    }

    @Override public float getAttackDamageFor(LivingEntity target,float original){
        return original * (1.0F + rageStacks() * DajiConfig.rageDamagePerStack);
    }

    @Override protected float applyNetcraftIncomingDamage(net.minecraft.world.damagesource.DamageSource source,float amount){
        float result = super.applyNetcraftIncomingDamage(source, amount);
        float reduction = Math.min(DajiConfig.rageReductionCap,
                rageStacks() * DajiConfig.rageReductionPerStack);
        return Math.max(0.1F, result * (1.0F - reduction));
    }

    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){
        if(level().isClientSide) return super.hurt(source, amount);
        if(!isAlive()) return false;

        // 护灵镜：客户端表明确“妲己处于无敌状态”；盾耐久属于当前可配置行为复原值。
        if(shield() > 0.0F){
            float adjusted = applyNetcraftIncomingDamage(source, amount);
            float remain = Math.max(0.0F, shield() - adjusted);
            entityData.set(SHIELD, remain);
            Player attacker = resolvePlayerAttacker(source);
            if(attacker != null) getHatredManager().addDamageHatred(attacker, adjusted);
            noActionTime = 0;
            if(remain <= 0.0F){
                play(ACT_FOX_BASIC, 8);
                // 原 Buff 本身没有持续时间；破盾后立即允许进入下一轮。
                stateTicks = Math.max(stateTicks, DajiConfig.shieldFailsafeTicks);
            }
            return true;
        }

        // 赤狐之力：客户端表明确 8 秒内“受到的伤害将转为治疗”。
        if(redPowerTicks > 0){
            float adjusted = applyNetcraftIncomingDamage(source, amount);
            heal(adjusted);
            Player attacker = resolvePlayerAttacker(source);
            if(attacker != null) getHatredManager().addDamageHatred(attacker, adjusted);
            noActionTime = 0;
            return true;
        }
        return super.hurt(source, amount);
    }

    @Override public void tick(){
        super.tick();
        if(level().isClientSide) return;
        if(!isAlive()) return;

        DajiConfig.reloadIfChanged();
        applyConfigIfNeeded();

        if(actionTicks > 0 && --actionTicks <= 0 && chargePrepTicks <= 0 && chargeMoveTicks <= 0){
            entityData.set(ACTION, ACT_IDLE);
        }
        if(basicCd>0) basicCd--;
        if(windCd>0) windCd--;
        if(summonCd>0) summonCd--;
        if(whiteFoxCd>0) whiteFoxCd--;
        if(chargeCd>0) chargeCd--;
        if(redPowerCd>0) redPowerCd--;
        if(spiritFireCd>0) spiritFireCd--;
        if(redPowerTicks>0 && --redPowerTicks <= 0) {
            redPowerTicks = 0;
            entityData.set(RED_POWER_ACTIVE, false);
        }

        tickPendingHit();
        tickCharge();
        tickSpiritFire();

        LivingEntity target = getAttackTargetEntity();
        if(target == null){
            getNavigation().stop();
            if(chargePrepTicks<=0 && chargeMoveTicks<=0) entityData.set(ACTION, ACT_IDLE);
            return;
        }

        stateTicks++;
        if(state == State.SHIELD){
            if(shield() <= 0.0F || stateTicks >= DajiConfig.shieldFailsafeTicks){
                enter(State.HUMAN);
            }
        }else if(stateTicks >= duration(state) && actionTicks<=0 && chargePrepTicks<=0 && chargeMoveTicks<=0){
            enter(next(state));
        }

        if(actionTicks > 0 || chargePrepTicks > 0 || chargeMoveTicks > 0) return;

        // 24 秒“计时 -> 妲己之怒/狂暴”只在能使用人形技能时推进，避免狐狸形态突然播放人形技能。
        if(state == State.HUMAN){
            if(rageTimer > 0) rageTimer--;
            if(rageTimer <= 0){
                rageTimer = DajiConfig.rageInterval;
                castRage(target);
                return;
            }
        }

        switch(state){
            case HUMAN -> tickHuman(target);
            case FIRE -> tickFire(target);
            case SPIRAL -> tickSpiral(target);
            case SHIELD -> tickShield(target);
        }
    }

    private void applyConfigIfNeeded(){
        boolean first = !initialized;
        if(!first && tickCount % 20 != 0) return;

        float ratio = getMaxHealth() > 0.0F ? getHealth()/getMaxHealth() : 1.0F;
        if(appliedHealth != DajiConfig.bossHealth){
            var hp = getAttribute(Attributes.MAX_HEALTH);
            if(hp != null) hp.setBaseValue(DajiConfig.bossHealth);
            appliedHealth = DajiConfig.bossHealth;
            setHealth(first ? getMaxHealth() : Math.max(0.1F, Math.min(getMaxHealth(), getMaxHealth()*ratio)));
        }
        if(appliedAttack != DajiConfig.bossAttack){
            var ad = getAttribute(Attributes.ATTACK_DAMAGE);
            if(ad != null) ad.setBaseValue(DajiConfig.bossAttack);
            appliedAttack = DajiConfig.bossAttack;
            setBaseDamage((int) DajiConfig.bossAttack);
        }
        if(appliedSpeed != DajiConfig.movementSpeed){
            var ms = getAttribute(Attributes.MOVEMENT_SPEED);
            if(ms != null) ms.setBaseValue(DajiConfig.movementSpeed);
            appliedSpeed = DajiConfig.movementSpeed;
        }
        if(appliedFollow != DajiConfig.followRange){
            var fr = getAttribute(Attributes.FOLLOW_RANGE);
            if(fr != null) fr.setBaseValue(DajiConfig.followRange);
            appliedFollow = DajiConfig.followRange;
        }
        setBaseDefense(DajiConfig.bossDefense);

        if(first){
            initialized = true;
            enter(State.HUMAN);
        }
    }

    private void tickHuman(LivingEntity target){
        entityData.set(FORM, FORM_HUMAN);
        if(windCd <= 0){
            windCd = DajiConfig.windCooldown;
            castWind(target);
            return;
        }
        if(summonCd <= 0){
            summonCd = DajiConfig.summonCooldown;
            spawnHumanFoxWave();
            play(ACT_SUMMON, 20);
            return;
        }
        basicOrMove(target, true);
    }

    private void tickFire(LivingEntity target){
        entityData.set(FORM, FORM_FOX);
        if(spiritFireCd <= 0){
            spiritFireCd = DajiConfig.spiritFireCooldown;
            castSpiritFire(target);
            return;
        }
        if(redPowerCd <= 0){
            redPowerCd = DajiConfig.redPowerCooldown;
            redPowerTicks = DajiConfig.redPowerDuration;
            entityData.set(RED_POWER_ACTIVE, true);
            play(ACT_RED_POWER, 44);
            return;
        }
        if(whiteFoxCd <= 0){
            whiteFoxCd = DajiConfig.whiteFoxCooldown;
            summonWhiteFox();
            play(ACT_SUMMON, 16);
            return;
        }
        basicOrMove(target, false);
    }

    private void tickSpiral(LivingEntity target){
        entityData.set(FORM, FORM_FOX);
        if(chargeCd <= 0){
            chargeCd = DajiConfig.chargeCooldown;
            beginCharge(target);
            return;
        }
        if(redPowerCd <= 0){
            redPowerCd = DajiConfig.redPowerCooldown;
            redPowerTicks = DajiConfig.redPowerDuration;
            entityData.set(RED_POWER_ACTIVE, true);
            play(ACT_RED_POWER, 44);
            return;
        }
        if(whiteFoxCd <= 0){
            whiteFoxCd = DajiConfig.whiteFoxCooldown;
            summonWhiteFox();
            play(ACT_SUMMON, 16);
            return;
        }
        basicOrMove(target, false);
    }

    private void tickShield(LivingEntity target){
        entityData.set(FORM, FORM_FOX);
        basicOrMove(target, false);
    }

    private void basicOrMove(LivingEntity target, boolean human){
        double range = human ? 3.5D : 4.5D;
        if(distanceToSqr(target) > range*range){
            getNavigation().moveTo(target, 1.0D);
            if(action() == ACT_IDLE) entityData.set(ACTION, ACT_WALK);
            return;
        }

        getNavigation().stop();
        if(action() == ACT_WALK) entityData.set(ACTION, ACT_IDLE);
        if(basicCd > 0) return;

        basicCd = DajiConfig.basicCooldown;
        faceTargetForAttack(target);
        play(human ? ACT_HUMAN_BASIC : ACT_FOX_BASIC, human ? 20 : 32);
        pendingHitTicks = human ? 9 : 10;
        pendingHitKind = human ? 1 : 2;
        pendingTarget = target.getUUID();
    }

    private void castWind(LivingEntity target){
        faceTargetForAttack(target);
        play(ACT_WIND, 26);
        pendingPos = target.position();
        // skill.txt delay=670ms，约 13.4 tick；取 14 tick。
        pendingHitTicks = 14;
        pendingHitKind = 3;
        DajiEffectEntity.spawn(level(), DajiEffectEntity.WIND, pendingPos, 50, getUUID());
    }

    private void castRage(LivingEntity target){
        faceTargetForAttack(target);
        play(ACT_RAGE, 70);
        int stacks = Math.min(DajiConfig.rageMaxStacks, rageStacks()+1);
        entityData.set(RAGE_STACKS, stacks);
        pendingHitTicks = 15;
        pendingHitKind = 4;
        pendingPos = position();
    }

    private void beginCharge(LivingEntity target){
        faceTargetForAttack(target);
        play(ACT_CHARGE_PREP, DajiConfig.chargeWarmupTicks);
        chargePrepTicks = DajiConfig.chargeWarmupTicks;
        chargeMoveTicks = 0;
        chargeHit.clear();

        Vec3 dir = target.position().subtract(position());
        chargeDirection = new Vec3(dir.x, 0.0D, dir.z);
        if(chargeDirection.lengthSqr() < 1.0E-4D){
            chargeDirection = Vec3.directionFromRotation(0.0F, getYRot());
        }else{
            chargeDirection = chargeDirection.normalize();
        }
        DajiEffectEntity.spawn(level(), DajiEffectEntity.SPIRAL, position(),
                DajiConfig.chargeWarmupTicks + 4, getUUID());
    }

    private void tickCharge(){
        if(chargePrepTicks > 0){
            getNavigation().stop();
            setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
            if(--chargePrepTicks <= 0){
                chargeMoveTicks = DajiConfig.chargeMoveTicks;
                play(ACT_CHARGE, chargeMoveTicks);
            }
            return;
        }
        if(chargeMoveTicks <= 0) return;

        getNavigation().stop();
        setDeltaMovement(chargeDirection.x * DajiConfig.chargeSpeed,
                getDeltaMovement().y,
                chargeDirection.z * DajiConfig.chargeSpeed);
        hasImpulse = true;

        AABB hitBox = getBoundingBox().inflate(1.0D);
        for(Player p : level().getEntitiesOfClass(Player.class, hitBox, this::validPlayer)){
            if(!chargeHit.add(p.getUUID())) continue;
            boolean hit = hurtWithoutKnockback(p, damageSources().mobAttack(this), DajiConfig.chargeDamage);
            if(hit){
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                        DajiConfig.chargeSlowTicks, DajiConfig.slowAmplifier, false, true));
            }
        }

        if(--chargeMoveTicks <= 0){
            setDeltaMovement(Vec3.ZERO);
            chargeDirection = Vec3.ZERO;
            chargeHit.clear();
            if(actionTicks <= 0) entityData.set(ACTION, ACT_IDLE);
        }
    }

    private void castSpiritFire(LivingEntity target){
        faceTargetForAttack(target);
        play(ACT_FIRE, 30);
        spiritFireTicks = DajiConfig.spiritFireDuration;
        spiritFireSpawnCd = 0;
    }

    private void tickSpiritFire(){
        if(spiritFireTicks <= 0) return;
        spiritFireTicks--;
        if(spiritFireSpawnCd > 0){
            spiritFireSpawnCd--;
            return;
        }
        spiritFireSpawnCd = Math.max(1, DajiConfig.spiritFireSpawnInterval);
        LivingEntity target = getAttackTargetEntity();
        Vec3 pos = target != null ? target.position() : position();
        DajiEffectEntity.spawn(level(), DajiEffectEntity.FIRE, pos,
                DajiConfig.firePatchLifetimeTicks, getUUID());
    }

    private void tickPendingHit(){
        if(pendingHitTicks <= 0) return;
        if(--pendingHitTicks > 0) return;

        switch(pendingHitKind){
            case 1 -> {
                LivingEntity t = findLiving(pendingTarget, 8.0D);
                if(t != null) hurtWithoutKnockback(t, damageSources().mobAttack(this), DajiConfig.basicDamage);
            }
            case 2 -> foxCleave(findLiving(pendingTarget, 10.0D));
            case 3 -> areaDamage(pendingPos, DajiConfig.windRadius, DajiConfig.windDamage, 0);
            case 4 -> areaDamage(position(), DajiConfig.rageRadius, DajiConfig.rageDamage, 0);
            default -> { }
        }
        pendingHitKind = 0;
        pendingTarget = null;
        pendingPos = null;
    }

    /** 攻略明确狐形平A带顺劈；做成 Boss 周围近距离范围伤害且不产生击退。 */
    private void foxCleave(LivingEntity primary){
        if(primary != null){
            hurtWithoutKnockback(primary, damageSources().mobAttack(this), DajiConfig.basicDamage);
        }
        AABB box = getBoundingBox().inflate(3.0D);
        for(Player p : level().getEntitiesOfClass(Player.class, box,
                p -> validPlayer(p) && p != primary)){
            hurtWithoutKnockback(p, damageSources().mobAttack(this), DajiConfig.basicDamage);
        }
    }

    private void areaDamage(Vec3 center, double radius, float damage, int slowTicks){
        if(center == null) return;
        AABB box = new AABB(center.x-radius, center.y-2, center.z-radius,
                center.x+radius, center.y+3, center.z+radius);
        for(Player p : level().getEntitiesOfClass(Player.class, box, this::validPlayer)){
            if(p.position().distanceToSqr(center) > radius*radius) continue;
            hurtWithoutKnockback(p, damageSources().indirectMagic(this,this), damage);
            if(slowTicks > 0){
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                        slowTicks, DajiConfig.slowAmplifier, false, true));
            }
        }
    }

    private boolean validPlayer(Player p){
        return p.isAlive() && !p.isCreative() && !p.isSpectator();
    }

    private LivingEntity findLiving(UUID id,double radius){
        if(id == null) return null;
        AABB box = getBoundingBox().inflate(radius);
        return level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e.getUUID().equals(id)).stream().findFirst().orElse(null);
    }

    /** 人形“召唤狐群”：数量/配比属于服务端 AI 缺失项，因此由配置控制。白狐单独由狐形技能召唤。 */
    private void spawnHumanFoxWave(){
        if(!(level() instanceof ServerLevel server)) return;
        List<Integer> variants = new ArrayList<>();
        for(int i=0;i<DajiConfig.smallFoxCount;i++) variants.add(DajiFoxMinion.SMALL);
        for(int i=0;i<DajiConfig.blueFoxCount;i++) variants.add(DajiFoxMinion.BLUE);
        spawnFoxVariants(server, variants);
    }

    private void summonWhiteFox(){
        if(!(level() instanceof ServerLevel server) || DajiConfig.whiteFoxCount <= 0) return;
        List<Integer> variants = new ArrayList<>();
        for(int i=0;i<DajiConfig.whiteFoxCount;i++) variants.add(DajiFoxMinion.WHITE);
        spawnFoxVariants(server, variants);
    }

    private void spawnFoxVariants(ServerLevel server, List<Integer> variants){
        int n = variants.size();
        if(n <= 0) return;
        double radius = DajiConfig.summonRadius;
        for(int i=0;i<n;i++){
            double a = Math.PI * 2.0D * i / n;
            DajiFoxMinion fox = DajiContent.FOX.get().create(server);
            if(fox == null) continue;
            fox.setup(variants.get(i), getUUID());
            fox.moveTo(getX()+Math.cos(a)*radius, getY(), getZ()+Math.sin(a)*radius,
                    (float)Math.toDegrees(-a), 0.0F);
            server.addFreshEntity(fox);
        }
    }

    private void enter(State next){
        int oldForm = form();

        state = next;
        stateTicks = 0;
        getNavigation().stop();
        entityData.set(ACTION, ACT_IDLE);
        actionTicks = 0;
        entityData.set(SHIELD, 0.0F);
        setDeltaMovement(Vec3.ZERO);

        switch(next){
            case HUMAN -> {
                entityData.set(FORM, FORM_HUMAN);
                // 从狐形回人形时播放原“解除变身”表现。
                if(oldForm == FORM_FOX){
                    DajiEffectEntity.spawn(level(), DajiEffectEntity.RELEASE, position(), 22, getUUID());
                }
                // 进入人形后让技能按各自 CD 自然轮转；不额外凭空召怪。
            }
            case FIRE -> {
                entityData.set(FORM, FORM_FOX);
                // 三段狐形循环分别使用涂山 / 青丘 / 有苏的原表现资源。
                DajiEffectEntity.spawn(level(), DajiEffectEntity.TRANSFORM_TS, position(), 44, getUUID());
                DajiEffectEntity.spawn(level(), DajiEffectEntity.AURA_TS, position(),
                        Math.max(44, DajiConfig.fireTicks), getUUID());
                spiritFireCd = 0;
                redPowerCd = Math.min(redPowerCd, 20);
                whiteFoxCd = Math.min(whiteFoxCd, 20);
            }
            case SPIRAL -> {
                entityData.set(FORM, FORM_FOX);
                DajiEffectEntity.spawn(level(), DajiEffectEntity.TRANSFORM_QQ, position(), 44, getUUID());
                DajiEffectEntity.spawn(level(), DajiEffectEntity.AURA_QQ, position(),
                        Math.max(44, DajiConfig.spiralTicks), getUUID());
                chargeCd = 0;
                redPowerCd = Math.min(redPowerCd, 20);
                whiteFoxCd = Math.min(whiteFoxCd, 20);
            }
            case SHIELD -> {
                entityData.set(FORM, FORM_FOX);
                entityData.set(SHIELD, DajiConfig.shieldHealth);
                DajiEffectEntity.spawn(level(), DajiEffectEntity.TRANSFORM_YS, position(), 44, getUUID());
                DajiEffectEntity.spawn(level(), DajiEffectEntity.AURA_YS, position(),
                        Math.max(44, DajiConfig.shieldFailsafeTicks), getUUID());
                DajiEffectEntity.spawn(level(), DajiEffectEntity.SHIELD, position(),
                        Math.max(44, DajiConfig.shieldFailsafeTicks), getUUID());
                play(ACT_SHIELD, 44);
            }
        }
    }

    private int duration(State s){
        return switch(s){
            case HUMAN -> DajiConfig.humanTicks;
            case FIRE -> DajiConfig.fireTicks;
            case SPIRAL -> DajiConfig.spiralTicks;
            case SHIELD -> Integer.MAX_VALUE;
        };
    }

    private State next(State s){
        return switch(s){
            case HUMAN -> State.FIRE;
            case FIRE -> State.SPIRAL;
            case SPIRAL -> State.SHIELD;
            case SHIELD -> State.HUMAN;
        };
    }

    private void play(int action,int ticks){
        entityData.set(ACTION, action);
        entityData.set(ACTION_SERIAL, entityData.get(ACTION_SERIAL)+1);
        actionTicks = Math.max(1, ticks);
        notifyAttackAction();
    }

    @Override protected void onNetcraftFightReset(){
        entityData.set(RAGE_STACKS, 0);
        entityData.set(SHIELD, 0.0F);
        redPowerTicks = 0;
        entityData.set(RED_POWER_ACTIVE, false);
        spiritFireTicks = 0;
        rageTimer = DajiConfig.rageInterval;
        basicCd = windCd = summonCd = whiteFoxCd = chargeCd = redPowerCd = spiritFireCd = 0;
        chargePrepTicks = chargeMoveTicks = 0;
        chargeDirection = Vec3.ZERO;
        chargeHit.clear();
        enter(State.HUMAN);
    }

    @Override public void die(net.minecraft.world.damagesource.DamageSource source){
        if(!level().isClientSide && level() instanceof ServerLevel server){
            AABB box = getBoundingBox().inflate(64.0D);
            UUID self = getUUID();
            for(DajiFoxMinion fox : server.getEntitiesOfClass(DajiFoxMinion.class, box,
                    f -> self.equals(f.ownerId()))) fox.discard();
            for(DajiEffectEntity fx : server.getEntitiesOfClass(DajiEffectEntity.class, box,
                    f -> self.equals(f.ownerId()))) fx.discard();
        }
        super.die(source);
    }

    @Override public void addAdditionalSaveData(CompoundTag tag){
        super.addAdditionalSaveData(tag);
        tag.putString("DajiState", state.name());
        tag.putInt("DajiStateTicks", stateTicks);
        tag.putInt("DajiActionTicks", actionTicks);
        tag.putInt("DajiBasicCd", basicCd);
        tag.putInt("DajiWindCd", windCd);
        tag.putInt("DajiSummonCd", summonCd);
        tag.putInt("DajiWhiteFoxCd", whiteFoxCd);
        tag.putInt("DajiChargeCd", chargeCd);
        tag.putInt("DajiRedCd", redPowerCd);
        tag.putInt("DajiFireCd", spiritFireCd);
        tag.putInt("DajiRedTicks", redPowerTicks);
        tag.putBoolean("DajiRedPowerActive", redPowerActive());
        tag.putInt("DajiFireTicks", spiritFireTicks);
        tag.putInt("DajiRageTimer", rageTimer);
        tag.putInt("DajiForm", form());
        tag.putInt("DajiAction", action());
        tag.putFloat("DajiShield", shield());
        tag.putInt("DajiRageStacks", rageStacks());
    }

    @Override public void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);
        try{state = State.valueOf(tag.getString("DajiState"));}catch(Exception e){state = State.HUMAN;}
        stateTicks = tag.getInt("DajiStateTicks");
        actionTicks = tag.getInt("DajiActionTicks");
        basicCd = tag.getInt("DajiBasicCd");
        windCd = tag.getInt("DajiWindCd");
        summonCd = tag.getInt("DajiSummonCd");
        whiteFoxCd = tag.getInt("DajiWhiteFoxCd");
        chargeCd = tag.getInt("DajiChargeCd");
        redPowerCd = tag.getInt("DajiRedCd");
        spiritFireCd = tag.getInt("DajiFireCd");
        redPowerTicks = tag.getInt("DajiRedTicks");
        entityData.set(RED_POWER_ACTIVE, redPowerTicks > 0 || tag.getBoolean("DajiRedPowerActive"));
        spiritFireTicks = tag.getInt("DajiFireTicks");
        rageTimer = tag.contains("DajiRageTimer") ? tag.getInt("DajiRageTimer") : DajiConfig.rageInterval;
        entityData.set(FORM, tag.getInt("DajiForm"));
        entityData.set(ACTION, tag.getInt("DajiAction"));
        entityData.set(SHIELD, tag.getFloat("DajiShield"));
        entityData.set(RAGE_STACKS, tag.getInt("DajiRageStacks"));
        // NBT 已由父类恢复生命值；不要把读档后的 Boss 当成全新生成，否则会被回满血。
        initialized = true;
    }
}
