package com.yourname.yellowduck.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class MountEntity extends PathfinderMob {

    public MountEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D)
                .add(Attributes.FOLLOW_RANGE, 35.0D);
    }

    @Override
    public Component getName() {
        return Component.literal("狮子狗");
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public LivingEntity getControllingPassenger() {
        if (this.getFirstPassenger() instanceof Player player) {
            return player;
        }
        return null;
    }

    @Override
    protected float getJumpPower() {
        return 0.6F;
    }

    // 骑乘高度（Y 方向）
    @Override
    public double getPassengersRidingOffset() {
        return super.getPassengersRidingOffset() + 1.2D;
    }

    // 【新增】骑乘水平位置：把玩家往前挪
    // FORWARD_OFFSET 就是往前挪的格数，0.8 大约挪到脖子位置
    // 还想更前 → 加大（1.0、1.2）；想往后 → 减小（0.5、0.3）
    private static final double FORWARD_OFFSET = 0.8D;

    @Override
    public void positionRider(Entity passenger) {
        if (this.hasPassenger(passenger)) {
            double y = this.getY() + this.getPassengersRidingOffset() + passenger.getMyRidingOffset();
            // 根据实体朝向计算前方向量
            double rad = Math.toRadians(this.getYRot());
            double dx = -Math.sin(rad) * FORWARD_OFFSET;
            double dz = Math.cos(rad) * FORWARD_OFFSET;
            passenger.setPos(this.getX() + dx, y, this.getZ() + dz);
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && !this.isVehicle()) {
            player.startRiding(this);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (this.isAlive() && this.getControllingPassenger() instanceof Player player) {
            this.setYRot(player.getYRot());
            this.yRotO = this.getYRot();
            this.setXRot(player.getXRot() * 0.5F);
            this.setRot(this.getYRot(), this.getXRot());
            this.yHeadRot = this.yBodyRot = this.getYRot();

            float strafe = player.xxa * 0.5F;
            float forward = player.zza;
            if (forward <= 0.0F) forward *= 0.25F;

            this.setSpeed((float) this.getAttributeValue(Attributes.MOVEMENT_SPEED));
            super.travel(new Vec3(strafe, 0, forward));
        } else {
            super.travel(travelVector);
        }
    }
}
