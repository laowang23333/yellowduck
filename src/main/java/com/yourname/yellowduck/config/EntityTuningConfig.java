package com.yourname.yellowduck.config;

import com.mojang.logging.LogUtils;
import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.boss.NetcraftBossBase;
import com.yourname.yellowduck.cleopatra.CleopatraConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YellowDuck 所有生物的统一属性/掉落配置。
 *
 * 数值字段统一使用字符串：留空表示沿用源码默认值；填写数字表示覆盖。
 * 掉落格式： [物品ID|最小数量|最大数量|概率]
 * 例如：     [minecraft:diamond|1|10|0.7]
 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class EntityTuningConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern DROP_PATTERN = Pattern.compile("\\[([^\\]]+)]");

    public static final ForgeConfigSpec SPEC;
    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment(
                "YellowDuck 生物属性与掉落配置",
                "所有数值留空字符串 \"\" 时沿用模组源码默认值。",
                "填写数值后，新生成/新加载的实体会应用配置。",
                "items 为空时保留原本掉落；非空时替换成这里写的掉落。",
                "掉落格式：[物品ID|最小数量|最大数量|概率]",
                "示例：[minecraft:diamond|1|10|0.7] 表示70%概率掉落1~10颗钻石。",
                "多个掉落物用英文逗号隔开。"
        );

        add(builder, "two_phase_boss", "BOSS：小黄鸭");
        add(builder, "sakurawitch", "BOSS：魔女小樱（NetCraft风格BOSS）");
        add(builder, "toy_bear", "小樱布偶熊");
        add(builder, "mount", "坐骑：魔化天狗");
        add(builder, "alpaca_mount", "坐骑：羊驼");
        add(builder, "rabbit_mount", "坐骑：玉兔");
        add(builder, "bamboo_horse_mount", "坐骑：竹马");
        add(builder, "silk_boss", "BOSS：疯狂教授斯尔克（T5）（NetCraft T级）");
        add(builder, "silk_shadow_bat", "疯狂教授斯尔克召唤物：暗影蝙蝠");
        add(builder, "silk_meteor", "疯狂教授斯尔克召唤物：追踪陨石");
        add(builder, "silk_plague_bear", "疯狂教授斯尔克召唤物：疫病转移之熊（复用小樱布偶熊模型）");
        add(builder, "silk_summoned_slime", "疯狂教授斯尔克召唤物：不稳定史莱姆（实体ID仍为 minecraft:slime）");

        CleopatraConfig.build(builder);

        SPEC = builder.build();
    }

    private EntityTuningConfig() {}

    private static void add(ForgeConfigSpec.Builder builder, String key, String title) {
        builder.comment("", title).push(key);

        Entry entry = new Entry(
                builder.comment("最大生命值：实体可拥有的最大生命。留空=源码默认值。")
                        .define("max_health", ""),
                builder.comment("攻击力：实体普通攻击/基础攻击使用的攻击伤害。留空=源码默认值。")
                        .define("attack_damage", ""),
                builder.comment("移动速度：地面移动速度。数值越大跑得越快。留空=源码默认值。")
                        .define("movement_speed", ""),
                builder.comment("攻击速度：拥有该属性的实体普通攻击速度。留空=源码默认值。")
                        .define("attack_speed", ""),
                builder.comment("护甲值：原版护甲减伤属性。留空=源码默认值。")
                        .define("armor", ""),
                builder.comment("护甲韧性：降低高额伤害穿透护甲的程度。留空=源码默认值。")
                        .define("armor_toughness", ""),
                builder.comment("击退抗性：0=无抗性，1=完全抗击退。留空=源码默认值。")
                        .define("knockback_resistance", ""),
                builder.comment("跟随范围：AI可持续追踪目标的基础距离。留空=源码默认值。")
                        .define("follow_range", ""),
                builder.comment("攻击击退：实体普通近战命中时附加的击退强度。留空=源码默认值。")
                        .define("attack_knockback", ""),
                builder.comment("飞行速度：仅对拥有飞行速度属性的实体有效。留空=源码默认值。")
                        .define("flying_speed", ""),
                builder.comment("跳跃强度：仅对拥有跳跃强度属性的坐骑/生物有效。留空=源码默认值。")
                        .define("jump_strength", ""),
                builder.comment("NetCraft T级：仅对继承通用 NetcraftBossBase 的BOSS有效，例如斯尔克填5就是T5。")
                        .define("netcraft_tier", ""),
                builder.comment(
                        "掉落物：留空=保留原掉落；非空=完全使用本配置掉落。",
                        "格式：[物品ID|最小数量|最大数量|概率]",
                        "示例：[minecraft:diamond|1|10|0.7]",
                        "多个示例：[minecraft:diamond|1|10|0.7],[minecraft:emerald|2|5|0.25]"
                ).define("items", "")
        );
        ENTRIES.put(key, entry);
        builder.pop();
    }

    @SubscribeEvent
    public static void entityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof LivingEntity entity)) return;
        Entry entry = entryFor(entity);
        if (entry == null) return;
        applyAttributes(entity, entry);
    }

    private static Entry entryFor(LivingEntity entity) {
        if (entity instanceof Slime slime && slime.getPersistentData().getBoolean("SilkProfessorSlime")) {
            return ENTRIES.get("silk_summoned_slime");
        }
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (id == null || !YellowDuckMod.MOD_ID.equals(id.getNamespace())) return null;
        return ENTRIES.get(id.getPath());
    }

    public static void reapply(LivingEntity entity) {
        Entry entry = entryFor(entity);
        if (entry != null) applyAttributes(entity, entry);
    }

    private static void applyAttributes(LivingEntity entity, Entry entry) {
        double oldMax = entity.getMaxHealth();
        float oldHealth = entity.getHealth();
        double healthRatio = oldMax > 0.0D ? oldHealth / oldMax : 1.0D;

        setAttribute(entity, Attributes.MAX_HEALTH, entry.maxHealth());
        setAttribute(entity, Attributes.ATTACK_DAMAGE, entry.attackDamage());
        setAttribute(entity, Attributes.MOVEMENT_SPEED, entry.movementSpeed());
        setAttribute(entity, Attributes.ATTACK_SPEED, entry.attackSpeed());
        setAttribute(entity, Attributes.ARMOR, entry.armor());
        setAttribute(entity, Attributes.ARMOR_TOUGHNESS, entry.armorToughness());
        setAttribute(entity, Attributes.KNOCKBACK_RESISTANCE, entry.knockbackResistance());
        setAttribute(entity, Attributes.FOLLOW_RANGE, entry.followRange());
        setAttribute(entity, Attributes.ATTACK_KNOCKBACK, entry.attackKnockback());
        setAttribute(entity, Attributes.FLYING_SPEED, entry.flyingSpeed());
        setAttribute(entity, Attributes.JUMP_STRENGTH, entry.jumpStrength());

        if (!entry.maxHealth().get().isBlank() && entity.getMaxHealth() > 0.0F) {
            entity.setHealth((float) Math.max(0.1D, Math.min(entity.getMaxHealth(), entity.getMaxHealth() * healthRatio)));
        }

        if (entity instanceof NetcraftBossBase boss) {
            Integer tier = parseInt(entry.netcraftTier().get(), "netcraft_tier");
            if (tier != null && tier > 0) boss.setBaseTier(tier);
            Double attack = parseDouble(entry.attackDamage().get(), "attack_damage");
            if (attack != null) boss.setBaseDamage((int) Math.round(attack));
        }
    }

    private static void setAttribute(LivingEntity entity, Attribute attribute, ForgeConfigSpec.ConfigValue<String> value) {
        String text = value.get();
        if (text == null || text.isBlank()) return;
        Double parsed = parseDouble(text, attribute.getDescriptionId());
        if (parsed == null) return;
        var instance = entity.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(parsed);
    }

    private static Double parseDouble(String text, String field) {
        if (text == null || text.isBlank()) return null;
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException ex) {
            LOGGER.warn("YellowDuck entity config: {} 不是有效数字: {}", field, text);
            return null;
        }
    }

    private static Integer parseInt(String text, String field) {
        Double value = parseDouble(text, field);
        return value == null ? null : (int) Math.round(value);
    }

    @SubscribeEvent
    public static void drops(LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Entry entry = entryFor(event.getEntity());
        if (entry == null) return;
        String config = entry.items().get();
        if (config == null || config.isBlank()) return;

        List<DropRule> rules = parseDrops(config);
        if (rules.isEmpty()) {
            LOGGER.warn("YellowDuck entity config: items 非空但没有任何有效条目，保留原掉落: {}", config);
            return;
        }

        event.getDrops().clear();
        for (DropRule rule : rules) {
            if (event.getEntity().getRandom().nextDouble() > rule.chance()) continue;
            int amount = rule.min() + event.getEntity().getRandom().nextInt(rule.max() - rule.min() + 1);
            while (amount > 0) {
                int count = Math.min(amount, rule.item().getMaxStackSize());
                ItemStack stack = new ItemStack(rule.item(), count);
                ItemEntity drop = new ItemEntity(event.getEntity().level(),
                        event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), stack);
                drop.setDefaultPickUpDelay();
                event.getDrops().add(drop);
                amount -= count;
            }
        }
    }

    private static List<DropRule> parseDrops(String text) {
        List<DropRule> rules = new ArrayList<>();
        Matcher matcher = DROP_PATTERN.matcher(text);
        while (matcher.find()) {
            String body = matcher.group(1).trim();
            String[] parts = body.split("\\|");
            if (parts.length != 4) {
                LOGGER.warn("YellowDuck掉落格式错误，应为 [物品ID|最小|最大|概率]：{}", matcher.group());
                continue;
            }
            try {
                ResourceLocation id = new ResourceLocation(parts[0].trim().toLowerCase(Locale.ROOT));
                Item item = ForgeRegistries.ITEMS.getValue(id);
                if (item == null || item == net.minecraft.world.item.Items.AIR) {
                    LOGGER.warn("YellowDuck掉落物ID不存在：{}", id);
                    continue;
                }
                int min = Math.max(1, Integer.parseInt(parts[1].trim()));
                int max = Math.max(min, Integer.parseInt(parts[2].trim()));
                double chance = Math.max(0.0D, Math.min(1.0D, Double.parseDouble(parts[3].trim())));
                rules.add(new DropRule(item, min, max, chance));
            } catch (Exception ex) {
                LOGGER.warn("YellowDuck掉落配置无法解析：{}", matcher.group());
            }
        }
        return rules;
    }

    private record DropRule(Item item, int min, int max, double chance) {}

    private record Entry(
            ForgeConfigSpec.ConfigValue<String> maxHealth,
            ForgeConfigSpec.ConfigValue<String> attackDamage,
            ForgeConfigSpec.ConfigValue<String> movementSpeed,
            ForgeConfigSpec.ConfigValue<String> attackSpeed,
            ForgeConfigSpec.ConfigValue<String> armor,
            ForgeConfigSpec.ConfigValue<String> armorToughness,
            ForgeConfigSpec.ConfigValue<String> knockbackResistance,
            ForgeConfigSpec.ConfigValue<String> followRange,
            ForgeConfigSpec.ConfigValue<String> attackKnockback,
            ForgeConfigSpec.ConfigValue<String> flyingSpeed,
            ForgeConfigSpec.ConfigValue<String> jumpStrength,
            ForgeConfigSpec.ConfigValue<String> netcraftTier,
            ForgeConfigSpec.ConfigValue<String> items
    ) {}
}
