package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.item.ItemPredicate;
import dev.uncandango.kubegroovy.level.LevelBlock;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@RemapPrefixForGY("kgy$")
public interface InventoryKGY {
	default boolean kgy$isMutable() {
		return false;
	}

	default int kgy$getSlots() {
		throw new NoMixinException();
	}

	default ItemStack kgy$getStackInSlot(int slot) {
		throw new NoMixinException();
	}

	default void kgy$setStackInSlot(int slot, ItemStack stack) {
		throw new IllegalStateException("This item handler can't be modified directly! Use insertItem or extractItem instead!");
	}

	default ItemStack kgy$insertItem(int slot, ItemStack stack, boolean simulate) {
		throw new NoMixinException();
	}

	default ItemStack kgy$extractItem(int slot, int amount, boolean simulate) {
		throw new NoMixinException();
	}

	default ItemStack kgy$insertItem(ItemStack stack, boolean simulate) {
		if (stack.isEmpty()) {
			return stack;
		}

		for (int i = 0; i < kgy$getSlots(); i++) {
			stack = kgy$insertItem(i, stack, simulate);
			if (stack.isEmpty()) {
				return ItemStack.EMPTY;
			}
		}

		return stack;
	}

	default int kgy$getSlotLimit(int slot) {
		throw new NoMixinException();
	}

	default boolean kgy$isItemValid(int slot, ItemStack stack) {
		throw new NoMixinException();
	}

	default int kgy$getWidth() {
		return Math.min(kgy$getSlots(), 9);
	}

	default int kgy$getHeight() {
		return (kgy$getSlots() + 8) / 9;
	}

	default void kgy$clear() {
		for (var i = kgy$getSlots() - 1; i >= 0; i--) {
			if (kgy$isMutable()) {
				kgy$setStackInSlot(i, ItemStack.EMPTY);
			} else {
				kgy$extractItem(i, kgy$getStackInSlot(i).getCount(), false);
			}
		}
	}

	default void kgy$clear(ItemPredicate match) {
		if (match.kgy$isWildcard()) {
			kgy$clear();
		}

		for (var i = kgy$getSlots(); i >= 0; i--) {
			if (match.test(kgy$getStackInSlot(i))) {
				if (kgy$isMutable()) {
					kgy$setStackInSlot(i, ItemStack.EMPTY);
				} else {
					kgy$extractItem(i, kgy$getStackInSlot(i).getCount(), false);
				}
			}
		}
	}

	default int kgy$find() {
		for (var i = 0; i < kgy$getSlots(); i++) {
			var stack1 = kgy$getStackInSlot(i);

			if (!stack1.isEmpty()) {
				return i;
			}
		}

		return -1;
	}

	default int kgy$find(ItemPredicate match) {
		if (match.kgy$isWildcard()) {
			return kgy$find();
		}

		for (var i = 0; i < kgy$getSlots(); i++) {
			var stack1 = kgy$getStackInSlot(i);

			if (match.test(stack1)) {
				return i;
			}
		}

		return -1;
	}

	default int kgy$count() {
		var count = 0;

		for (var i = 0; i < kgy$getSlots(); i++) {
			count += kgy$getStackInSlot(i).getCount();
		}

		return count;
	}

	default int kgy$count(ItemPredicate match) {
		if (match.kgy$isWildcard()) {
			return kgy$count();
		}

		var count = 0;

		for (var i = 0; i < kgy$getSlots(); i++) {
			var stack1 = kgy$getStackInSlot(i);

			if (match.test(stack1)) {
				count += stack1.getCount();
			}
		}

		return count;
	}

	default int kgy$countNonEmpty() {
		var count = 0;

		for (var i = 0; i < kgy$getSlots(); i++) {
			if (!kgy$getStackInSlot(i).isEmpty()) {
				count++;
			}
		}

		return count;
	}

	default int kgy$countNonEmpty(ItemPredicate match) {
		if (match.kgy$isWildcard()) {
			return kgy$countNonEmpty();
		}

		var count = 0;

		for (var i = 0; i < kgy$getSlots(); i++) {
			var stack1 = kgy$getStackInSlot(i);

			if (match.test(stack1)) {
				count++;
			}
		}

		return count;
	}

	default boolean kgy$isEmpty() {
		for (var i = 0; i < kgy$getSlots(); i++) {
			if (!kgy$getStackInSlot(i).isEmpty()) {
				return false;
			}
		}

		return true;
	}

	default void kgy$setChanged() {
	}

	@Nullable
	default LevelBlock kgy$getBlock(Level level) {
		return null;
	}

	default List<ItemStack> kgy$getAllItems() {
		var list = new ArrayList<ItemStack>();

		for (var i = 0; i < kgy$getSlots(); i++) {
			ItemStack is = kgy$getStackInSlot(i);

			if (!is.isEmpty()) {
				list.add(is);
			}
		}

		return list;
	}

	@Nullable
	default Container kgy$asContainer() {
		return null;
	}
}
