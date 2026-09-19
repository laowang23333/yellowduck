package com.yourname.yellowduck.silk;

import com.yourname.yellowduck.boss.NetcraftBossBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** 根据用户提供的斯尔克机制实现。伤害与未注明的冷却见SilkBalance。所有伤害、状态和随机点名均由服务端计算。 */
public class SilkBoss extends NetcraftBossBase {
    public static final EntityDataAccessor<Integer> ANIMATION = SynchedEntityData.defineId(SilkBoss.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> CAST_SERIAL = SynchedEntityData.defineId(SilkBoss.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> MAD = SynchedEntityData.defineId(SilkBoss.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> WALKING = SynchedEntityData.defineId(SilkBoss.class, EntityDataSerializers.BOOLEAN);
    private final Map<UUID, Fighter> fighters = new HashMap<>();
    private final List<Column> columns = new ArrayList<>();
    private final List<Star> stars = new ArrayList<>();
    private final List<Echo> echoes = new ArrayList<>();
    private final Set<UUID> summons = new HashSet<>();
    private Vec3 waterCenter;
    private float flameYaw;
    private int energy, madUntil, combatAge, blackWaterAge;
    private boolean crossed80, crossed70, flamePending, plaguePending, engaged;
    private int cast, castAge, castDuration;
    private UUID castTarget;
    private Vec3 castPoint;
    private int nextBasic, nextBats, nextStar, nextColumn, nextPlague, nextBurst, nextChaser, nextFlame;
    private double previousX, previousZ;
    private static final class Fighter {
        int corruption, plagueDue, rootUntil, virusUntil;
        Vec3 rootPoint;
    }
    private record Column(Vec3 point, int expires) {}
    private record Star(Vec3 point, int due) {}
    private record Echo(UUID target, Vec3 fallback, int due) {}
    public SilkBoss(EntityType<? extends SilkBoss> type, net.minecraft.world.level.Level level) {
        super(type, level);
        setPersistenceRequired();
        setBaseDamage((int) SilkBalance.BASIC_DAMAGE);
        xpReward = 150;
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, SilkBalance.HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.23).add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.ATTACK_DAMAGE, SilkBalance.BASIC_DAMAGE);
    }
    @Override protected void registerGoals() {} // 技能循环自行管理导航，防止原版近战额外造成一次伤害。
    @Override protected void defineSynchedData() {
        super.defineSynchedData(); entityData.define(ANIMATION, 0); entityData.define(CAST_SERIAL, 0);
        entityData.define(MAD, false); entityData.define(WALKING, false);
    }
    @Override public Component getName() { return Component.literal("疯狂教授斯尔克"); }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean isPlayingAttackAnimation() { return cast > 0; }
    public int phase() { return getHealth() <= getMaxHealth() * 0.20F ? 3 : getHealth() <= getMaxHealth() * 0.80F ? 2 : 1; }
    @Override
    public boolean isValidHatredPlayer(net.minecraft.world.entity.player.Player player) {
        if (!super.isValidHatredPlayer(player)) return false;
        Vec3 spawn = getSpawnPosition();
        return spawn == null || player.position().distanceToSqr(spawn)
                <= SilkBalance.ARENA_RADIUS * SilkBalance.ARENA_RADIUS;
    }

    public boolean valid(ServerPlayer player) {
        return isValidHatredPlayer(player);
    }

    public List<ServerPlayer> targets() {
        if (!(level() instanceof ServerLevel sl)) return List.of();
        Vec3 spawn = getSpawnPosition();
        Vec3 center = spawn == null ? position() : spawn;
        return sl.getEntitiesOfClass(
                ServerPlayer.class,
                new AABB(center, center).inflate(SilkBalance.ARENA_RADIUS),
                this::valid
        );
    }

    private ServerPlayer chooseTank(List<ServerPlayer> players) {
        return getAttackTargetEntity() instanceof ServerPlayer player && valid(player) ? player : null;
    }
    private List<ServerPlayer> randomTargets(int count) {
        List<ServerPlayer> list = new ArrayList<>(targets());
        for (int i = list.size() - 1; i > 0; i--) Collections.swap(list, i, random.nextInt(i + 1));
        return list.subList(0, Math.min(count, list.size()));
    }
    @Override public void tick() {
        // super.tick() 会先运行通用 NetCraft 仇恨/脱战/回位逻辑。
        super.tick();
        if (level().isClientSide || !isAlive()) return;

        List<ServerPlayer> players = targets();
        ServerPlayer tank = chooseTank(players);

        // NetCraft 普通仇恨模式：没有有效仇恨目标时 Boss 不会因为“玩家在场”就自动开战。
        if (tank == null || !tank.isAlive()) {
            getNavigation().stop();
            entityData.set(WALKING, false);
            return;
        }

        if (!engaged) {
            engaged = true;
            previousX = getX();
            previousZ = getZ();
            nextBats = tickCount + 120;
            nextStar = tickCount + 180;
            nextColumn = tickCount + 100;
            nextPlague = tickCount + 100;
            nextBurst = tickCount + 180;
            nextChaser = tickCount + 100;
            nextFlame = tickCount + SilkBalance.FLAME_COOLDOWN;
        }

        combatAge++;
        for (ServerPlayer p : players) fighters.computeIfAbsent(p.getUUID(), ignored -> new Fighter());

        if (phase() >= 2 && !crossed80) {
            crossed80 = true;
            flamePending = true;
        }
        if (getHealth() <= getMaxHealth() * 0.70F && !crossed70) {
            crossed70 = true;
            startMadness();
            summonHelpers();
            plaguePending = true;
        }
        if (entityData.get(MAD) && tickCount >= madUntil) {
            entityData.set(MAD, false);
            energy = 0;
        }
        if (!entityData.get(MAD) && energy >= 100) startMadness();

        updateHazards(players);
        updateFighters(players);
        if (!isAlive()) return;

        setTarget(tank);
        getLookControl().setLookAt(tank, 30, 30);

        if (cast > 0) {
            getNavigation().stop();
            tickCast();
        } else {
            if (distanceToSqr(tank) > 64 || !hasLineOfSight(tank)) {
                getNavigation().moveTo(tank, 1.0);
            } else {
                getNavigation().stop();
            }
            schedule(tank);
        }

        double dx = getX() - previousX;
        double dz = getZ() - previousZ;
        entityData.set(WALKING, dx * dx + dz * dz > 1.0E-5);
        previousX = getX();
        previousZ = getZ();
    }
    private void schedule(ServerPlayer tank) {
        if (flamePending) { flamePending = false; begin(7, tank); return; }
        if (plaguePending) { plaguePending = false; begin(4, tank); nextPlague = tickCount + SilkBalance.PLAGUE_COOLDOWN; return; }
        if ((phase() == 3 || (phase() >= 2 && !entityData.get(MAD))) && tickCount >= nextColumn) { createColumns(); nextColumn = tickCount + SilkBalance.COLUMN_COOLDOWN; }
        if (phase() == 3 && tickCount >= nextChaser) { begin(6, tank); nextChaser = tickCount + SilkBalance.CHASER_COOLDOWN; return; }
        if (phase() >= 2 && tickCount >= nextFlame) { begin(7, tank); nextFlame = tickCount + SilkBalance.FLAME_COOLDOWN; return; }
        if (phase() >= 2 && entityData.get(MAD)) {
            if (tickCount >= nextPlague) { begin(4, tank); nextPlague = tickCount + SilkBalance.PLAGUE_COOLDOWN; return; }
            if (tickCount >= nextBurst) { begin(5, tank); nextBurst = tickCount + SilkBalance.BURST_COOLDOWN; return; }
        } else if (phase() >= 2 && tickCount >= nextStar) {
            begin(3, tank); nextStar = tickCount + SilkBalance.METEOR_COOLDOWN; return;
        }
        if (tickCount >= nextBats) { begin(2, tank); nextBats = tickCount + SilkBalance.BAT_COOLDOWN; return; }
        if (tickCount >= nextBasic && distanceToSqr(tank) <= 24 * 24 && hasLineOfSight(tank)) {
            begin(1, tank); nextBasic = tickCount + SilkBalance.BASIC_COOLDOWN;
        }
    }
    private void begin(int animation, ServerPlayer target) {
        faceTargetForAttack(target);
        cast = animation; castAge = 0; castTarget = target.getUUID(); castPoint = target.position();
        castDuration = switch (animation) { case 1 -> 28; case 2 -> 50; case 3 -> 32; case 4 -> 24; case 5 -> 58; case 6 -> 54; default -> 172; };
        entityData.set(ANIMATION, animation); entityData.set(CAST_SERIAL, entityData.get(CAST_SERIAL) + 1);
        if (animation == 7) {
            Vec3 direction = castPoint.subtract(position());
            flameYaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
            setYRot(flameYaw); yBodyRot = flameYaw;
            announce("§6斯尔克正在蓄力扇形火焰，离开正面！");
        }
    }
    private void tickCast() {
        castAge++;
        if (cast == 7) {
            if (castAge < 40 && castAge % 5 == 0) fan(false);
            if (castAge >= 40 && castAge % 20 == 0) fan(true);
        } else if (castAge == 12) {
            switch (cast) {
                case 1 -> basic(); case 2 -> bats(); case 3 -> star(); case 4 -> plague();
                case 5 -> burst(); case 6 -> chaser(); default -> { }
            }
        }
        if (castAge >= castDuration) { cast = 0; entityData.set(ANIMATION, 0); }
    }
    private ServerPlayer player(UUID id) {
        if (id == null || !(level() instanceof ServerLevel sl)) return null;
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(id);
        return p != null && valid(p) ? p : null;
    }
    public boolean hit(ServerPlayer player, float amount, int corruption) {
        if (!isAlive() || !valid(player) || !hasLineOfSight(player)) return false;
        float damage = amount * (phase() == 3 ? SilkBalance.PHASE_THREE_MULTIPLIER : 1);
        boolean hit = hurtWithoutKnockback(player, damageSources().indirectMagic(this, this), damage);
        if (hit) {
            corrupt(player, corruption);
            if (phase() == 3) {
                Fighter f = fighters.computeIfAbsent(player.getUUID(), ignored -> new Fighter());
                f.virusUntil = tickCount + 120;
                player.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 0));
            }
        }
        return hit;
    }
    private void basic() {
        ServerPlayer target = player(castTarget);
        if (target == null || distanceToSqr(target) > 24 * 24) return;
        Vec3 center = target.position(); ring(center, SilkBalance.BASIC_RADIUS, false);
        for (ServerPlayer p : targets()) if (p.position().distanceToSqr(center) <= SilkBalance.BASIC_RADIUS * SilkBalance.BASIC_RADIUS) {
            if (hit(p, SilkBalance.BASIC_DAMAGE, SilkBalance.BASIC_CORRUPTION) && !entityData.get(MAD)) energy = Math.min(100, energy + SilkBalance.ENERGY_PER_HIT);
        }
    }
    private void bats() {
        for (ServerPlayer p : randomTargets(3)) {
            SilkBat bat = SilkContent.BAT.get().create(level());
            if (bat == null) continue;
            Vec3 start = position().add(0, 2, 0);
            bat.moveTo(start.x, start.y, start.z, getYRot(), 0); bat.launch(this, p.getEyePosition());
            level().addFreshEntity(bat); summons.add(bat.getUUID());
            p.displayClientMessage(Component.literal("§5暗影蝙蝠锁定了你的位置，及时躲开！"), true);
        }
    }
    private void star() {
        List<ServerPlayer> list = randomTargets(1); if (list.isEmpty()) return;
        ServerPlayer p = list.get(0); Fighter f = fighters.computeIfAbsent(p.getUUID(), ignored -> new Fighter());
        f.rootPoint = p.position(); f.rootUntil = tickCount + 60;
        stars.add(new Star(p.position(), tickCount + 60));
        p.displayClientMessage(Component.literal("§5黑暗流星：你被禁锢了！队友远离落点！"), false);
    }
    private void plague() {
        List<ServerPlayer> candidates = new ArrayList<>(targets());
        candidates.removeIf(p -> fighters.get(p.getUUID()) != null && fighters.get(p.getUUID()).plagueDue > tickCount);
        if (candidates.isEmpty()) return;
        ServerPlayer p = candidates.get(random.nextInt(candidates.size()));
        fighters.computeIfAbsent(p.getUUID(), ignored -> new Fighter()).plagueDue = tickCount + SilkBalance.PLAGUE_TICKS;
        announce("§4暗黑疫病点名：" + p.getScoreboardName() + "，30秒内靠近召唤的熊！");
    }
    private void burst() {
        announce("§4黑暗能量爆发！3秒后分散，避免二次伤害重叠！");
        for (ServerPlayer p : targets()) {
            hit(p, SilkBalance.BURST_DAMAGE, SilkBalance.SKILL_CORRUPTION);
            echoes.add(new Echo(p.getUUID(), p.position(), tickCount + 60));
        }
        ring(position(), 12, true);
    }
    private void fan(boolean damage) {
        setYRot(flameYaw); yBodyRot = flameYaw;
        Vec3 forward = new Vec3(-Math.sin(Math.toRadians(getYRot())), 0, Math.cos(Math.toRadians(getYRot())));
        ServerLevel sl = (ServerLevel) level();
        for (int i = -9; i <= 9; i++) {
            double a = Math.toRadians(getYRot() + i * 10);
            for (int r = 2; r <= (int) SilkBalance.FLAME_RANGE; r += 2) sl.sendParticles(damage ? ParticleTypes.FLAME : ParticleTypes.SMOKE,
                    getX() - Math.sin(a) * r, getY() + 0.5, getZ() + Math.cos(a) * r, 1, 0.1, 0.15, 0.1, 0);
        }
        if (!damage) return;
        for (ServerPlayer p : targets()) {
            Vec3 delta = p.position().subtract(position());
            if (Math.abs(delta.y) <= 4 && delta.horizontalDistanceSqr() <= SilkBalance.FLAME_RANGE * SilkBalance.FLAME_RANGE
                    && delta.dot(forward) >= 0) hit(p, SilkBalance.FLAME_DAMAGE / 4, 2);
        }
    }
    private void startMadness() {
        energy = 0; entityData.set(MAD, true); madUntil = tickCount + SilkBalance.MADNESS_TICKS;
        nextPlague = tickCount + 40; nextBurst = tickCount + 160;
        announce("§4斯尔克进入疯狂形态！持续120秒！");
        level().playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2, 0.7F);
    }
    private Vec3 homePosition() {
        Vec3 spawn = getSpawnPosition();
        return spawn == null ? position() : spawn;
    }

    private Vec3 floorPoint(double x, double z) {
        Vec3 home = homePosition();
        BlockPos start = BlockPos.containing(x, home.y + 5, z);
        for (int i = 0; i < 12; i++) {
            BlockPos at = start.below(i);
            if (level().getBlockState(at.below()).isSolidRender(level(), at.below())
                    && level().getBlockState(at).getCollisionShape(level(), at).isEmpty()
                    && level().getBlockState(at.above()).getCollisionShape(level(), at.above()).isEmpty()) {
                return new Vec3(x, at.getY(), z);
            }
        }
        return home;
    }

    private Vec3 randomFloor() {
        Vec3 home = homePosition();
        double angle = random.nextDouble() * Math.PI * 2;
        double radius = 5 + random.nextDouble() * 13;
        return floorPoint(home.x + Math.cos(angle) * radius, home.z + Math.sin(angle) * radius);
    }
    private void summonHelpers() {
        PolarBear bear = EntityType.POLAR_BEAR.create(level());
        if (bear != null) {
            Vec3 p = randomFloor(); bear.moveTo(p.x, p.y, p.z, 0, 0); bear.setNoAi(true); bear.setPersistenceRequired();
            bear.setCustomName(Component.literal("疫病转移之熊")); bear.setCustomNameVisible(true);
            bear.getPersistentData().putUUID("SilkOwner", getUUID()); level().addFreshEntity(bear); summons.add(bear.getUUID());
        }
        for (int i = 0; i < 3; i++) {
            Slime slime = EntityType.SLIME.create(level()); if (slime == null) continue;
            Vec3 p = randomFloor(); slime.moveTo(p.x, p.y, p.z, 0, 0); SilkCombatEvents.resizeSlime(slime, 2);
            slime.setPersistenceRequired(); slime.getPersistentData().putUUID("SilkOwner", getUUID());
            level().addFreshEntity(slime); summons.add(slime.getUUID());
        }
        announce("§c70%血量：召唤1只熊和3只史莱姆，强制进入疯狂！");
    }
    private void createColumns() {
        columns.clear();
        for (int i = 0; i < 3; i++) columns.add(new Column(randomFloor(), tickCount + 240));
        announce("§b心智光柱出现，靠近光柱降低心智腐蚀！");
    }
    private void chaser() {
        SilkMeteor meteor = SilkContent.METEOR.get().create(level()); if (meteor == null) return;
        Vec3 p = randomFloor().add(0, 2, 0); meteor.moveTo(p.x, p.y, p.z, 0, 0); meteor.setOwner(this);
        level().addFreshEntity(meteor); summons.add(meteor.getUUID());
        announce("§5追踪陨石出现！集火摧毁，或远离它！");
    }
    private void updateHazards(List<ServerPlayer> players) {
        columns.removeIf(c -> tickCount >= c.expires());
        if (tickCount % 5 == 0) for (Column c : columns) {
            for (int y = 0; y < 5; y++) ((ServerLevel) level()).sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    c.point().x, c.point().y + y * 0.6, c.point().z, 3, 0.3, 0.1, 0.3, 0.01);
        }
        if (tickCount % 20 == 0) for (ServerPlayer p : players) {
            if (columns.stream().anyMatch(c -> p.position().distanceToSqr(c.point()) < 4)) {
                Fighter f = fighters.get(p.getUUID()); if (f != null) f.corruption = Math.max(0, f.corruption - SilkBalance.COLUMN_CLEANSE_PER_SECOND);
            }
        }
        for (Iterator<Star> it = stars.iterator(); it.hasNext();) {
            Star s = it.next();
            if (tickCount >= s.due()) { aoe(s.point(), SilkBalance.STAR_RADIUS, SilkBalance.METEOR_DAMAGE, 10); it.remove(); }
            else if (tickCount % 3 == 0) {
                ring(s.point(), SilkBalance.STAR_RADIUS, false);
                ((ServerLevel) level()).sendParticles(ParticleTypes.DRAGON_BREATH, s.point().x,
                        s.point().y + 1 + (s.due() - tickCount) / 5.0, s.point().z, 18, 0.6, 0.6, 0.6, 0);
            }
        }
        // 同一tick汇总所有二次爆发，避免原版受伤无敌帧吞掉重叠伤害。
        Map<ServerPlayer, Integer> echoHits = new HashMap<>();
        for (Iterator<Echo> it = echoes.iterator(); it.hasNext();) {
            Echo e = it.next(); if (tickCount < e.due()) continue;
            ServerPlayer marked = player(e.target()); Vec3 at = marked == null ? e.fallback() : marked.position();
            ring(at, 3, true);
            for (ServerPlayer p : players) if (p.position().distanceToSqr(at) <= 9) echoHits.merge(p, 1, Integer::sum);
            it.remove();
        }
        echoHits.forEach((p, count) -> hit(p, SilkBalance.BURST_DAMAGE * 0.6F * count, 3 * count));
        if (phase() == 3) {
            if (waterCenter == null) { waterCenter = position(); createColumns(); nextColumn = tickCount + SilkBalance.COLUMN_COOLDOWN; }
            blackWaterAge++;
            double radius = Math.min(SilkBalance.BLACK_WATER_MAX_RADIUS, 2 + blackWaterAge / 100.0);
            if (tickCount % 5 == 0) ring(waterCenter, radius, false);
            if (tickCount % 5 == 0) ((ServerLevel) level()).sendParticles(ParticleTypes.SMOKE,
                    waterCenter.x, waterCenter.y + 0.08, waterCenter.z, 25, radius * 0.4, 0.02, radius * 0.4, 0);
            if (tickCount % 20 == 0) for (ServerPlayer p : players) {
                Vec3 d = p.position().subtract(waterCenter);
                if (Math.abs(d.y) < 2 && d.horizontalDistanceSqr() <= radius * radius) corrupt(p, SilkBalance.BLACK_WATER_PER_SECOND);
            }
        }
    }
    private void updateFighters(List<ServerPlayer> players) {
        for (var entry : new ArrayList<>(fighters.entrySet())) {
            if (!isAlive()) return;
            ServerPlayer p = player(entry.getKey()); Fighter f = entry.getValue();
            if (p == null) { fighters.remove(entry.getKey()); continue; }
            if (f.rootUntil > tickCount && f.rootPoint != null) {
                if (p.isPassenger()) p.stopRiding();
                p.teleportTo(f.rootPoint.x, f.rootPoint.y, f.rootPoint.z); p.setDeltaMovement(Vec3.ZERO);
            }
            if (f.plagueDue > 0 && tickCount >= f.plagueDue) {
                f.plagueDue = 0;
                PolarBear bear = level().getEntitiesOfClass(PolarBear.class, p.getBoundingBox().inflate(5), b ->
                        b.isAlive() && b.getPersistentData().hasUUID("SilkOwner")
                                && b.getPersistentData().getUUID("SilkOwner").equals(getUUID()) && b.distanceToSqr(p) <= 25)
                        .stream().min(Comparator.comparingDouble(b -> b.distanceToSqr(p))).orElse(null);
                if (bear != null) { bear.kill(); announce("§a暗黑疫病转移，熊已牺牲！"); }
                else {
                    SilkCombatEvents.lock(p); aoe(p.position(), SilkBalance.PLAGUE_RADIUS, SilkBalance.PLAGUE_DAMAGE, 15);
                }
            }
            if (tickCount % 20 == 0 && !SilkCombatEvents.locked(p)) {
                String text = "§5心智腐蚀 " + f.corruption + "/100";
                if (f.plagueDue > tickCount) text += " §4疫病 " + ((f.plagueDue - tickCount + 19) / 20) + "秒";
                if (f.virusUntil > tickCount) text += " §2瘟疫病毒";
                p.displayClientMessage(Component.literal(text), true);
            }
        }
    }
    private void corrupt(ServerPlayer p, int amount) {
        Fighter f = fighters.computeIfAbsent(p.getUUID(), ignored -> new Fighter());
        f.corruption = Math.min(100, f.corruption + amount);
        if (f.corruption >= 100 && p.isAlive()) {
            p.displayClientMessage(Component.literal("§4心智腐蚀达到100，你被黑暗吞噬！"), false);
            p.kill(); // 按用户要求直接死亡，不是传送离场，也不经图腾伤害吸收。
        }
    }
    public void aoe(Vec3 point, double radius, float damage, int corruption) {
        ring(point, radius, true);
        for (ServerPlayer p : targets()) if (p.position().distanceToSqr(point) <= radius * radius) hit(p, damage, corruption);
    }
    private void ring(Vec3 at, double radius, boolean burst) {
        ServerLevel sl = (ServerLevel) level(); int count = 36;
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count;
            sl.sendParticles(burst ? ParticleTypes.WITCH : ParticleTypes.SMOKE,
                    at.x + Math.cos(a) * radius, at.y + 0.12, at.z + Math.sin(a) * radius, 1, 0, 0.1, 0, 0);
        }
    }
    private void announce(String message) { for (ServerPlayer p : targets()) p.displayClientMessage(Component.literal(message), false); }
    private void cleanup() {
        if (level() instanceof ServerLevel sl) for (UUID id : summons) {
            Entity e = sl.getEntity(id); if (e != null) e.discard();
        }
        summons.clear(); fighters.clear(); columns.clear(); stars.clear(); echoes.clear();
    }
    private void resetSilkCombatState() {
        cleanup();
        getNavigation().stop();
        setTarget(null);
        cast = energy = combatAge = blackWaterAge = 0;
        waterCenter = null;
        castTarget = null;
        castPoint = null;
        crossed80 = crossed70 = flamePending = plaguePending = engaged = false;
        entityData.set(ANIMATION, 0);
        entityData.set(MAD, false);
        entityData.set(WALKING, false);
    }

    @Override
    protected void onNetcraftDisengageStarted() {
        // NetCraft 清仇恨时立即结束本场技能状态；传送和回血由通用管理器处理。
        resetSilkCombatState();
    }

    @Override
    protected void onNetcraftFightReset() {
        resetSilkCombatState();
        getHatredManager().clearCombatStatistics();
    }

    @Override public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide) {
            cleanup();
            getHatredManager().clearAll();
            entityData.set(ANIMATION, -1);
        }
    }

    @Override protected void tickDeath() {
        deathTime++;
        if (!level().isClientSide && deathTime >= 40) remove(Entity.RemovalReason.KILLED);
    }

    @Override public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide) cleanup();
        super.remove(reason);
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        // 出生点由 NetcraftBossBase 统一保存；技能战斗状态不跨卸载延续。
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        // 兼容旧版斯尔克存档中的 SilkHomeX/Y/Z。
        if (getSpawnPosition() == null && tag.contains("SilkHomeX")) {
            setSpawnPosition(new Vec3(
                    tag.getDouble("SilkHomeX"),
                    tag.getDouble("SilkHomeY"),
                    tag.getDouble("SilkHomeZ")
            ));
        }

        setHealth(getMaxHealth());
        resetSilkCombatState();
    }

    /** NetCraft boss_map_icon.png 右上角小红旗左边的徽章。 */
    @Override public int getIconAtlasU() { return 1152; }
    @Override public int getIconAtlasV() { return 2; }
    @Override public int getIconWidth() { return 106; }
    @Override public int getIconHeight() { return 95; }

}