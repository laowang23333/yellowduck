package com.yourname.yellowduck.piratechest;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.core.BlockPos;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 非空龙宫海盗箱不会被爆炸摧毁。 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class DragonPalacePirateChestEvents {
    private DragonPalacePirateChestEvents() {}

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) return;
        event.getAffectedBlocks().removeIf((BlockPos pos) ->
                event.getLevel().getBlockEntity(pos) instanceof DragonPalacePirateChestBlockEntity chest
                        && !chest.isEmpty());
    }
}
