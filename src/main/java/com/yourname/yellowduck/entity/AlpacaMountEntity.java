package com.yourname.yellowduck.entity;

import com.yourname.yellowduck.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 羊驼坐骑：复用通用坐骑的骑乘/移动逻辑，只更换模型、名称和坐骑蛋。 */
public class AlpacaMountEntity extends MountEntity {
    public AlpacaMountEntity(EntityType<? extends AlpacaMountEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public Component getName() {
        return Component.literal("羊驼");
    }

    /**
     * 羊驼模型的背部比基础坐骑默认座位低；这里把玩家放到背部中央。
     */
    @Override
    protected double getRiderYOffset() {
        return 0.92D;
    }

    /** 不再把玩家向羊驼头部前移，座位保持在身体/背部中央。 */
    @Override
    protected double getRiderForwardOffset() {
        return 0.0D;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.ALPACA_MOUNT.get());
    }
}
