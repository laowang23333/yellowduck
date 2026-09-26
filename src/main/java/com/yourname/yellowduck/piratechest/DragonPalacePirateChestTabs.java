package com.yourname.yellowduck.piratechest;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.util.*;

/** 按创造栏类别 + 名称/ID 搜索过滤海盗箱内容。 */
public final class DragonPalacePirateChestTabs {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Map<Item, CreativeModeTab> ownerOf = Map.of();

    private final CreativeModeTab tab;
    private final Component name;
    private final ItemStack icon;

    private DragonPalacePirateChestTabs(CreativeModeTab tab, Component name, ItemStack icon) {
        this.tab = tab;
        this.name = name;
        this.icon = icon;
    }

    public Component name() { return name; }
    public ItemStack icon() { return icon; }

    public static List<DragonPalacePirateChestTabs> build(FeatureFlagSet flags, HolderLookup.Provider lookup) {
        List<DragonPalacePirateChestTabs> result = new ArrayList<>();
        result.add(new DragonPalacePirateChestTabs(null, Component.literal("全部"), new ItemStack(Items.CHEST)));
        if (flags == null || lookup == null) return List.copyOf(result);

        try {
            List<CreativeModeTab> netcraft = new ArrayList<>();
            List<CreativeModeTab> modded = new ArrayList<>();
            List<CreativeModeTab> vanilla = new ArrayList<>();

            for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
                if (tab.getType() != CreativeModeTab.Type.CATEGORY) continue;
                ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
                if (id != null && "minecraft".equals(id.getNamespace())) vanilla.add(tab);
                else if (id != null && (id.getNamespace().startsWith("netcraft") || "liuguang".equals(id.getNamespace()))) {
                    netcraft.add(tab);
                } else modded.add(tab);
            }

            List<CreativeModeTab> ordered = new ArrayList<>(netcraft.size() + modded.size() + vanilla.size());
            ordered.addAll(netcraft);
            ordered.addAll(modded);
            ordered.addAll(vanilla);

            CreativeModeTab.ItemDisplayParameters params =
                    new CreativeModeTab.ItemDisplayParameters(flags, true, lookup);
            Map<Item, CreativeModeTab> map = new HashMap<>();

            for (CreativeModeTab tab : ordered) {
                try {
                    tab.buildContents(params);
                } catch (Throwable error) {
                    LOGGER.debug("跳过无法构建的创造栏分类 {}", BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab));
                    continue;
                }
                result.add(new DragonPalacePirateChestTabs(tab, tab.getDisplayName(), tab.getIconItem()));
                for (ItemStack stack : tab.getDisplayItems()) {
                    map.putIfAbsent(stack.getItem(), tab);
                }
            }
            ownerOf = map;
        } catch (Throwable error) {
            LOGGER.warn("构建龙宫海盗箱分类失败，将只保留‘全部’分类。", error);
        }
        return List.copyOf(result);
    }

    public static int[] filter(Container container, DragonPalacePirateChestTabs category, String search) {
        int size = container.getContainerSize();
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        boolean all = category == null || category.tab == null;
        if (all && query.isEmpty()) {
            int[] result = new int[size];
            for (int i = 0; i < size; i++) result[i] = i;
            return result;
        }

        int[] temp = new int[size];
        int count = 0;
        for (int i = 0; i < size; i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            if (category != null && !category.matches(stack)) continue;
            if (!query.isEmpty() && !nameMatches(stack, query)) continue;
            temp[count++] = i;
        }
        return Arrays.copyOf(temp, count);
    }

    private boolean matches(ItemStack stack) {
        return tab == null || ownerOf.get(stack.getItem()) == tab;
    }

    private static boolean nameMatches(ItemStack stack, String query) {
        if (stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) return true;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.toString().toLowerCase(Locale.ROOT).contains(query);
    }
}
