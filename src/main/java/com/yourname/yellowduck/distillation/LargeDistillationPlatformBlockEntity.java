package com.yourname.yellowduck.distillation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class LargeDistillationPlatformBlockEntity extends BaseContainerBlockEntity implements ContainerData {
    public static final int INPUT_SLOTS = 6;
    public static final int FUEL_SLOTS = 4;
    public static final int BOTTLE_SLOTS = 4;
    public static final int OUTPUT_SLOTS = 8;
    public static final int SLOT_COUNT = 22;
    public static final int FUEL_START = 6;
    public static final int BOTTLE_START = 10;
    public static final int OUTPUT_START = 14;
    public static final int INGREDIENTS_PER_BATCH = 64;
    public static final int ALCOHOL_PER_BATCH = 8;
    public static final int BOTTLES_PER_BATCH = 8;
    public static final int BATCH_TIME = 1200;
    private static final ResourceLocation ALCOHOL_ID = new ResourceLocation("netcraft_alchemy", "item_alcohol");

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int cookTime;
    private int cookTimeTotal = BATCH_TIME;
    private int burnTime;
    private int burnTimeTotal;
    private UUID owner;
    private String ownerName = "";

    public LargeDistillationPlatformBlockEntity(BlockPos pos, BlockState state) {
        super(DistillationContent.LARGE_DISTILLATION_PLATFORM_BE.get(), pos, state);
    }

    public void setOwner(Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    public boolean isOwner(Player player) { return owner == null || owner.equals(player.getUUID()); }
    public String getOwnerName() { return ownerName; }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LargeDistillationPlatformBlockEntity be) {
        boolean changed = false;
        if (be.burnTime > 0) { be.burnTime--; changed = true; }

        if (be.canProduce()) {
            if (be.burnTime <= 0) {
                int fuel = be.consumeFuel();
                if (fuel > 0) { be.burnTime = fuel; be.burnTimeTotal = fuel; changed = true; }
            }
            if (be.burnTime > 0) {
                be.cookTime++;
                if (be.cookTime >= BATCH_TIME) {
                    be.cookTime = 0;
                    be.consumeIngredients();
                    be.consumeBottles();
                    be.produceAlcohol();
                }
                changed = true;
            } else if (be.cookTime > 0) {
                be.cookTime = 0;
                changed = true;
            }
        } else if (be.cookTime > 0) {
            be.cookTime = 0;
            changed = true;
        }
        if (changed) setChanged(level, pos, state);
    }

    private boolean canProduce() {
        return countIngredients() >= INGREDIENTS_PER_BATCH
                && countBottles() >= BOTTLES_PER_BATCH
                && canStoreAlcohol();
    }

    private int countIngredients() {
        int count = 0;
        for (int i = 0; i < INPUT_SLOTS; i++) if (isIngredient(items.get(i))) count += items.get(i).getCount();
        return count;
    }

    public static boolean isIngredient(ItemStack stack) {
        return stack.is(Items.WHEAT) || stack.is(Items.POTATO);
    }

    private int countBottles() {
        int count = 0;
        for (int i = BOTTLE_START; i < OUTPUT_START; i++) if (isBottle(items.get(i))) count += items.get(i).getCount();
        return count;
    }

    public static boolean isBottle(ItemStack stack) { return stack.is(Items.GLASS_BOTTLE); }
    public static boolean isFuel(ItemStack stack) { return ForgeHooks.getBurnTime(stack, RecipeType.SMELTING) > 0; }

    public static boolean isAlcohol(ItemStack stack) {
        Item alcohol = getAlcoholItem();
        return alcohol != null && !stack.isEmpty() && stack.is(alcohol);
    }

    @Nullable
    private static Item getAlcoholItem() {
        Item item = ForgeRegistries.ITEMS.getValue(ALCOHOL_ID);
        return item == null || item == Items.AIR ? null : item;
    }

    private boolean canStoreAlcohol() {
        Item alcohol = getAlcoholItem();
        if (alcohol == null) return false;
        int space = 0;
        int max = new ItemStack(alcohol).getMaxStackSize();
        for (int i = OUTPUT_START; i < SLOT_COUNT; i++) {
            ItemStack s = items.get(i);
            if (s.isEmpty()) space += max;
            else if (s.is(alcohol)) space += max - s.getCount();
            if (space >= ALCOHOL_PER_BATCH) return true;
        }
        return false;
    }

    private int consumeFuel() {
        for (int i = FUEL_START; i < BOTTLE_START; i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) continue;
            int burn = ForgeHooks.getBurnTime(stack, RecipeType.SMELTING);
            if (burn <= 0) continue;
            ItemStack remaining = stack.getCraftingRemainingItem();
            stack.shrink(1);
            if (stack.isEmpty()) items.set(i, remaining);
            return burn;
        }
        return 0;
    }

    private void consumeIngredients() {
        int remaining = INGREDIENTS_PER_BATCH;
        for (int i = 0; i < INPUT_SLOTS && remaining > 0; i++) {
            ItemStack stack = items.get(i);
            if (!isIngredient(stack)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
    }

    private void consumeBottles() {
        int remaining = BOTTLES_PER_BATCH;
        for (int i = BOTTLE_START; i < OUTPUT_START && remaining > 0; i++) {
            ItemStack stack = items.get(i);
            if (!isBottle(stack)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
    }

    private void produceAlcohol() {
        Item alcohol = getAlcoholItem();
        if (alcohol == null) return;
        int max = new ItemStack(alcohol).getMaxStackSize();
        int remaining = ALCOHOL_PER_BATCH;
        for (int i = OUTPUT_START; i < SLOT_COUNT && remaining > 0; i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty() && stack.is(alcohol)) {
                int add = Math.min(remaining, max - stack.getCount());
                stack.grow(add); remaining -= add;
            }
        }
        for (int i = OUTPUT_START; i < SLOT_COUNT && remaining > 0; i++) {
            if (items.get(i).isEmpty()) {
                int add = Math.min(remaining, max);
                items.set(i, new ItemStack(alcohol, add)); remaining -= add;
            }
        }
    }

    @Override protected Component getDefaultName() {
        return Component.translatable("container.yellowduck.large_distillation_platform");
    }

    @Override protected AbstractContainerMenu createMenu(int id, Inventory inv) {
        return new LargeDistillationPlatformMenu(id, inv, this);
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = ContainerHelper.takeItem(items, slot);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (!stack.isEmpty() && stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override public boolean stillValid(Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) return false;
        return player.distanceToSqr(worldPosition.getX()+0.5D, worldPosition.getY()+0.5D, worldPosition.getZ()+0.5D) <= 64.0D;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot < FUEL_START) return isIngredient(stack);
        if (slot < BOTTLE_START) return isFuel(stack);
        if (slot < OUTPUT_START) return isBottle(stack);
        return isAlcohol(stack);
    }

    @Override public int get(int index) {
        return switch (index) {
            case 0 -> cookTime;
            case 1 -> cookTimeTotal;
            case 2 -> burnTime;
            case 3 -> burnTimeTotal;
            default -> 0;
        };
    }
    @Override public void set(int index, int value) {
        switch (index) {
            case 0 -> cookTime = value;
            case 1 -> cookTimeTotal = value;
            case 2 -> burnTime = value;
            case 3 -> burnTimeTotal = value;
        }
    }
    @Override public int getCount() { return 4; }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        cookTime = tag.getInt("CookTime");
        cookTimeTotal = BATCH_TIME;
        burnTime = tag.getInt("BurnTime");
        burnTimeTotal = tag.getInt("BurnTimeTotal");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putInt("CookTime", cookTime);
        tag.putInt("CookTimeTotal", cookTimeTotal);
        tag.putInt("BurnTime", burnTime);
        tag.putInt("BurnTimeTotal", burnTimeTotal);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
    }
}
