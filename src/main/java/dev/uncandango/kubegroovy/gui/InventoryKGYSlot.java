package dev.uncandango.kubegroovy.gui;

import dev.uncandango.kubegroovy.core.InventoryKGY;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class InventoryKGYSlot extends Slot {
	public final InventoryKGY inventory;
	public final int invIndex;

	public InventoryKGYSlot(InventoryKGY inventory, int invIndex, int xPosition, int yPosition) {
		super(KubeGroovyGUI.EMPTY_CONTAINER, invIndex, xPosition, yPosition);
		this.inventory = inventory;
		this.invIndex = invIndex;
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return inventory.kgy$isItemValid(invIndex, stack);
	}

	@Override
	public ItemStack getItem() {
		return inventory.kgy$getStackInSlot(invIndex);
	}

	@Override
	public void set(ItemStack stack) {
		inventory.kgy$setStackInSlot(invIndex, stack);
		this.setChanged();
	}

	@Override
	public void onQuickCraft(ItemStack oldStackIn, ItemStack newStackIn) {
	}

	@Override
	public void setChanged() {
		this.inventory.kgy$setChanged();
	}

	@Override
	public int getMaxStackSize() {
		return this.inventory.kgy$getSlotLimit(this.invIndex);
	}

	@Override
	public int getMaxStackSize(ItemStack stack) {
		ItemStack maxAdd = stack.copy();
		int maxInput = stack.getMaxStackSize();
		maxAdd.setCount(maxInput);

		ItemStack currentStack = inventory.kgy$getStackInSlot(invIndex);
		if (inventory.kgy$isMutable()) {
			inventory.kgy$setStackInSlot(invIndex, ItemStack.EMPTY);
			ItemStack remainder = inventory.kgy$insertItem(invIndex, maxAdd, true);
			inventory.kgy$setStackInSlot(invIndex, currentStack);
			return maxInput - remainder.getCount();
		} else {
			ItemStack remainder = inventory.kgy$insertItem(invIndex, maxAdd, true);
			int current = currentStack.getCount();
			int added = maxInput - remainder.getCount();
			return current + added;
		}
	}

	@Override
	public boolean mayPickup(Player playerIn) {
		return !this.inventory.kgy$extractItem(invIndex, 1, true).isEmpty();
	}

	@Override
	public ItemStack remove(int amount) {
		return this.inventory.kgy$extractItem(invIndex, amount, false);
	}
}
