package com.yourname.yellowduck.event;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.registry.ModEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * BossIce2 1.1.3 小樱 Debuff 结算：
 * 1) 魔法防御削减：1~10 层对应魔法易伤 2/5/9/14/20/27/35/44/54/65%。
 * 2) 多人一起吃喷火后获得魔法虚弱，玩家自己造成的魔法伤害降低 50%。
 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class SakuraCombatEvents {
    private SakuraCombatEvents() {}

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        if (!isMagicDamage(source)) return;

        // 喷火抱团惩罚：携带魔法虚弱的玩家造成魔法伤害减半。
        Entity attacker = source.getEntity();
        if (attacker instanceof Player player
                && player.hasEffect(ModEffects.SAKURA_MAGIC_WEAKNESS.get())) {
            event.setAmount(event.getAmount() * 0.5F);
        }

        // 小樱普攻叠的“魔法防御削减”作用于玩家受到的所有魔法伤害。
        if (event.getEntity() instanceof Player player) {
            MobEffectInstance effect = player.getEffect(ModEffects.MAGIC_VULNERABILITY.get());
            if (effect != null) {
                int stacks = Math.max(1, Math.min(10, effect.getAmplifier() + 1));
                int percent = magicDefenseReductionPercent(stacks);
                event.setAmount(event.getAmount() * (1.0F + percent / 100.0F));
            }
        }
    }

    private static int magicDefenseReductionPercent(int stacks) {
        int total = 0;
        for (int i = 1; i <= stacks; i++) total += i + 1;
        return total;
    }

    private static boolean isMagicDamage(DamageSource source) {
        if (source == null) return false;
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) return true;
        // 兼容 NetCraft 1.4.x 自己的 magic_attack / magic_projectile 伤害类型，
        // 不在编译期直接依赖 NetCraft 类，避免版本更新导致 YellowDuck 编译失败。
        return source.typeHolder().unwrapKey()
                .map(key -> key.location().getPath().contains("magic"))
                .orElse(false);
    }
}
