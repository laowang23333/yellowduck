package com.yourname.yellowduck.entity;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

    // 右键骑上去
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && !this.isVehicle()) {
            player.startRiding(this);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    // 玩家骑乘时，用 WASD 控制方向
    @Override
    public void travel(Vec3 travelVector) {
        if (this.isVehicle() && this.getControllingPassenger() instanceof Player player) {
            // 跟随玩家视角
            this.setYRot(player.getYRot());
            this.yRotO = this.getYRot();
            this.setXRot(player.getXRot() * 0.5F);
            this.setRot(this.getYRot(), this.getXRot());
            this.yHeadRot = this.yBodyRot = this.getYRot();

            // 读取玩家 WASD 输入
            float strafe = player.xxa * 0.5F;
            float forward = player.zza;
            if (forward <= 0.0F) forward *= 0.25F;

            // 设置移动速度
            this.setSpeed((float) this.getAttributeValue(Attributes.MOVEMENT_SPEED));

            // 应用移动
            super.travel(new Vec3(strafe, travelVector.y, forward));
        } else {
            // 无骑乘者时正常 AI 移动
            super.travel(travelVector);
        }
    }

    // 让骑乘者跟随旋转
    @Override
    public boolean isControlledByLocalInstance() {
        return this.isVehicle() && this.getControllingPassenger() instanceof Player;
    }
}
