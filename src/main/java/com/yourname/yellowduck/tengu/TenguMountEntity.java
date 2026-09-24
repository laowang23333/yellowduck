package com.yourname.yellowduck.tengu;

import com.yourname.yellowduck.entity.MountEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 驯服后进入现有坐骑系统的天狗实体。当前版本不提供驯服入口和坐骑蛋。 */
public final class TenguMountEntity extends MountEntity {
    public TenguMountEntity(EntityType<? extends TenguMountEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public Component getName() {
        return Component.literal("天狗");
    }

    @Override
    protected double getRiderYOffset() {
        return 1.45D;
    }

    @Override
    protected double getRiderForwardOffset() {
        return -0.20D;
    }

    @Override
    public ItemStack getPickResult() {
        // 天狗没有任何坐骑蛋/刷怪蛋入口。
        return ItemStack.EMPTY;
    }
}
