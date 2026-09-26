package com.yourname.yellowduck.distillation;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class LargeDistillationPlatformMenu extends AbstractContainerMenu {
    private static final int MACHINE_SLOTS = 22;
    private static final int PLAYER_SLOTS = 36;
    private final LargeDistillationPlatformBlockEntity platform;

    public LargeDistillationPlatformMenu(int id, Inventory inv, LargeDistillationPlatformBlockEntity platform) {
        super(DistillationContent.LARGE_DISTILLATION_PLATFORM_MENU.get(), id);
        this.platform = platform;

        for (int row=0; row<2; row++) for (int col=0; col<3; col++)
            addSlot(new Slot(platform, row*3+col, 9+col*18, 18+row*18));
        for (int i=0; i<4; i++) addSlot(new Slot(platform, 6+i, 9+i*18, 62));
        for (int row=0; row<2; row++) for (int col=0; col<2; col++)
            addSlot(new Slot(platform, 10+row*2+col, 79+col*18, 18+row*18));
        for (int row=0; row<2; row++) for (int col=0; col<4; col++)
            addSlot(new Slot(platform, 14+row*4+col, 119+col*18, 18+row*18));

        for (int row=0; row<3; row++) for (int col=0; col<9; col++)
            addSlot(new Slot(inv, 9+row*9+col, 17+col*18, 88+row*18));
        for (int col=0; col<9; col++) addSlot(new Slot(inv, col, 17+col*18, 162));
        addDataSlots(platform);
    }

    public LargeDistillationPlatformMenu(int id, Inventory inv, FriendlyByteBuf data) {
        this(id, inv, resolve(inv, data.readBlockPos()));
    }

    private static LargeDistillationPlatformBlockEntity resolve(Inventory inv, BlockPos pos) {
        if (inv.player.level().getBlockEntity(pos) instanceof LargeDistillationPlatformBlockEntity be) return be;
        throw new IllegalStateException("大型蒸馏台 BlockEntity 不存在: " + pos);
    }

    @Override public boolean stillValid(Player player) { return platform.stillValid(player); }
    public int getCookTime() { return platform.get(0); }
    public int getCookTimeTotal() { return platform.get(1); }
    public int getBurnTime() { return platform.get(2); }
    public int getBurnTimeTotal() { return platform.get(3); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < MACHINE_SLOTS
                ? moveItemStackTo(stack, MACHINE_SLOTS, MACHINE_SLOTS + PLAYER_SLOTS, true)
                : moveIntoMachine(stack);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }

    private boolean moveIntoMachine(ItemStack stack) {
        if (LargeDistillationPlatformBlockEntity.isIngredient(stack)) return moveItemStackTo(stack,0,6,false);
        if (LargeDistillationPlatformBlockEntity.isBottle(stack)) return moveItemStackTo(stack,10,14,false);
        if (LargeDistillationPlatformBlockEntity.isAlcohol(stack)) return moveItemStackTo(stack,14,22,false);
        if (LargeDistillationPlatformBlockEntity.isFuel(stack)) return moveItemStackTo(stack,6,10,false);
        return false;
    }
}
