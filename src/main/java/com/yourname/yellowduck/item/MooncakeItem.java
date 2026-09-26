package com.yourname.yellowduck.item;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.compat.NetcraftFoodBuffBridge;
import com.yourname.yellowduck.registry.ModEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** YellowDuck 五种月饼食物，3D 显示继续使用本 Mod 自己的 Native GLTF 渲染器。 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MooncakeItem extends GltfModelItem {
    public static final int TEN_SECONDS = 20 * 10;
    public static final int TEN_MINUTES = 20 * 60 * 10;
    private static final float DAMAGE_MULTIPLIER = 1.20F;

    public enum Kind {
        SAKURA_DAMAGE,
        SAKURA_REGENERATION,
        ROSE_SPEED,
        RABBIT_HASTE,
        RABBIT_ALL_NETCRAFT_BUFFS
    }

    private final Kind kind;

    public MooncakeItem(Properties properties,
                        Kind kind,
                        ResourceLocation modelLocation,
                        float visualScale) {
        super(properties, modelLocation, visualScale);
        this.kind = kind;
    }

    /**
     * 物品栏/快捷栏使用轻量 2D 图标，避免手机端同时渲染多个高面数 GLB 导致掉帧和触控丢失。
     * 手持、第三人称、地面实体仍由 YellowDuck Native GLTF 渲染。
     */
    public ResourceLocation getGuiIconTexture() {
        String name = switch (kind) {
            case SAKURA_DAMAGE -> "sakura_ice_mooncake_red";
            case SAKURA_REGENERATION -> "sakura_ice_mooncake_yellow";
            case ROSE_SPEED -> "rose_mooncake";
            case RABBIT_HASTE -> "rabbit_cake";
            case RABBIT_ALL_NETCRAFT_BUFFS -> "rabbit_rabbit_cake";
        };
        return new ResourceLocation(YellowDuckMod.MOD_ID, "textures/item/mooncake/" + name + ".png");
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);

        if (!level.isClientSide && entity instanceof Player player) {
            switch (kind) {
                case SAKURA_DAMAGE -> applyNonStackingEffect(
                        player, ModEffects.SAKURA_MOONCAKE_DAMAGE.get(), TEN_SECONDS, 0);
                case SAKURA_REGENERATION -> applyNonStackingEffect(
                        player, ModEffects.SAKURA_MOONCAKE_REGENERATION.get(), TEN_SECONDS, 0);
                case ROSE_SPEED -> applyNonStackingEffect(
                        player, MobEffects.MOVEMENT_SPEED, TEN_SECONDS, 0);
                case RABBIT_HASTE -> applyNonStackingEffect(
                        player, MobEffects.DIG_SPEED, TEN_SECONDS, 0);
                case RABBIT_ALL_NETCRAFT_BUFFS ->
                        NetcraftFoodBuffBridge.applyAll(player, TEN_MINUTES);
            }
        }
        return result;
    }

    /**
     * 不把 I 叠成 II。已有更高等级不覆盖，已有更长持续时间也不缩短；
     * 同等级快结束时最多刷新回本月饼的 10 秒。
     */
    private static void applyNonStackingEffect(Player player,
                                               MobEffect effect,
                                               int duration,
                                               int amplifier) {
        MobEffectInstance old = player.getEffect(effect);
        if (old != null) {
            if (old.getAmplifier() > amplifier) return;
            if (old.getAmplifier() == amplifier && old.getDuration() >= duration) return;
        }
        player.addEffect(new MobEffectInstance(effect, duration, amplifier, false, false, true));
    }

    /**
     * 红色樱花冰皮月饼：让 NetCraft/装备/技能/防御等先完成伤害计算，
     * 在 LivingDamageEvent 的 LOWEST 阶段再乘 1.20，得到“最终实际伤害 +20%”。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getAmount() <= 0.0F) return;

        Player attacker = resolvePlayer(event.getSource().getEntity(), event.getSource().getDirectEntity());
        if (attacker == null || event.getEntity() == attacker) return;
        if (!attacker.hasEffect(ModEffects.SAKURA_MOONCAKE_DAMAGE.get())) return;

        event.setAmount(event.getAmount() * DAMAGE_MULTIPLIER);
    }

    private static Player resolvePlayer(Entity sourceEntity, Entity directEntity) {
        if (sourceEntity instanceof Player player) return player;
        if (directEntity instanceof Projectile projectile && projectile.getOwner() instanceof Player player) {
            return player;
        }
        return null;
    }
}
