package com.yourname.yellowduck.entity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

public final class HateHelper {

    private static final ResourceLocation PROVOKE_ID =
            new ResourceLocation("netcraft", "provocation");
    private static final ResourceLocation STEALTH_ID =
            new ResourceLocation("netcraft", "stealth");

    private HateHelper() {}

    /**
     * 算一个玩家对怪物的仇恨权重。
     * 基础 1.0，挑衅每级 +50%，隐蔽每级 -30%（下限 0.1）。
     */
    public static double getHateWeight(Player player) {
        double weight = 1.0;

        Enchantment provoke = ForgeRegistries.ENCHANTMENTS.getValue(PROVOKE_ID);
        Enchantment stealth = ForgeRegistries.ENCHANTMENTS.getValue(STEALTH_ID);

        int provokeLevel = 0;
        int stealthLevel = 0;

        if (provoke != null) {
            provokeLevel = Math.max(provokeLevel, EnchantmentHelper.getItemEnchantmentLevel(provoke, player.getMainHandItem()));
            provokeLevel = Math.max(provokeLevel, EnchantmentHelper.getItemEnchantmentLevel(provoke, player.getOffhandItem()));
            for (ItemStack s : player.getInventory().armor) {
                provokeLevel = Math.max(provokeLevel, EnchantmentHelper.getItemEnchantmentLevel(provoke, s));
            }
        }
        if (stealth != null) {
            stealthLevel = Math.max(stealthLevel, EnchantmentHelper.getItemEnchantmentLevel(stealth, player.getMainHandItem()));
            stealthLevel = Math.max(stealthLevel, EnchantmentHelper.getItemEnchantmentLevel(stealth, player.getOffhandItem()));
            for (ItemStack s : player.getInventory().armor) {
                stealthLevel = Math.max(stealthLevel, EnchantmentHelper.getItemEnchantmentLevel(stealth, s));
            }
        }

        weight *= (1.0 + 0.5 * provokeLevel);
        weight *= Math.max(0.1, 1.0 - 0.3 * stealthLevel);
        return weight;
    }

    /**
     * 在范围内找仇恨最高的玩家。距离越近，权重越高（最多 ×1.5，最远 ×0.5）。
     */
    public static Player findHighestHateTarget(Mob mob, double range) {
        AABB aabb = mob.getBoundingBox().inflate(range);
        List<Player> players = mob.level().getEntitiesOfClass(Player.class, aabb);

        Player best = null;
        double bestWeight = 0;

        for (Player p : players) {
            if (!p.isAlive()) continue;
            if (p.isCreative() || p.isSpectator()) continue;

            double w = getHateWeight(p);
            double dist = mob.distanceTo(p);
            double distFactor = 1.0 - Math.min(0.5, dist / (range * 2.0));
            w *= distFactor;

            if (w > bestWeight) {
                bestWeight = w;
                best = p;
            }
        }
        return best;
    }
}
