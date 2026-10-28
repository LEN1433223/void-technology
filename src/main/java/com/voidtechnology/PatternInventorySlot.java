package com.voidtechnology;

import appeng.api.inventories.InternalInventory;
import appeng.crafting.pattern.EncodedPatternItem;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 把 AE2 的 {@link InternalInventory}（样板库存）适配成原版 {@link Slot}，
 * 好让 LDLib2 的 {@code ItemSlot} 直接绑定。
 *
 * <p>限制：只接受 AE2 的编码样板 {@link EncodedPatternItem}，且每格 1 个（样板不可堆叠）。</p>
 */
final class PatternInventorySlot extends Slot {

    private final InternalInventory inventory;
    private final int index;

    PatternInventorySlot(InternalInventory inventory, int index) {
        // 坐标无意义：LDLib2 的 ItemSlot 用自身布局决定显示位置
        super(new SimpleContainer(1), 0, 0, 0);
        this.inventory = inventory;
        this.index = index;
    }

    @Override
    public ItemStack getItem() {
        return inventory.getStackInSlot(index);
    }

    @Override
    public void set(ItemStack stack) {
        // 用 insert/extract 而不是 setItemDirect：后者不触发 AE2 的 onChangeInventory，
        // 样板变了逻辑不会重算缓存，GUI 里放样板会"看着放进去但供应器不认"。
        var current = inventory.getStackInSlot(index);
        if (!current.isEmpty()) {
            inventory.extractItem(index, current.getCount(), false);
        }
        if (!stack.isEmpty()) {
            inventory.insertItem(index, stack, false);
        }
    }

    @Override
    public ItemStack remove(int amount) {
        return inventory.extractItem(index, amount, false);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return stack.getItem() instanceof EncodedPatternItem;
    }

    @Override
    public boolean mayPickup(Player player) {
        return true;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public int getContainerSlot() {
        return index;
    }

    @Override
    public boolean hasItem() {
        return !getItem().isEmpty();
    }
}
