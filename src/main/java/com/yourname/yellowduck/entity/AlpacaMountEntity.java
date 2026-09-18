package com.yourname.yellowduck.entity;

import com.yourname.yellowduck.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 羊驼坐骑：复用鬼狼星的骑乘/移动逻辑，只更换模型、名称和坐骑蛋。 */
public class AlpacaMountEntity extends MountEntity {
    public AlpacaMountEntity(EntityType<? extends AlpacaMountEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public Component getName() {
        return Component.literal("羊驼");
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.ALPACA_MOUNT.get());
    }
}
