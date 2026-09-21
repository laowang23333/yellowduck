package com.yourname.yellowduck.event;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.block.BigChestBlockEntity;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 海盗箱的非玩家破坏保护。 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BigChestProtectionEvents {
    private BigChestProtectionEvents() {
    }

    /**
     * 非空海盗箱绝不能被爆炸炸掉。
     * 空箱仍保持原版爆炸行为；有物品的箱子从本次爆炸的受影响方块列表中移除。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide) {
            return;
        }

        event.getAffectedBlocks().removeIf(pos ->
                event.getLevel().getBlockEntity(pos) instanceof BigChestBlockEntity chest
                        && !chest.isEmpty());
    }
}
