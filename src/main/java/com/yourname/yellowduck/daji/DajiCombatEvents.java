package com.yourname.yellowduck.daji;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 白狐之力：SC写明目标承伤提高30%，持续1.5秒。 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class DajiCombatEvents {
    private DajiCombatEvents() {}

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().hasEffect(DajiContent.VULNERABILITY.get())) {
            event.setAmount(event.getAmount() * 1.30F);
        }
    }
}
