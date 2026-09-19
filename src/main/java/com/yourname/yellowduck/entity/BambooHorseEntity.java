package com.yourname.yellowduck.entity;

import com.yourname.yellowduck.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 竹马：复用玉兔的双跳起飞、飞行控制和耐力逻辑。 */
public class BambooHorseEntity extends RabbitMountEntity {
    public BambooHorseEntity(EntityType<? extends BambooHorseEntity> type, Level level) { super(type, level); }
    @Override public Component getName() { return Component.literal("竹马"); }
    @Override public ItemStack getPickResult() { return new ItemStack(ModItems.BAMBOO_HORSE_MOUNT.get()); }
    @Override protected double getRiderYOffset() { return 0.62D; }
    @Override protected double getRiderForwardOffset() { return -0.10D; }
}
