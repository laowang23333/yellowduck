package com.yourname.yellowduck.registry;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.menu.BigChestMenu;
import com.yourname.yellowduck.party.PartyMenu;
import com.yourname.yellowduck.dungeon.RewardMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, YellowDuckMod.MOD_ID);

    public static final RegistryObject<MenuType<BigChestMenu>> BIG_CHEST =
            MENUS.register("big_chest", () -> IForgeMenuType.create(BigChestMenu::new));

    public static final RegistryObject<MenuType<PartyMenu>> PARTY =
            MENUS.register("party", () -> IForgeMenuType.create(PartyMenu::new));

    public static final RegistryObject<MenuType<RewardMenu>> DUNGEON_REWARD =
            MENUS.register("dungeon_reward", () -> IForgeMenuType.create(RewardMenu::new));
}
