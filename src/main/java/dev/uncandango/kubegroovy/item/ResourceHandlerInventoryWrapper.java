package dev.uncandango.kubegroovy.item;

import dev.uncandango.kubegroovy.core.InventoryKGY;
import dev.uncandango.kubegroovy.level.LevelBlock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.StacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

public record ResourceHandlerInventoryWrapper(ResourceHandler<ItemResource> handler) implements InventoryKGY {
	@Override
	public boolean kgy$isMutable() {
		return true;
	}

	@Override
	public int kgy$getSlots() {
		return handler.size();
	}

	@Override
	public ItemStack kgy$getStackInSlot(int slot) {
		var resource = handler.getResource(slot);
		if (resource.isEmpty()) {
			return ItemStack.EMPTY;
		}
		return resource.toStack(handler.getAmountAsInt(slot));
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	@Override
	public void kgy$setStackInSlot(int slot, ItemStack stack) {
		if (handler instanceof StacksResourceHandler stacks) {
			var resource = ItemResource.of(stack);
			stacks.set(slot, resource, stack.getCount());
		} else {
			InventoryKGY.super.kgy$setStackInSlot(slot, stack);
		}
	}

	@Override
	public ItemStack kgy$insertItem(int slot, ItemStack stack, boolean simulate) {
		if (stack.isEmpty()) {
			return stack;
		}
		var resource = ItemResource.of(stack);
		try (var tx = Transaction.openRoot()) {
			int inserted = handler.insert(slot, resource, stack.getCount(), tx);
			if (!simulate) {
				tx.commit();
			}
			int remaining = stack.getCount() - inserted;
			return remaining <= 0 ? ItemStack.EMPTY : stack.copyWithCount(remaining);
		}
	}

	@Override
	public ItemStack kgy$extractItem(int slot, int amount, boolean simulate) {
		if (amount <= 0) {
			return ItemStack.EMPTY;
		}
		var resource = handler.getResource(slot);
		if (resource.isEmpty()) {
			return ItemStack.EMPTY;
		}
		try (var tx = Transaction.openRoot()) {
			int extracted = handler.extract(slot, resource, amount, tx);
			if (!simulate) {
				tx.commit();
			}
			return extracted <= 0 ? ItemStack.EMPTY : resource.toStack(extracted);
		}
	}

	@Override
	public int kgy$getSlotLimit(int slot) {
		return handler.getCapacityAsInt(slot, ItemResource.EMPTY);
	}

	@Override
	public boolean kgy$isItemValid(int slot, ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return handler.isValid(slot, ItemResource.of(stack));
	}

	@Override
	@Nullable
	public LevelBlock kgy$getBlock(Level level) {
		if (handler instanceof BlockEntity entity) {
			return level.kgy$getBlock(entity);
		}
		return null;
	}
}
