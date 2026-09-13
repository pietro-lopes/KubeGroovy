package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.level.LevelBlock;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

@RemapPrefixForGY("kgy$")
public interface ContainerKGY extends InventoryKGY {
	default Container kgy$self() {
		return (Container) this;
	}

	@Override
	default boolean kgy$isMutable() {
		return true;
	}

	@Override
	default int kgy$getSlots() {
		return this.kgy$self().getContainerSize();
	}

	@Override
	default ItemStack kgy$getStackInSlot(int slot) {
		return this.kgy$self().getItem(slot);
	}

	@Override
	default ItemStack kgy$insertItem(int slot, ItemStack stack, boolean simulate) {
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}

		var stackInSlot = this.kgy$self().getItem(slot);

		int m;
		if (!stackInSlot.isEmpty()) {
			if (stackInSlot.getCount() >= Math.min(stackInSlot.getMaxStackSize(), kgy$getSlotLimit(slot))) {
				return stack;
			}

			if (!ItemStack.isSameItemSameComponents(stack, stackInSlot)) {
				return stack;
			}

			if (!this.kgy$self().canPlaceItem(slot, stack)) {
				return stack;
			}

			m = Math.min(stack.getMaxStackSize(), kgy$getSlotLimit(slot)) - stackInSlot.getCount();

			if (stack.getCount() <= m) {
				if (!simulate) {
					var copy = stack.copy();
					copy.grow(stackInSlot.getCount());
					this.kgy$self().setItem(slot, copy);
					this.kgy$self().setChanged();
				}

				return ItemStack.EMPTY;
			} else {
				// copy the stack to not modify the original one
				stack = stack.copy();
				if (!simulate) {
					var copy = stack.split(m);
					copy.grow(stackInSlot.getCount());
					this.kgy$self().setItem(slot, copy);
					this.kgy$self().setChanged();
				} else {
					stack.shrink(m);
				}
				return stack;
			}
		} else {
			if (!this.kgy$self().canPlaceItem(slot, stack)) {
				return stack;
			}

			m = Math.min(stack.getMaxStackSize(), kgy$getSlotLimit(slot));
			if (m < stack.getCount()) {
				// copy the stack to not modify the original one
				stack = stack.copy();
				if (!simulate) {
					this.kgy$self().setItem(slot, stack.split(m));
					this.kgy$self().setChanged();
				} else {
					stack.shrink(m);
				}
				return stack;
			} else {
				if (!simulate) {
					this.kgy$self().setItem(slot, stack);
					this.kgy$self().setChanged();
				}
				return ItemStack.EMPTY;
			}
		}

	}

	@Override
	default ItemStack kgy$extractItem(int slot, int amount, boolean simulate) {
		if (amount == 0) {
			return ItemStack.EMPTY;
		}

		var stackInSlot = this.kgy$self().getItem(slot);

		if (stackInSlot.isEmpty()) {
			return ItemStack.EMPTY;
		}

		if (simulate) {
			if (stackInSlot.getCount() < amount) {
				return stackInSlot.copy();
			} else {
				var copy = stackInSlot.copy();
				copy.setCount(amount);
				return copy;
			}
		} else {
			var m = Math.min(stackInSlot.getCount(), amount);

			var decrStackSize = this.kgy$self().removeItem(slot, m);
			this.kgy$self().setChanged();
			return decrStackSize;
		}
	}

	@Override
	default void kgy$setStackInSlot(int slot, ItemStack stack) {
		this.kgy$self().setItem(slot, stack);
	}

	@Override
	default int kgy$getSlotLimit(int slot) {
		return this.kgy$self().getMaxStackSize();
	}

	@Override
	default boolean kgy$isItemValid(int slot, ItemStack stack) {
		return this.kgy$self().canPlaceItem(slot, stack);
	}

	@Override
	default int kgy$getWidth() {
		return kgy$self() instanceof CraftingContainer crafter ? crafter.getWidth() : InventoryKGY.super.kgy$getWidth();
	}

	@Override
	default int kgy$getHeight() {
		return kgy$self() instanceof CraftingContainer crafter ? crafter.getHeight() : InventoryKGY.super.kgy$getHeight();
	}

	@Override
	default void kgy$clear() {
		kgy$self().clearContent();
	}

	@Override
	default void kgy$setChanged() {
		kgy$self().setChanged();

		if (kgy$self() instanceof Inventory inv) {
			inv.player.kgy$sendInventoryUpdate();
		}
	}

	@Override
	default @Nullable LevelBlock kgy$getBlock(Level level) {
		if (kgy$self() instanceof BlockEntity be) {
			return level.kgy$getBlock(be);
		}

		return null;
	}

	@Override
	default Container kgy$asContainer() {
		return kgy$self();
	}
}
